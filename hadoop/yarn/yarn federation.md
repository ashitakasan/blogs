# Hadoop Trunk

# Yarn Federation 介绍
Apache Hadoop 官方在 hadoop trunk 中加入了 yarn federation，该项目由 Subru Krishnan 主刀，旨在解决巨大规模集群（10k ~ 100k NM）中计算资源管理问题。

__Yarn Federation 目前特点：__

- 将所有节点拆分成多个子集群，使用 Federation 管理这些子集群
- 作业根据提交策略的配置提交到某个子集群，作业的 Container 按照资源拆分规则分发到各个子集群
- 单集群 HA 支持，如果某个子集群故障，则可提交作业、分发资源请求到其他子集群
- FD 接管了 RM 的部分职责，客户端提交作业、查询作业等只需要与 FD 通信，FD 也接管了 RM admin RPC
- FD 的策略配置和作业运行历史保存在 Store 中，trunk 的 store 可能成为单点故障


## Architecture
Yarn Federation 架构如下图（来自官方）：  
![Yarn Federation 架构](https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/images/federation_architecture.png)

其中：

- `Router`: 转发客户端请求到子集群，根据策略和 store 信息选择子集群，Router 可有多个，信息共享在 store 中
- `State Store`: 储存了 Router 策略配置，App 运行的集群等信息
- `RM`: 负责单个子集群，支持 HA，RM 向 Router 汇报集群状况，包括集群当前容量、节点信息等
- `NM`: 运行 Container，单个 NM 只向一个 RM 汇报
- `AMRMProxy`: 向多个子集群 RM 请求资源，根据一定的策略分发资源请求到各个子集群
- `Client`: 客户端只向 Router 发提交 app、查询 app 等请求

下边我们从代码中分析 Federation 各个组件的原理。


## Router
`Router` 作为一个复合服务，主要包含三个主要服务组件：

```java
public class Router extends CompositeService {
	private RouterClientRMService clientRMProxyService;
	private RouterRMAdminService rmAdminProxyService;
	private WebApp webApp;
}
```
`Router` 通过 `clientRMProxyService` 接收 client 的请求，并转发到 RM 上；同时为 admin 请求保留了一个 RPC 服务 `rmAdminProxyService`，保证集群繁忙时 admin 请求也能得到处理，这两个服务的实现类似，这里只分析 `RouterClientRMService`。

### RouterClientRMService
由于所有客户端请求都通过 `Router` 来处理，因此 `Router` 需要对接收到的客户端请求验证后再进行处理，完成验证功能的便是拦截器 `ClientRequestInterceptor`：

```java
public class RouterClientRMService extends AbstractService {
	private Map<String, RequestInterceptorChainWrapper> userPipelineMap;
}
protected void serviceStart() throws Exception {
	this.userPipelineMap = Collections.synchronizedMap(
		new LRUCacheHashMap<String, RequestInterceptorChainWrapper>(maxCacheSize, true));
}
```
对于每个用户，都保存了一个拦截器链，所有用户的拦截器链缓存在一个 LRU 的 map 中；

```java
private void initializePipeline(String user) {
	synchronized (this.userPipelineMap) {
		chainWrapper = new RequestInterceptorChainWrapper();
		this.userPipelineMap.put(user, chainWrapper);
	}
	try{
		ClientRequestInterceptor interceptorChain = this.createRequestInterceptorChain();
		interceptorChain.init(user);
		chainWrapper.init(interceptorChain);
	}
	...
}
```
其中，`this.createRequestInterceptorChain()` 即构造用户对应的拦截器链。
拦截器链对应一系列的逗号分隔的拦截器的实现类，默认的拦截器链只有一个：`DefaultClientRequestInterceptor`；其内部实现为：

