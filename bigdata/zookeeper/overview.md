 
## Zookeeper: 分布式应用程序的分布式协调服务

### 设计目标
ZooKeeper 实现了高性能，高可用性，严格的顺序访问  
高性能适用于大型分布式系统，高可用性预防单点故障，严格顺序访问可以实现同步原语  
ZooKeeper 本身由一组服务器构成集群，构成 Master - Slaves 模式
![ZooKeeper](https://zookeeper.apache.org/doc/r3.4.9/images/zkservice.jpg)  
服务器之间数据同步，客户端可以连接到任意一个服务器，以接收数据修改通知

### 数据结构
ZooKeeper 的数据由 znode 维护，znode 组成了 ZooKeeper 的分层命名空间，类似与文件系统树
![ZooKeeper's Hierarchical Namespace](https://zookeeper.apache.org/doc/r3.4.9/images/zknamespace.jpg)  
每个 znode 都可以关联数据，并有数个子节点，znode 维护与其关联的数据更改、ACL更改、时间戳，以允许缓存验证和协调更新，每次 znode 数据更改时，版本号都会增加  

### 监视器
客户端可以在 znode 上设置监视器 watches，znode 数据更改时，会触发监视器，客户端会收到数据更改的数据包

### 保证
ZooKeeper 提供了几点保证：
- 顺序一致性
- 原子性
- 单一系统映像，所有服务器的数据都是相同的
- 可靠性
- 及时性

### 简单 API
- `create`    创建一个 ZooKeeper 节点
- `delete` 	删除一个 znode
- `exists` 	测试节点是否存在
- `get data` 	从节点上读取数据
- `set data` 向节点写入数据
- `get children` 检索节点的子节点的列表
- `sync` 等待要传播的数据

### 实现
- ZooKeeper 多个服务器之间数据可复制，复制的数据包含整个内存数据库，数据跟新会记录到磁盘  
- 客户端的读数据请求由所连接的服务器自己处理，客户端的所有写请求被转发到单个服务，即 leader，leader 接收消息并分发消息到各个从服务器，消息层负责在单点失效时替换领导者  
- ZooKeeper 的消息层的数据更新是原子的，其可以保证副本数据不会有偏差

### 使用
新建配置文件 conf/zoo.cfg，配置选项及解释：
```SHELL
tickTime=2000				# ZooKeeper 使用的基本时间单位(毫秒)，用于心跳
dataDir=/var/lib/zookeeper	# 内存数据库快照的存储位置
clientPort=2181				# 监听客户端连接的端口
initLimit=5					# 集群中 followers 连接到 leader 的超时时间，5个 tickTime
syncLimit=2					# leader 与 followers 之间发送消息请求和响应时间间隔
server.1=zoo1:2888:3888		# server.X=A:B:C 中 X 表示服务器号，A 为服务器 ip地址，
server.2=zoo2:2888:3888		# 	B 为该 server 与集群 leader 通信端口，C 为选举 leader 端口
server.3=zoo3:2888:3888
```
