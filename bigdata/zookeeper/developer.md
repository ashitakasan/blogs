## 开发者指南

### Zookeeper 数据模型
Zookeeper 维护的数据结构类似与 Unix 文件系统，不同的是 Zookeeper 的目录也能存放数据，并且只有绝对路径，没有相对路径，'.' 和 '..' 不能单独用作节点名，部分 unicode 可以用作节点名。

#### ZNode
Zookeeper 中，一个 znode 即一个数据节点，其维护了一个 stat 结构，包括 数据更改的版本，acl 更改，时间戳。版本号会随着数据一起发给客户端，当客户端需要更新数据时，必须提供数据修改的版本。

#### 监视器
znode 可以设置监视器，znode 数据更改时出发监视器并清除监视器，并向客户端发送通知

#### 数据访问
每个 znode 数据的读写都是原子的，读取获取节点所有数据，写入更改所有数据；每个节点都有一个访问控制列表 ACL

#### 临时节点
创建该类型 znode 的会话处于活动状态，znode 就会存在，会话借宿，znode 被删除；临时 znode 不能创建子节点

#### 顺序节点
Zookeeper 支持创建名称单调递增的 znode (<path>%010d)，一个父节点只能创建一个，节点名称的计数器是由父节点维护的，最多支持 MAX_INT 大小

#### Zookeeper 时间
- `zxid` 对 ZooKeeper 状态的每个更改都有事物 id
- `Version numbers` 对节点的每个更改都将导致该节点的某个版本号增加，三个版本号：
	- version  znode 数据更改的版本号
	- cversion znode 子节点更改的版本号
	- aversion znode ACL 更改的版本号
- `Ticks` ZooKeeper 时间颗粒
- `Real time` 仅存在于 znode 创建和修改时保存的时间戳

#### Zookeeper Stat 结构
每个 znode 维护的 stat 结构具有以下字段：
- `czxid` 创建此 znode 的 zxid
- `mzxid` 最后修改此 znode 的 zxid
- `pzxid` 最后修改此 znode 的子节点的 zxid
- `ctime` 创建此 znode 的时间 (ms)
- `mtime` 从上次修改此 znode 到现在的时间间隔 (ms)
- `version` 此 znode 的数据的版本号
- `cversion` 此 znode 子节点更改的版本号
- `aversion` 此 znode 的 ACL 更改的版本号
- `ephemeralOwner` 如果 znode 是临时节点，其保存 znode 所有者的会话 ID，否则其为 0
- `dataLength` 此 znode 的数据字段的长度
- `numChildren` 此 znode 的子节点数  
<br>