```java
public class DefaultClientRequestInterceptor extends AbstractClientRequestInterceptor {
	private ApplicationClientProtocol clientRMProxy
}
```
即默认的拦截器链的实现，不会验证用户请求，直接将用户请求转发到单个 RM 上。  
如果想开启 FD 功能，则需要配置拦截器链为：`FederationClientInterceptor`。  
需要注意的是：如果想配置多个拦截器到拦截器链上，那么需要将默认的拦截器 `DefaultClientRequestInterceptor` 或者 FD 拦截器 `FederationClientInterceptor` 配置在拦截器链的最后一个。

Yarn Federation 的功能都通过 `FederationClientInterceptor` 这个拦截器完成。

### FederationClientInterceptor
`FederationClientInterceptor` 中即包含了上述 FD 架构图中重要的组件：

```java
private Map<SubClusterId, ApplicationClientProtocol> clientRMProxies;
private FederationStateStoreFacade federationFacade;
private RouterPolicyFacade policyFacade;
```

- `ClientRMProxy`: `Router` 作为 RM 的代理，接收客户端请求，每个 RM 都有一个代理
- `FederationStateStoreFacade`: `Router` 中各种配置的储存位置，其中包含了 store 的读写、请求解析
- `RouterPolicyFacade`: `Router` 请求路由转发的策略，客户端提交作业时，用来选择子集群，即 AM 运行的子集群，也叫 `Home Cluster`

FD Interceptor 主要处理以下几种用户请求：

- `getNewApplication`: 向 RM 请求 appid，Router 随机找一个 RM 转发请求
- `submitApplication`: 提交 app，Router 根据提交信息及策略配置选择 RM 转发请求
- `forceKillApplication`: Router 根据 store 中 app 运行记录，查找 RM 转发请求
- `getApplicationReport`: Router 根据 store 中 app 运行记录，查找 RM 转发请求

**此后 `FederationClientInterceptor` 对于每个用户请求，都要从 `StateStore` 获取 subClusterId，然后根据 subClusterId 从 `clientRMProxies` 中获取 RM 对应的 `clientRMProxy`，然后 FD 通过 `clientRMProxy` 与对应的 RM 通信，获取请求响应后返回客户端。**

其中，在 `submitApplication` 详细实现了 failover，以下是几种故障情形下的提交流程：

- 基本流程
	1. client 提交一个 app 到 Router
	2. Router 选择一个 cluster 转发请求
	3. Router 将 appid <-> cluster 映射信息保存到 store 中
	4. store 响应 cluster
	5. Router 提交请求到选择的 cluster
- 如果 store 故障
	1. client 提交一个 app 到 Router
	2. Router 选择一个 cluster 转发请求
	3. Router 将 appid <-> cluster 映射信息保存到 store 中
	4. 由于 store 故障，Router 会重试
	5. Router 响应 client 错误信息
- 提交 app 到 RM 之前 Router 故障：
	1. client 提交一个 app 到 Router
	2. Router 选择一个 cluster 转发请求
	3. Router 将 appid <-> cluster 映射信息保存到 store 中
	4. Router 故障，client 超时并重新提交作业
	5. Router(new) 选择一个 cluster 转发请求
	6. Router 将新的 appid <-> cluster 映射信息保存到 store 中
	7. 由于 store 已经有该 appid 信息，store 返回之前选择的 cluster
	8. Router 提交请求到返回的 cluster 上
- 提交 app 到 RM 之后 Router 故障
	1. client 提交一个 app 到 Router
	2. Router 选择一个 cluster 转发请求
	3. Router 将 appid <-> cluster 映射信息保存到 store 中
	4. Router 提交请求到选择的 cluster 上
	5. Router 故障，client 超时并重新提交作业
	6. Router 选择一个 cluster 转发请求
	7. Router 将新的 appid <-> cluster 映射信息保存到 store 中
	8. 由于 store 已经有该 appid 信息，store 返回之前选择的 cluster
	9. 如果 client 重新提交请求到相同的 RM，RM 不会异常并返回成功消息
