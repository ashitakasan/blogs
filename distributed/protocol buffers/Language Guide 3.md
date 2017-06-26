# Language Guide (proto3)

## 定义消息类型
```protoc
syntax = "proto3";

message SearchRequest {
	string query = 1;
	int32 page_number = 2;
	int32 result_pre_page = 3;
}
```
`syntax` 字段指定 protocol buffer 语法版本，默认使用 proto2

### 指定字段类型
可以为字段指定标量类型、枚举类型、其他消息类型

### 分配标签
每个消息字段都需要分配唯一的标签，这些标签用于以消息二进制格式标识字段，并且不能更改；使用标签应该注意：

- 可使用标签值范围为：1 ～ 2^29-1，但其中 19000 ～ 19999 为保留标签值，不能使用
- 1～15 的标签值只占用一个字节，16～2047 需要两个字节，频繁使用的字段最好分配 1～15 的标签值以减小消息大小，同时注意为将来可能出现的频繁字段保留一些空间

### 指定字段规则
proto3:

- `singular` : 该字段在消息中最多出现一次
- `repeated` : 字段个数可以为任意个（包括 0），值的重复顺序被记录

proto2:

- `required` : 该字段在消息中必须出现一次
- `optional` : 该字段在消息中可选择的出现
- `repeated` : 该字段可以在消息中重复任意次（包括 0），值的重复顺序被记录，该类型的标量字段应当指定编码规则 packed，即 `repeated int32 samples = 4 [packed=true];`

### 添加消息类型
可以在单个 proto 文件中定义多个消息体

### 添加注释
proto 文件的注释类似于 C/C++ 的注释语法

### 保留字段
如果更新消息定义时，删除了一些字段，那么为了保持消息兼容性，需要在新的消息定义中，指定已经删除的消息字段的字段名或标签值：

```protoc
message Foo {
  reserved 2, 15, 9 to 11;
  reserved "foo", "bar";
}
```
注意：不能在单个 `reserved` 语句中混合声明字段名和标签号。

### proto 文件编译结果
- `C++` : 生成 .h 和 .cc 文件，并为文件中描述的每个消息类型分配一个类
- `Java` : 每个消息类型生成一个包含类的 .java 文件，以及一个用于创建消息实例的 Builder 类
- `Python` : 生成一个模块，其中包含 .proto 中每个消息类型的静态描述符，然后使用元类在运行时创建必要的 Python 数据访问类
- `Go` : 生成一个 .pb.go 文件，其文件中包含每种消息类型的类型


## 标量值类型
基本标量类型有：

- `double`: 双精度浮点
- `float`: 单精度浮点
- `int32`: 使用可变长度编码的 整型
- `int64`: 使用可变长度编码的 长整型
- `uint32`: 使用可变长度编码的 无符号整型
- `uint64`: 使用可变长度编码的 无符号长整型
- `sint32`: 使用可变长度编码的 有符号整型
- `sint64`: 使用可变长度编码的 有符号长整型
- `fixed32`: 固定四个字节的 整型
- `fixed64`: 固定八个字节的 长整型
- `sfixed32`: 固定四个字节的 有符号整型
- `sfixed64`: 固定八个字节的 有符号整型
- `bool`: 布尔类型
- `string`: 字符串必须始终包含 UTF-8 编码或7位 ASCII 文本
- `bytes`: 包含任意任意的字节序列

设置字段值时，会进行类型检查


## 默认值
如果接收的消息中不包含特定的 `singular` 类型字段值，则其被设置为默认值

- `string`: 空字符串
- `bytes`: 空字节
- `bool`: false
- `numeric`: 0
- `enums`: 第一个枚举值
- `message`: 语言相关

`repeated` 字段的默认值为空，通常为语言相关的空列表  
对于标量消息如 `bool`，一旦解析了消息，就不能确定该值是否是默认值

`proto2` 中可以指定字段默认值：`optional int32 result_per_page = 3 [default = 10];`


## 枚举类型
```protoc
message SearchRequest {
	string query = 1;
	int32 page_number = 2;
	int32 result_per_page = 3;
	enum Corpus {
		UNIVERSAL = 0;
		WEB = 1;
		IMAGES = 2;
		LOCAL = 3;
		NEWS = 4;
		PRODUCTS = 5;
		VIDEO = 6;
	}
	Corpus corpus = 4;
}
```
枚举的第一个常量需要赋标签值为 0，既可以作为默认值，又可以与 `proto2` 兼容  
可以为不同的枚举值分配相同的标签值来定义别名，需要设置 `allow_alias = true`

