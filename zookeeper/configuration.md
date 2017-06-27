## 配置参数
ZooKeeper 的行为由 ZooKeeper 配置文件管理，这个文件被设计为使得完全相同的文件可以被构成 ZooKeeper 服务器的所有服务器使用，假设磁盘布局是相同的。如果服务器使用不同的配置文件，则必须注意确保所有不同配置文件中的 __服务器列表__ 匹配。

### 最小配置
#### clientPort
监听客户端连接的端口；即客户端尝试连接的端口

#### dataDir
ZooKeeper将存储内存数据库快照的位置，以及默认的数据库更新的事务日志

#### tickTime
单个 tick 的长度，这是 ZooKeeper 使用的基本时间单位，以毫秒为单位

### 高级配置
该部分中的配置设置是可选的；也可以使用 Java 系统属性设置，一般形式为 zookeeper.keyword

#### dataLogDir
事务日志专用目录，拥有专用的日志设备对吞吐量和稳定延迟有很大的影响，建议使用专用日志设备

#### globalOutstandingLimit (Java 属性 zookeeper.globalOutstandingLimit)
如果有多个客户端，则客户端提交请求的速度很容易高于 ZooKeeper 的处理速度。为了防止 ZooKeeper 由于排队的请求过多而耗尽内存，ZooKeeper 将限制客户端提交，保证系统中未完成请求不超过 globalOutstandingLimit，默认为 1000

#### preAllocSize (Java 属性 zookeeper.preAllocSize)
ZooKeeper 在事务日志文件中以 preAllocSize 千字节的块分配空间,默认块大小为 64M

#### snapCount (Java 属性 zookeeper.snapCount)
ZooKeeper 将 snapCount 个事务写入日志文件后，将启动快照并创建新的事务日志文件，默认 snapCount 为100000

#### maxClientCnxns
限制由 IP 地址标识的单个客户端可能对 ZooKeeper 集合的单个成员创建的并发连接数（在套接字级别），为防止 DDOS 攻击，默认为 60

#### clientPortAddress
侦听客户端连接的地址，该属性可选的，默认对于客户端的地址/端口没有限制

#### minSessionTimeout
服务器将允许客户端协商的最小会话超时，默认 2 个 tickTime

#### maxSessionTimeout
服务器将允许客户端协商的最大会话超时，默认 20 个 tickTime

#### fsync.warningthresholdms (Java 属性 zookeeper.fsync.warningthresholdms)
当事务日志 (WAL) 中的fsync所用时间超过此值时，将会向日志中输出一条警告消息，默认为 1000ms

#### autopurge.snapRetainCount
启用后，ZooKeeper 自动清除功能会分别在 dataDir 和 dataLogDir 中保留 autopurge.snapRetainCount 个最新的快照和相应的事务日志，并删除其余的快照。默认值为 3，最小值为 3

#### autopurge.purgeInterval
以小时为单位的时间间隔，必须针对该时间间隔触发清除任务。设置为正整数（1及以上）以启用自动清除，默认值为0

#### syncEnabled (Java 属性 zookeeper.observer.syncEnabled)
观察者现在记录事务和写入快照到磁盘默认像参与者，默认值为 true

### 集群属性

#### electionAlg
选举实施使用，默认为 3：
- 0：原始的基于 UDP 的版本；
- 1：快速领导者选择的非认证的基于 UDP 的版本；
- 2：快速领导者选择的经认证的基于 UDP 的版本;
- 3：快速领导者选择的基于 TCP 的版本

#### initLimit
允许关注者连接并同步到领导者的超时时间，如果 ZooKeeper 管理的数据量很大，则需要增加此值

#### leaderServes (Java 属性 zookeeper.leaderServes)
Leader 是否接受客户端连接。默认值为 yes。  
领导机器协调更新，对于更高的更新吞吐量，领导者可以配置为不接受客户端并专注于协调

#### server.x=[hostname]:nnnnn[:nnnnn]
组成 ZooKeeper 的服务器集合。  
当服务器启动时，它通过在数据目录中查找文件 myid 来确定它是哪个服务器。该文件包含服务器编号，以 ASCII 格式，并且应该与此设置左侧的 server.x 中的 x 匹配。  

由客户端使用的 ZooKeeper 服务器列表必须匹配每个 ZooKeeper 服务器上的服务器列表。  