- 如果 RM 故障：
	1. client 提交一个 app 到 Router
	2. Router 选择一个 cluster 转发请求（根据策略）
	3. Router 将 appid <-> cluster 映射信息保存到 store 中
	4. Router 提交请求到选择的 cluster 上
	5. cluster RM 故障，并且 RM 的 HA 也故障
	6. Router 超时，Router 选择新的 cluster 转发请求
	7. Router 将新的 appid <-> cluster 映射信息保存到 store 中
	8. store 返回 OK，Router 提交请求到选择的 cluster 中

app 提交成功后，`FederationStateStoreFacade` 负责将 app 的运行信息添加到 store 中。

FD 的其他组件主要用于配置 client 选择提交子集群策略、AM 请求资源策略、FD 信息储存方式等；下面具体分析 FD 内其他组件原理。


## RouterPolicyFacade
对外开放的方法只有一个：`getHomeSubcluster`，即根据 app 提交信息选择子集群。

```java
public SubClusterId getHomeSubcluster(ApplicationSubmissionContext appSubmissionContext,
	List<SubClusterId> blackListSubClusters) throws YarnException {
	String queue = appSubmissionContext.getQueue();
	...
	SubClusterPolicyConfiguration configuration = null;
	try {
		configuration = federationFacade.getPolicyConfiguration(queue);
	} catch (YarnException e) {}
	if (configuration == null) {
		queue = YarnConfiguration.DEFAULT_FEDERATION_POLICY_KEY;
		configuration = federationFacade.getPolicyConfiguration(queue);
	}
	...
	if (!cachedConfs.containsKey(queue) || !cachedConfs.get(queue).equals(configuration)) {
		singlePolicyReinit(policyMap, cachedConfs, queue, configuration);
	}
	FederationRouterPolicy policy = policyMap.get(queue);
	...
	return policy.getHomeSubcluster(appSubmissionContext, blackListSubClusters);
}
```
可以看出，`RouterPolicyFacade` 选择子集群时，首先从 StateStore 中获取配置 `SubClusterPolicyConfiguration`，配置中包含了 policy，然后根据配置再获取队列对应的策略 `FederationRouterPolicy`，最后通过调用 policy 的 `getHomeSubcluster` 获取应该提交 app 的子集群。

`RouterPolicyFacade` 中有两个重要的缓存 Map，其 key 都是队列名：

```java
private Map<String, SubClusterPolicyConfiguration> globalConfMap;
Map<String, FederationRouterPolicy> globalPolicyMap;
```
`globalConfMap` 保存了队列 queue 对应的 `SubClusterPolicyConfiguration`：

```protobuf
message SubClusterPolicyConfigurationProto {
	optional string queue = 1;
	optional string type = 2;
	optional bytes params = 3;
}
```
其中 type 为 `FederationPolicyManager` 的类名，该类提供了 Federation 策略的管理、储存，从中可以获取到 `FederationRouterPolicy`。

可以看出，`RouterPolicyFacade` 对于队列的路由策略使用了两级缓存，每次获取队列配置时，首先从 StateStore 获取配置，如果队列的配置有所更改，则调用 `singlePolicyReinit` 重新初始化该队列的策略配置；然后从 `FederationRouterPolicy` 的缓存 `globalPolicyMap` 中获取当前队列的 `FederationRouterPolicy`，将获取提交子集群的调用交给具体的策略实现类。


## FederationPolicyManager
`FederationPolicyManager` 提供了 Federation 策略的管理、储存；它只是 `FederationAMRMProxyPolicy` 和 `FederationRouterPolicy` 这两个接口的封装，使用不同的 AMRMProxy 策略和 FederationRouter 策略的组合，构成了不同的 FederationPolicy 管理器，每个队列都对应一个 `FederationPolicyManager`：

```
public abstract class AbstractPolicyManager implements FederationPolicyManager {
	private String queue;
	protected Class routerFederationPolicy;
	protected Class amrmProxyFederationPolicy;
}
```
具体的 AMRMProxy 策略和 FederationRouter 策略下边具体分析，值得一提的是，获取 AMRMProxy 策略的方法仅仅只有 `NodeManager` 的 `FederationInterceptor` 调用，这很容易理解，该 `FederationInterceptor` 是用于 AM 向 RM 发起资源请求时使用的，有了它，AM 就可以通过 Router 向多个子集群的 RM 发起资源请求。