### Zookeeper 会话
#### Zookeeper 客户端状态转移
- CONNECTING 	Zookeeper 客户端刚创建时处于此状态
- CONNECTED		客户端连接到服务器
- CLOSED		客户端发生错误，如会话到期、认证失败、应用程序关闭
![状态转换图](https://zookeeper.apache.org/doc/r3.4.9/images/state_dia.jpg)

#### 命令
`zkCli.sh 127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002`
客户端将从服务器列表中任意选择一个连接，如果失败则尝试下一个  
可以在服务器后加根目录名，如 `127.0.0.1:3002/app/a`，该客户端请求的节点路径是基于此路径的相对路径，每个客户端可以有不同的根  

客户端连接到服务器时，会提供会话超时时间，会话超时由服务器管理。当会话到期时（即超时时间内没有收到客户端的心跳包），集群立即删除与该会话有关的所有临时节点，并触发这些节点的监视器。超时的客户端重新与服务器建立连接时，将收到“会话超时”的通知。  
如果客户端在连接过程中，发生网络延迟，则客户端可能重新建立到别的服务器的连接，Zookeeper 内部发生 SessionMovedException。

### Zookeeper 监视器
Zookeeper 客户端的读操作 `getData(), getChildren(), exists()`，可以设置监视器，当监视的节点数据更改或删除时，该客户端将收到通知，监视器有三个特点：
- 一次性：节点数据更改触发监视器后，监视器将被删除
- 发送给客户端：Zookeeper 是严格排序的，在更改成功之前不会触发监视器
- 监视器设置的数据：有两种监视器类型：数据监视器、子监视器，`getData(), exists()` 设置数据监视器，`getChildren()` 设置子监视器。因此，`setData()` 会触发数据监视器，`create()` 会触发新节点的数据监视器以及父节点的子监视器，`delete()` 会触发删除节点的数据监视器和子监视器、父节点的子监视器。

#### 监视器语义
监视器类型及创建方式：
- `Created` exists()
- `Deleted` exists(), getData(), getChildren()
- `Changed` exists(), getData()
- `Child` getChildren()

#### 监视器保证
- Zookeeper 保证事件、监视器、异步恢复都是严格排序的
- 客户端会先收到监视器事件，再看到更改的新数据
- 监视器事件的顺序对应与数据更改的顺序

#### 监视器 注意事项
- 监视器是一次性的，如果想收到未来的通知则必须设置新的监视器
- 不能确保收到节点的所有更新，因为监视器是一次性的并且监视器事件和设置监视器之间存在延迟
- 一个监视器对象仅触发一次
- 从发服务器断开连接，不会接收到任何通知

### Zookeeper 访问控制 ACL
Zookeeper 通过 ACL 控制节点的访问，ACL 类似于 Unix 文件权限，但不是递归的，即节点权限仅与节点自身 ACL 相关，与父节点无关；ACL 通过特定的格式定义节点权限

#### ACL 权限列表
- `CREATE` 可创建子节点
- `READ` 可读取节点数据及列出子节点列表
- `WRITE` 可设置节点数据
- `DELETE` 可删除子节点
- `ADMIN` 可设置 ACL

#### 内建 ACL 格式
- `world` 'anyone'：表示任何用户
- `auth` 没有用户 id，表示任何通过任证的用户
- `digest` 使用 username:password 字符串的 md5 作为 ACL ID
- `ip` 使用 ip 地址作为 ACL ID，格式为 addr/bits，限制客户端 ip 地址的开头

### ZooKeeper 插件式身份验证
服务器在认证客户端时，首先在客户端连接后收集客户端验证信息，然后在节点的 ACL 中查找客户端验证信息。验证插件接口：
```Java
public interface AuthenticationProvider {
	String getScheme();
	KeeperException.Code handleAuthentication(ServerCnxn cnxn, byte authData[]);
	boolean isValid(String id);
	boolean matches(String id, String aclExpr);
	boolean isAuthenticated();
}
```

### 一致性保证
- `顺序一致性` 来自客户端的更新将按照发送的顺序应用
- `原子性` 更新要么成功要么失败
- `单系统映像` 所有服务器的数据视图都相同
- `可靠性` 一旦更新成功，将持续到客户端覆盖更新，不会回滚
- `及时性` 客户端视图保证在一定时间范围内（大约几十秒）是最新的

#### 注意
ZooKeeper 不保证同一时刻不同客户端看到的服务器视图一致，由于网络的延迟  
如果应用程序对同时一致性有要求，则可以在读取数据前调用 sync() 同步服务器数据

### 绑定
ZooKeeper 客户端支持两种绑定：Java 和 C
#### Java
ZooKeeper 的 Java 客户端主要实现在 `org.apache.zookeeper` 和 `org.apache.zookeeper.data` 包中。  
在客户端建立时会创建两个线程，IO 线程和事件线程；所有IO都发生在 IO 线程上，所有事件回调都发生在事件线程上。会话维护，如重新连接到ZooKeeper服务器和维持心跳是在 IO 线程完成，同步方法的响应也在 IO 线程中处理；所有对异步方法和监视事件的响应都在事件线程上进行处理。
- 异步调用和监视器回调将按顺序进行
- 回调不会阻塞 IO 线程或同步调用
- 同步调用可能无法以正确的顺序返回

#### C
ZooKeeper 的 C 客户端包括单线程库和多线程库，多线程库与 Java API 类似，创建 IO 线程和事件线程

### ZooKeeper 操作指南
一些有用的特点和建议，避免陷入陷阱：
- ZooKeeper 客户端从服务器断开连接时，不会收到通知，如果断开连接期间服务器创建并删除节点，则客户端不会收到任何通知
- 必须测试 ZooKeeper 服务器故障；只要大多数服务器处于活动状态，ZooKeeper服务就可以幸存下来
- 客户端使用的 ZooKeeper 服务器列表必须与每个 ZooKeeper 服务器所具有的服务器列表匹配
- ZooKeeper 最具性能关键的部分是事务日志，ZooKeeper 必须在返回响应之前将事务同步到磁盘
- 正确设置 Java 最大堆大小，避免磁盘交换是非常重要的；ZooKeeper 中，一切都是有序的，所以如果有一个请求命中磁盘，则队列中所有其他的请求都会命中磁盘

### 程序结构 demo