有两个端口号，第一个 followers 用来连接 leader，第二个用来领导选举；只有在 electionAlg 为 1,2或3（默认值）时，才需要领导选举端口

#### syncLimit
允许 followers 与 ZooKeeper 同步的时间限制，如果 followers 落后于 leader 太远，他们将被丢弃

#### group.x=nnnnn[:nnnnn]
启用分组构造，x 是组标识符，= 符号的数字对应于服务器标识。右侧是以冒号分隔的服务器标识符列表；组必须是不相交的，所有组的并集必须是 ZooKeeper 服务器集合

#### weight.x=nnnnn
与 group.x 一起使用，它在形成仲裁时为服务器分配权重。该值对应于投票时（如领导选举和原子广播协议）服务器的权重，默认为 1

#### cnxTimeout (Java 属性 zookeeper.cnxTimeout)
设置为领导选举通知打开连接的超时值，默认为 5 秒，仅在 electionAlg 为 3 时有效

### 认证和授权选项
允许对服务执行的身份验证/授权进行控制

#### zookeeper.DigestAuthenticationProvider.superDigest (Java 属性 zookeeper.DigestAuthenticationProvider.superDigest)
使 ZooKeeper 集合管理员能够以超级用户身份访问 znode 层次结构，对于被认证为 super 的用户不进行 ACL 检查，默认为禁用

#### isro
测试服务器是否以只读模式运行。如果在只读模式下，服务器将响应 ro，如果不是只读模式，服务器将响应 rw

#### gtmk
将当前跟踪掩码获取为十进制格式的 64 位有符号长整数值

#### stmk
设置当前跟踪掩码。跟踪掩码为 64位，其中每个位启用或禁用服务器上的特定类别的跟踪日志记录，必须将 Log4J 配置为启用 TRACE 级别才能查看跟踪日志记录消息

### 非安全选项

#### forceSync (Java 属性 zookeeper.forceSync)
需要在完成处理更新之前将更新同步到事务日志的介质，如果此选项为 no，ZooKeeper 不需要更新同步到磁盘

#### jute.maxbuffer (Java 属性 jute.maxbuffer)
指定可以存储在 znode 中的数据的最大大小，默认值为 0xfffff，即 1M。如果更改此选项，则必须在所有服务器和客户端上设置该系统属性

#### skipACL (Java 属性 zookeeper.skipACL)
跳过ACL检查，这将提高吞吐量，但是不安全

#### quorumListenOnAllIPs
当设置为true时，ZooKeeper 服务器将侦听来自其所有可用 IP地址上的对等体的连接，而不仅是配置文件的服务器列表中配置的地址，默认为 false

### 使用 Netty 框架通信

ZooKeeper 总是直接使用 jdk NIO，然而在 3.4 和更高版本中，Netty 作为 NIO 的一个选项被支持。通过将环境变量 `zookeeper.serverCnxnFactory` 设置为 `org.apache.zookeeper.server.NettyServerCnxnFactory`，可以使用基于 Netty 的通信来代替 jdk NIO。  
<br>

## ZooKeeper 命令：四字命令
ZooKeeper 响应一组命令，每个命令由四个字母组成。可通过 telnet 或 nc 在客户端端口向 ZooKeeper 发出命令  

- `conf` 打印服务配置的详细信息
- `cons` 列出连接到此服务器的所有客户端的完整连接/会话详细信息。包括有关接收/发送的数据包数，会话ID，操作延迟，上次执行的操作等的信息
- `crst` 重置所有连接的连接/会话统计信息
- `dump` 列出未完成的会话和临时节点。这只适用于 leader
- `envi` 打印有关服务环境的详细信息
- `ruok` 测试服务器是否正在以非错误状态运行；如果服务器正在运行，服务器将以 imok 响应。否则它根本不会响应。imok 响应仅表示服务器进程是活动的并且已绑定到监听端口
- `srst` 重置服务器统计信息
- `srvr` 列出服务器的完整详细信息
- `stat` 列出服务器和连接的客户端的简要详细信息
- `wchs` 列出服务器监视器的简要信息
- `wchc` 按会话列出服务器监视器的详细信息，这将输出与相关联的监视器的会话（连接）列表
- `wchp` 按路径列出服务器监视器的详细信息，这将输出具有关联会话的路径（znode）列表
- `mntr` 输出可用于监控集群运行状况的变量列表

示例：
```Shell
echo stat | nc localhost 2181
```