## RouterPolicy
`FederationRouterPolicy` 提供了客户端提交作业到哪个子集群的策略，也就是如何选择 AM 运行的子集群。

目前系统中实现了几种子集群选择策略：

- `HashBasedRouterPolicy`: 根据 queue 的 hbase 选择子集群，可确保同一队列的所有作业都在同一子集群
- `LoadBasedRouterPolicy`: 简单的负载均衡路由，转发作业请求到 load 最小的集群上（内存）
- `PriorityRouterPolicy`: 以子集群 weights 作为优先级，选择 weights 最高的子集群
- `RejectRouterPolicy`: 拒绝所有路由请求，主要用于禁止某个队列提交作业
- `UniformRandomRouterPolicy`: 该策略均匀随机的从各个 active 的子集群中选择集群（默认）
- `WeightedRandomRouterPolicy`: 以 weight 为权重在当前活动的子集群中随机选择启动 app

这里主要涉及到综合考虑子集群的 weights 配置和子集群当前 load 的问题。上边讲到 `SubClusterPolicyConfigurationProto` 协议的结构，第三个参数 `params` 便可以用来配置某个队列使用各个子集群的优先级 `weights`，参数解析完成后构造出 `WeightedPolicyInfo` 的实例，并保存在 `AMRMProxyPolicy` 和 `RouterPolicy` 的实现类中。

```protobuf
message SubClusterPolicyConfigurationProto {
	optional string queue = 1;
	optional string type = 2;
	optional bytes params = 3;
}
```

#### WeightedPolicyInfo
解析后的 weights 包含两个主要配置：

```java
private Map<SubClusterIdInfo, Float> routerPolicyWeights = new HashMap<>();
private Map<SubClusterIdInfo, Float> amrmPolicyWeights = new HashMap<>();
private float headroomAlpha;
```
顾名思义，`routerPolicyWeights` 配置了选择提交子集群（即 AM）时的子集群权重，`amrmPolicyWeights` 配置了 AM 进行多集群资源请求时的子集群权重，`headroomAlpha` 用于综合考虑子集群的 weights 配置和子集群当前 load。

以 `WeightedRandomRouterPolicy` 为例，它以各个子集群配置的权重为概率，使用随机算法随机选择一个子集群作为提交子集群。

```java
public SubClusterId getHomeSubcluster(...){
	Map<SubClusterIdInfo, Float> weights = getPolicyInfo().getRouterPolicyWeights();
	ArrayList<Float> weightList = new ArrayList<>();
	ArrayList<SubClusterId> scIdList = new ArrayList<>();
	for (Map.Entry<SubClusterIdInfo, Float> entry : weights.entrySet()) {
		...
		if (entry.getKey() != null && activeSubclusters.containsKey(entry.getKey().toId())) {
			weightList.add(entry.getValue());
			scIdList.add(entry.getKey().toId());
      }
	}
	int pickedIndex = FederationPolicyUtils.getWeightedRandom(weightList);
	return scIdList.get(pickedIndex);
}
```
`WeightedPolicyInfo` 在 `AMRMProxy` 中的使用下边再介绍。


## AMRMProxy
Yarn Federation 的一个主要功能就是，AM 可以向多个子集群发起资源请求，请求方式有很多种。其资源请求流程如下图所示（来自官方）：  
![Yarn Federation AMRMProxy](https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/images/amrmproxy_architecture.png)

AM 的资源请求，经过多个拦截器的验证后，达到 `FederationInterceptor`，在这里，AM 将获取来自 Router 的 `AMRMProxy`，通过这个代理发出的资源请求，将被 Router 根据队列相应的策略配置，分发到各个子集群。

目前 Router 内实现了几种资源请求分发的策略：

