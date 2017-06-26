# Protocol Buffer Basics: Java

`Protocol Buffer` 创建一个实现自动编码和解析 `Protocol Buffer` 数据的类，并使用高效的二进制格式；生成的类为构成 `Protocol Buffer` 的字段提供了 `getter` 和 `setter`，并且以一个单元的形式来处理读取和写入 `Protocol Buffer` 的细节

## 定义协议格式
```protoc
syntax = "proto2";

package tutorial;

option java_package = "com.example.tutorial";
option java_outer_classname = "AddressBookProtos";

message Person {
	required string name = 1;
	required int32 id = 2;
	optional string email = 3;

	enum PhoneType {
		MOBILE = 0;
		HOME = 1;
		WORK = 2;
	}

	message PhoneNumber {
		required string number = 1;
		optional PhoneType type = 2 [default = HOME];
	}

	repeated PhoneNumber phones = 4;
}

message AddressBook {
	repeated Person people = 1;
}
```
- `package` 语言无关的包名
- `java_package` 定义了 java 输出的包名，其比 `package` 优先级高
- `java_outer_classname` 定义了 java 输出的消息类名

消息只是一个包含一组类型字段的聚合，同一消息的字段后边的数字是唯一的，即分配标签

#### 字段修饰符
- `required` 必须提供字段的值，否则消息将被视为“未初始化”
- `optional` 该字段可能设置也可能不设置。如果未设置可选字段值，则使用默认值
- `repeated` 字段可能重复任意次数（包括零），重复值的顺序将保留在协议缓冲区中


## 编译 Protocol Buffer
```protoc
protoc -I=$SRC_DIR --java_out=$DST_DIR $SRC_DIR/addressbook.proto
```


## Protocol Buffer API
每个消息体都生成一个类，每个类都有自己的 `Builder` 类，用于创建该类的实例  
消息和构建器都为消息的每个字段自动生成访问器方法；消息只有 `getter`，而构建器有 `getter` 和 `setter`  
为生成符合语言规范的字段名，应该始终在 `.proto` 文件中为字段名使用小写加下划线


### Builders 和 Messages
`Protocol Buffer` 编译器生成的消息类都是不可变的

### 标准消息方法
- `isInitialized()` 检查是否已设置所有必填字段
- `toString()` 返回消息的可读的表示形式
- `mergeFrom(Message other)` 将其他内容合并到此消息中，覆盖单个标量字段，合并复合字段和连接重复字段
- `clear()` 将所有字段清除为空状态

### 解析和序列化
- `byte[] toByteArray()` 序列化消息并返回一个包含其原始字节的字节数组
- `static Person parseFrom(byte[] data)` 从给定的字节数组解析消息
- `void writeTo(OutputStream output)` 序列化消息并将其写入 `OutputStream`
- `static Person parseFrom(InputStream input)` 从 `InputStream` 读取和解析消息


## 扩展 Protocol Buffer
更新 `Protocol Buffer` 规则：

- 不能更改任何现有字段的标签号
- 不得添加或删除任何 `required` 字段
- 可以删除 `optional` 或 `repeated` 的字段
- 可以添加新的 `optional` 字段或 `repeated` 字段，但必须使用新的标签号
