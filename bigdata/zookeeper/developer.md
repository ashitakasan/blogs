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



### Zookeeper 监视器



### Zookeeper 访问控制 ACL



### ZooKeeper 身份验证



### 一致性保证



### 绑定



### ZooKeeper 操作指南



### 程序结构 demo