- `BroadcastAMRMProxyPolicy`: 将资源请求广播到所有子集群（默认）
- `RejectAMRMProxyPolicy`: 拒绝所有请求，主要用于禁止某个队列提交作业、发起资源请求
- `LocalityMulticastAMRMProxyPolicy`: 优先选择本地化的资源请求策略，本地化即 AM 所在子集群

这里重点分析一下 `LocalityMulticastAMRMProxyPolicy`，它是 AM 向多个子集群发起资源请求策略的主要实现。
其转发请求的基本规则为：

- 主机本地化：如果其他子集群不能解析资源请求，则请求发往 home 集群，即 AM 所在集群
- 机架本地化：资源请求优先发往同一机架上的其他 RM 的 NM 节点
- 任何本地化请求只转发到拥有相应本地化资源的节点子集群的 RM
- 所有没有本地化要求的请求会依据 WeightedPolicy 的配置和动态余量来拆分
	1. headroomAlpha 为 1 表明仅依赖 headroom 拆分请求
	2. headroomAlpha 为 0 表明仅依赖 weights 拆分请求

当 AM 的资源请求发到 Router 上时，`LocalityMulticastAMRMProxyPolicy` 会根据策略配置拆分资源请求，资源拆分结果保存在 `AllocationBookkeeper` 中。首先，它将资源请求按照子集群本地化、机架本地化分类，剔除具有本地化要求的资源请求后，剩下无本地化要求的资源请求，然后查询可以分配资源的子集群，开始拆分。

```java
private void splitIndividualAny(ResourceRequest originalResourceRequest, 
	Set<SubClusterId> targetSubclusters,AllocationBookkeeper allocBookkeeper) 
	throws YarnException {
	long allocationId = originalResourceRequest.getAllocationRequestId();
	int numContainer = originalResourceRequest.getNumContainers();
	...
	List<SubClusterId> targetSCs = new ArrayList<>(targetSubclusters);
	ArrayList<Float> weightsList = new ArrayList<>();
	for (SubClusterId targetId : targetSCs) {
		if (allocationBookkeeper.getSubClustersForId(allocationId) != null) {
			weightsList.add(getLocalityBasedWeighting(allocationId, targetId, allocBookkeeper));
		} else {
			float headroomWeighting = getHeadroomWeighting(targetId, allocationBookkeeper);
			float policyWeighting = getPolicyConfigWeighting(targetId, allocationBookkeeper);
			weightsList.add(hrAlpha * headroomWeighting + (1 - hrAlpha) * policyWeighting);
		}
	}
	ArrayList<Integer> containerNums = computeIntegerAssignment(numContainer, weightsList);
	for (SubClusterId targetId : targetSCs) {
		ResourceRequest out = ResourceRequest.newInstance(...);
		...
	}
}
protected ArrayList<Integer> computeIntegerAssignment(int totalNum,
	ArrayList<Float> weightsList) throws YarnException {
	ArrayList<Integer> ret = new ArrayList<>();
	...
	for (i = 0; i < residue; i++) {
		int index = FederationPolicyUtils.getWeightedRandom(weightsList);
		ret.set(index, ret.get(index) + 1);
	}
	return ret;
}
```
计算资源请求拆分时，会根据各个子集群的 `weights` 和 `headroomAlpha` 配置，计算各个子集群的资源拆分请求权重，其中 `headroomAlpha` 控制了子集群资源余量和策略配置的侧重度，`headroomAlpha` 的值在 `0-1` 之间，值越小越侧重子集群策略配置，越大越侧重子集群资源余量，需要注意 `headroomWeighting` 是各个子集群资源余量的相对值。

最后，返回经过资源拆分后的资源分配列表：`Map<SubClusterId, List<ResourceRequest>>`，交给 AM 去向各个子集群发起资源请求。


## FederationStateStore
`FederationStateStore` 储存了几个集群运行时数据：子集群基本信息、作业运行记录（AM 所有子集群）、队列策略配置信息，这里以 `MemoryFederationStateStore` 为例介绍一下 `FederationStateStore` 的具体实现。