```protoc
enum EnumAllowingAlias {
	option allow_alias = true;
	UNKNOWN = 0;
	STARTED = 1;
	RUNNING = 1;
}
```
可以在消息体内或外定义枚举类型，它们可以在同一个 `proto` 文件中被引用，无法识别的枚举值将在消息被反序列化时保留在消息中


## 其他消息类型
可以使用其他消息类型作为字段类型

```protoc
message SearchResponse {
	repeated Result results = 1;
}

message Result {
	string url = 1;
	string title = 2;
	repeated string snippets = 3;
}
```

### import proto
可以使用 import 导入其他消息的定义

### 使用 proto2 消息类型
可以导入 `proto2` 消息类型并在 `proto3` 消息中使用，反之亦然；`proto2` 枚举不能直接在 `proto3` 语法中使用


## 嵌套类型
可以在消息体中定义和使用嵌套的消息

```protoc
message SearchResponse {
	message Result {
		string url = 1;
		string title = 2;
		repeated string snippets = 3;
	}
	repeated Result results = 1;
}
```
引用内部的消息的方式为：`Parent.Type`，可以嵌套任意深度的消息


## 更新消息类型
消息定义更新的规则：

- 不要更改任何现有字段的数字标签
- 如果添加新字段，则使用“旧”消息格式的代码序列化的任何消息仍然可以通过新生成的代码进行解析
- 在新的消息类型中不再使用的标签号，就可以删除该字段；可能需要重命名该字段，也可以添加前缀 `OBSOLETE_`，或者使该标签 `reserved`
- int32，uint32，int64，uint64 和 bool 都是兼容的，即字段类型可以互换
- sint32 和 sint64 相互兼容，但与其他整数类型不兼容
- 只要字节是有效的 UTF-8，字符串和字节是兼容的
- 如果 `bytes` 包含消息的编码版本，嵌入式消息与 `bytes` 兼容
- fixed32 与 sfixed32 兼容，而 fixed64 与 sfixed64 兼容
- 枚举与整型类型（int32，uint32，int64，uint64）兼容，注意如果值不符合则将被截断

`proto2` Only:
- 添加的任何新字段应该是 `optional` 或 `repeated`
- `optional` 与 `repeated` 兼容，`repeated` 的消息转化为 `optional` 只有最后一个被读取
- 只要默认值不会通过消息发送，更改默认值通常可以；接收端将读取自己协议版本的默认值而忽略发送端的默认值
- 


## 未知字段
未知域是 `protocol buffer` 正确的序列化数据格式，表示解析器无法识别的字段


## Any (proto3)
`Any` 消息类型可以将消息用作嵌入式类型，而不必具有 `.proto` 定义；`Any` 包含任意序列化的消息作为字节，以及一个充当全局唯一标识符并解析为该消息类型的 URL

```protoc
import "google/protobuf/any.proto";

message ErrorStatus {
	string message = 1;
	repeated google.protobuf.Any details = 2;
}
```
给定消息类型的默认类型 URL 是 `type.googleapis.com/packagename.messagename`


## Extensions (proto2)
扩展可让声明消息中的一系列字段号可用于第三方扩展。扩展名是一个字段的占位符，其类型未由原始 `.proto` 文件定义。这允许其他 `.proto` 文件通过使用这些数字标签定义一些或所有字段的类型来添加到消息定义

```protoc
message Foo {
	// ...
	extensions 100 to 199;
}
```
现在可以在自己的 `.proto` 文件中为 `Foo` 添加新的字段

```protoc
extend Foo {
	optional int32 bar = 126;
}
```
访问应用程序代码中的扩展字段的方式与访问常规字段略有不同：

```cpp
Foo foo;
foo.SetExtension(bar, 15);
```
扩展名可以是任何字段类型，包括消息类型，但不能是 `oneofs` 或 `map`

### 嵌套扩展
```protoc
message Baz {
	extend Foo {
		optional int32 bar = 126;
	}
	...
}
```
```cpp
Foo foo;
foo.SetExtension(Baz::bar, 15);
```
一个常见的模式是在扩展的字段类型的范围内定义扩展名

```protoc
message Baz {
	extend Foo {
		optional Baz foo_ext = 127;
	}
	...
}
```
为避免混淆，建议这样定义扩展：

```protoc
message Baz {
	...
}
// This can even be in a different file.
extend Foo {
	optional Baz foo_baz_ext = 127;
}
```

### 选择扩展标签
确保两个用户不使用相同的数字标签添加相同消息类型的扩展非常重要 - 如果扩展名被意外解释为错误类型，则可能会导致数据损坏，可以使用max关键字指定扩展范围达到最大可能字段编号：