```java
public class MemoryFederationStateStore implements FederationStateStore {
	private Map<SubClusterId, SubClusterInfo> membership;
	private Map<ApplicationId, SubClusterId> applications;
	private Map<String, SubClusterPolicyConfiguration> policies;
	...
}
```
其中，`membership` 通过各个子集群的 RM 的心跳来维持：

```java
// org.apache.hadoop.yarn.server.federation.store.impl.MemoryFederationStateStore.java
public SubClusterRegisterResponse registerSubCluster(SubClusterRegisterRequest request){
	...
	membership.put(subClusterInfo.getSubClusterId(), subClusterInfoToSave);
}

// org.apache.hadoop.yarn.server.resourcemanager.federation.FederationStateStoreService.java
private void registerAndInitializeHeartbeat() {
	SubClusterInfo subClusterInfo = SubClusterInfo.newInstance(subClusterId,
		amRMAddress, clientRMAddress, rmAdminAddress, webAppAddress,
		SubClusterState.SC_NEW, ResourceManager.getClusterTimeStamp(), "");
	registerSubCluster(SubClusterRegisterRequest.newInstance(subClusterInfo));
	...
	stateStoreHeartbeat = new FederationStateStoreHeartbeat(subClusterId,
		stateStoreClient, rmContext.getScheduler());
	scheduledExecutorService = HadoopExecutors.newSingleThreadScheduledExecutor();
	scheduledExecutorService.scheduleWithFixedDelay(stateStoreHeartbeat,
		heartbeatInterval, heartbeatInterval, TimeUnit.SECONDS);
}
```
`applications` 则通过客户端提交作业和作业运行结束来更新和维护，可用于作业信息查询；

`policies` 储存了各个子队列的策略配置，通过手动配置来更新和维护。

目前 `FederationStateStore` 官方的实现有三种：

- `MemoryFederationStateStore`: 配置信息保存在 Router 的内存中
- `SQLFederationStateStore`: 配置信息保存在数据库中（如 MySQL）
- `ZookeeperFederationStateStore`: 配置信息保存在 zk 中

在 `Router` 中，`FederationStateStore` 主要通过 `FederationStateStoreFacade` 来访问。

### FederationStateStoreFacade
`FederationStateStoreFacade` 在 FD 中用于访问 FederationStateStore，主要用来从 stateStore 中获取集群信息、queue 的策略配置等，同时其内部也实现了一个简单的 cache。这个 cache 的实现不太好理解，下边分析一下。

首先分析代码，才能知道 cache 中存的是什么内容。这个 cache 通过 `geronimo` 的 jcache 实现，cache 是常见的 <K, V> 格式，cache 名为 `FederationStateStoreFacade` 类名，默认过期时间 300s。

cache 的默认 K, V 都是 `Object`，但是这段代码可以看出：

```java
public Map<SubClusterId, SubClusterInfo> getSubClusters(
	final boolean filterInactiveSubClusters) throws YarnException {
	try {
		if (isCachingEnabled()) {
			return (Map<SubClusterId, SubClusterInfo>) cache
            .get(buildGetSubClustersCacheRequest(filterInactiveSubClusters));
		}
		...
	} catch (Throwable ex) {
		throw new YarnException(ex);
	}
}
...
private Object buildGetSubClustersCacheRequest(final boolean filterInactiveSubClusters) {
	final String cacheKey = buildCacheKey(getClass().getSimpleName(),
		GET_SUBCLUSTERS_CACHEID, null);
	CacheRequest<String, Map<SubClusterId, SubClusterInfo>> cacheRequest = 
		new CacheRequest<String, Map<SubClusterId, SubClusterInfo>>(cacheKey,
            new Func<String, Map<SubClusterId, SubClusterInfo>>() {...});
	return cacheRequest;
}
```
cache 中的 Value 是一个 Map，包含了 `SubClusterId` 到 `SubClusterInfo` 的映射信息，而 cache 的 Key 是 `CacheRequest`：

```java
private static class CacheRequest<K, V> {
	private K key;
	private Func<K, V> func;

	public CacheRequest(K key, Func<K, V> func) {
		this.key = key;
		this.func = func;
	}
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		return result;
	}
}
protected interface Func<T, TResult> {
	TResult invoke(T input) throws Exception;
}
```
`CacheRequest` 的 `hashCode` 和 `equals` 方法都只计算 key，即认为 key 相同的 CacheRequest 都相等，因此 `buildGetSubClustersCacheRequest` 方法中 build 的 `CacheRequest` 都相同，这个 key 所对应 value 正是 `Func<>` 的 `TResult`：`Map<SubClusterId, SubClusterInfo>`。

```java
protected String buildCacheKey(String typeName, String methodName, String argName) {
	StringBuilder buffer = new StringBuilder();
	buffer.append(typeName).append(".");
	buffer.append(methodName);
	if (argName != null) {
		buffer.append("::");
		buffer.append(argName);
	}
	return buffer.toString();
}
```
从 `buildCacheKey` 方法中可以看出，cache 的 key 的固定格式为：`className.methodName::argName`；而目前系统中只用了两个 key：

```java
private static final String GET_SUBCLUSTERS_CACHEID = "getSubClusters";
private static final String GET_POLICIES_CONFIGURATIONS_CACHEID = "getPoliciesConfigurations";
```

也就是说，cache 中缓存了 `<CacheRequest, Map<SubClusterId, SubClusterInfo>>` 的信息，其中的 key `CacheRequest` 只有两个，`getSubClusters`、`getPoliciesConfigurations`。


## Federation 下作业运行流程
最后，以官方提供的作业从提交到结束的流程图，介绍一下 `Yarn Federation` 下作业运行流程。  
![Federation 下作业运行流程](https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/images/federation_sequence_diagram.png)

1. `Router` 接收到一个 YARN 客户端的作业提交请求
2. `Router` 访问路由表/策略以选择作业的 “主 RM“（策略配置是在 RM 心跳下从 `state-store` 接收的）
3. `Router` 查询 `membership` 以确定 主 RM 的在当前 `Federation` 中
4. `Router` 将应用程序提交请求重定向到 归属 RM（主 RM）
5. `Router` 使用 主子集群标识符 更新应用程序状态
6. 一旦应用程序被提交到归属 RM，就在具有可用资源的第一个 `NodeManager` 上触发 YARN 流程，即将应用程序添加到调度程序队列，并且在主子群集中启动其 AM。
	- 在此过程中，AM 的环境变量被修改，用来指示 `AMRMProxy` 要交流的 YARN RM 的地址；
	- AM 的 token 在启动 AM 时也会被 NM 修改，所以 AM 只能与 `AMRMProxy` 通信。从 AM 到 YARN RM 的任何未来通信都由 `AMRMProxy` 调解
7. AM 将使用 HDFS 公开的本地化信息来请求 `Container`
8. 根据策略，`AMRMProxy` 可以通过提交 Unmanaged AM，以及将 AM 心跳转发到相关子集群来模拟其他子集群的 AM
9. `AMRMProxy` 将使用本地化信息和在 `state-store` 中配置的可插拔策略来决定是否将 AM 接收到的资源请求转发到 主 RM 或一个（或多个）辅助 RM；上图展示了 `AMRMProxy` 决定将请求转发给辅助 RM 的情况
10. 辅助 RM 将为 `AMRMProxy` 提供有效的 `Container` token，以便在其子集群中的某个节点上启动新 `Container`；这种机制可以确保每个子集群都使用自己的 token，避免需要集群共享密钥来创建 token
11. `AMRMProxy` 将分配响应转发回原 AM
12. AM 使用标准 YARN 协议在目标 `NodeManager`（在子集群2上）启动 `Container`

作业运行的主 RM 会保存在 `FederationStateStore` 中；作业运行期间，如果客户端有查询作业信息的需求，则 `Router` 首先从 `FederationStateStore` 中查询出作业运行的主 RM，然后再在这个 RM 上查询作业的详细的运行时信息。