```protoc
message Foo {
	extensions 1000 to max;
}
```
可以定义包含保留标签的扩展范围，但 `protocol buffer` 不允许使用这些数字定义实际的扩展名


## OneOf
如果有一个包含多个字段的消息，并且最多可以同时设置一个字段，则可以通过使用 `oneof` 功能强制执行此行为并节省内存；`Oneof` 共享内存中的所有字段，最多可以同时设置一个字段，设置任何成员自动清除所有其他成员

### 使用 Oneof
```protoc
message SampleMessage {
	oneof test_oneof {
		string name = 4;
		SubMessage sub_message = 9;
	}
}
```
`oneof` 具有检查哪个字段被设置的特殊方法

### Oneof 特征
- 设置一个字段将自动清除所有其他成员
- 如果解析器在消息中遇到 `oneof` 的多个成员，则只有最后一个成员才能在解析的消息中使用
- `oneof` 不能是 `repeated`
- 反射 API 适用于 `oneof` 字段
- 如果使用 Cpp，请确保代码不会导致内存崩溃
- 在 Cpp 中，如果您使用 `oneofs Swap()` 两个消息，每个消息的 `oneof` 字段将最终与另一个的相同

### 向后兼容性问题
添加或删除一个字段时，如果检查一个返回值 `None/NOT_SET` 的值，则可能意味着 `oneof` 尚未设置或已被设置为该 `oneof` 的不同版本中的字段

### 标签重用问题
- 将字段移入或移出 `oneof`：在消息序列化和解析后，可能会丢失一些信息
- 删除 `oneof` 字段并将其添加回：这可能会在消息序列化和解析后清除当前设置的字段
- 拆分或合并 `oneof`：这与移动常规字段有类似的问题


## Maps
创建一个关联映射作为数据定义的一部分：
`map<key_type, value_type> map_field = N;` 其中 `key_type` 可以是任何整数或字符串类型，`value_type` 可以是除了 map 之外的任何类型

- `map` 不支持扩展 `extension` (`proto2`)
- `map` 字段不能是 `repeated` (`proto2` 不能使用 `repeated`, `optional`, `required`)
- `map` 的定义格式排序和 `map` 迭代排序未定义
- 当为 `.proto` 生成文本格式时，`map` 按键排序，数字键按数字排序
- 当从消息解析或合并时，如果有重复的 `map` 键，则使用最后一个键

### 向后兼容性
`map` 语法等同于以下内容：

```protoc
message MapFieldEntry {
	key_type key = 1;
	value_type value = 2;
}
repeated MapFieldEntry map_field = N;
```


## 包 Packages
可以向 `.proto` 文件添加可选的包说明符，以防止协议消息类型之间的名称冲突

```protoc
package foo.bar;
message Open { ... }
```
包说明符对生成的代码的影响取决于语言

### 包和名称解析
类型名称解析与 Cpp 一样：首先搜索最内层的范围，然后再搜索最下层的范围，每个包被认为是“内部”到其父包


## 定义服务
如果要使用 RPC 系统的消息类型，可以在 `.proto` 文件中定义一个 RPC 服务接口，`protocol buffer` 将根据选择的语言生成服务接口代码和存根

```protoc
service SearchService {
	rpc Search (SearchRequest) returns (SearchResponse);
}
```


## JSON 映射
如果 JSON 编码数据中缺少值，或者其值为空，则在解析为 `protocol buffer` 时将被解释为适当的默认值；如果一个字段在 `protocol buffer` 具有默认值，则默认情况下将在 JSON 编码数据中省略该值，以节省空间

- `message`: object
- `enum`: string
- `map<K,V>`: object
- `repeated V`: array
- `bool`: true, false
- `string`: string
- `bytes`: base64 string
- `int32, fixed32, uint32`: number
- `int64, fixed64, uint64`: string
- `float, double`: number
- `Any`: object
- `Timestamp`: string 使用 RFC 3339
- `Duration`: string
- `Struct`: object
- `Wrapper types`: 各种类型
- `FieldMask`: string
- `ListValue`: array
- `Value`: value
- `NullValue`: null


## Options
`.proto` 文件中的单个声明可以使用多个 `Options` 进行注释  
常用选项：

- `java_package`: 文件选项，用于生成的 Java 类的包：`option java_package = "com.example.foo";`
- `java_multiple_files`: 文件选项，影响在包级别定义顶级消息，枚举和服务：`option java_multiple_files = true;`
- `java_outer_classname`: 文件选项，要生成的最外层Java类的类名：`option java_outer_classname = "Ponycopter";`
- `optimize_for`: 文件选项，可以设置为 `SPEED`，`CODE_SIZE` 或 `LITE_RUNTIME`：例如 `option optimize_for = CODE_SIZE;`
	- `SPEED`: `protocol buffer` 将生成用于序列化，解析和执行其他常见操作的代码
	- `CODE_SIZE`: `protocol buffer` 将生成最小类，并且将依赖于共享的基于反射的代码来实现串行化，解析和各种其他操作
	- `LITE_RUNTIME`: `protocol buffer` 将生成仅依赖于 `lite` 运行时库（`libprotobuf-lite`而不是`libprotobuf`）的类
- `cc_generic_services, java_generic_services, py_generic_services`: 文件选项，是否应分别基于 Cpp，Java 和 Python 中的服务定义生成抽象服务代码，默认为 `true`
- `cc_enable_arenas`: 文件选项，为 Cpp 生成的代码启用 `arena allocation`
- `objc_class_prefix`: 文件选项，将 Objective-C 类前缀设置为所有 Objective-C 生成的类和本枚举的枚举
- `deprecated`: 字段选项，设置为 `true`，则表示该字段已被弃用，不应由新代码使用：`int32 old_field = 6 [deprecated=true];`
- `message_set_wire_format`: (`proto2`)消息选项，设置为 `true`，则该消息使用不同的二进制格式
- `packed`: (`proto2`): 字段选项，如果在基本数字类型的重复字段上设置为 `true`，则使用更紧凑的编码

### 自定义选项
`protocol buffer` 允许定义和使用自己的选项，定义自己的选项只是扩展 `google/protobuf/descriptor.proto`，可以为 `protocol buffers` 中的每种构造定义自定义选项

```protoc
import "google/protobuf/descriptor.proto";

extend google.protobuf.FileOptions {
	optional string my_file_option = 50000;
}
extend google.protobuf.MessageOptions {
	optional int32 my_message_option = 50001;
}
extend google.protobuf.FieldOptions {
	optional float my_field_option = 50002;
}
extend google.protobuf.EnumOptions {
	optional bool my_enum_option = 50003;
}
extend google.protobuf.EnumValueOptions {
	optional uint32 my_enum_value_option = 50004;
}
extend google.protobuf.ServiceOptions {
	optional MyEnum my_service_option = 50005;
}
extend google.protobuf.MethodOptions {
	optional MyMessage my_method_option = 50006;
}

option (my_file_option) = "Hello world!";

message MyMessage {
	option (my_message_option) = 1234;

	optional int32 foo = 1 [(my_field_option) = 4.5];
	optional string bar = 2;
}

enum MyEnum {
	option (my_enum_option) = true;

	FOO = 1 [(my_enum_value_option) = 321];
	BAR = 2;
}

message RequestType {}
message ResponseType {}

service MyService {
	option (my_service_option) = FOO;

	rpc MyMethod(RequestType) returns(ResponseType) {
		// Note:  my_method_option has type MyMessage.  We can set each field
		//   within it using a separate "option" line.
		option (my_method_option).foo = 567;
		option (my_method_option).bar = "Some string";
	}
}
```
读取自定义选项值类似于读取扩展 `extension`：

```java
String value = MyProtoFile.MyMessage.getDescriptor().getOptions().getExtension(MyProtoFile.myOption);
```
如果要在包定义的程序包之外使用自定义选项，则必须在选项名称前面加上程序包名称

```protoc
// foo.proto
import "google/protobuf/descriptor.proto";
package foo;
extend google.protobuf.MessageOptions {
	optional string my_option = 51234;
}

// bar.proto
import "foo.proto";
package bar;
message MyMessage {
	option (foo.my_option) = "Hello world!";
}
```
每个选项类型（文件级别，消息级别，字段级别等）都有自己的标签数字空间


## 生成类
```shell
protoc --proto_path=IMPORT_PATH --cpp_out=DST_DIR --java_out=DST_DIR --python_out=DST_DIR --go_out=DST_DIR --ruby_out=DST_DIR --javanano_out=DST_DIR --objc_out=DST_DIR --csharp_out=DST_DIR path/to/file.proto
```

- `IMPORT_PATH` 指定在解析导入指令时查找 `.proto` 文件的目录
- 可以提供一个或多个输出指令；如果 `DST_DIR` 以 `.zip` 或 `.jar` 结尾，编译器将把输出写入具有给定名称的单个 `ZIP` 格式归档文件
- 必须提供一个或多个 `.proto` 文件作为输入
