# Java Generated Code

## 编译器调用
```protoc
protoc --proto_path=src --java_out=build/gen src/foo.proto
```

## Packages
如果没有 `java_package`，则使用 `package` 声明作为输出 `java` 包名

```protoc
package foo.bar;
option java_package = "com.example.foo.bar";
```

## 消息
消息生成的类基于 `GeneratedMessage`，并且是 `final` 的

- `option optimize_for = CODE_SIZE;` 基于反射实现，减少了生成的代码的大小，但也降低了性能
- `option optimize_for = LITE_RUNTIME;` 实现 `MessageLite` 接口，不支持描述符或反射
- 消息静态方法
	- `static Foo getDefaultInstance()` 返回 `Foo` 的单例实例
	- `static Descriptor getDescriptor()` 返回类型的描述符
	- `static Foo parseFrom(...)` 从给定的源解析一个类型为 `Foo` 的消息
	- `static Parser parser()` 返回 `Parser` 的一个实例
	- `Foo.Builder newBuilder()` 创建 `Builder`
	- `Foo.Builder newBuilder(Foo prototype)` 创建 `Builder`，所有字段初始化为与 `prototype` 相同

## Builder
消息对象是不可变的，就像 `Java String` 一样。要构建消息对象，您需要使用 `Builder`，每个消息类都有自己的 `Builder` 类

## 子 Builder
包含子消息的消息，编译器还生成子 `Builder`

## 嵌套类型
消息可以在另一个消息中声明

## 字段
`protocol buffer` 编译器为 `.proto` 文件中的消息中定义的每个字段生成一组访问方法，仅在 `Builder` 中定义修改值的方法

### Singular 字段 (proto2)
```protoc
optional int32 foo = 1;
required int32 foo = 1;
```
编译器将在消息类及其 `Builder` 中生成以下访问器方法：

- `boolean hasFoo()`
- `int getFoo()`
编译器将仅在消息的 `Builder` 中生成以下方法：

- `Builder setFoo(int value)`
- `Builder clearFoo()`

### Singular 字段 (proto3)
```protoc
int32 foo = 1;
```
编译器将在消息类及其 `Builder` 中生成以下访问器方法：

- `int getFoo()`

编译器将仅在消息的 `Builder` 中生成以下方法：

- `Builder setFoo(int value)`
- `Builder clearFoo()`

嵌入式消息字段生成额外访问方法：

- `boolean hasFoo()`

编译器生成两个访问器方法，允许访问相关的子 `Builder` 以获取消息类型。消息类及其 `Builder` 中生成以下方法：

- `FooOrBuilder getFooOrBuilder()`

编译器仅在消息的 `Builder` 中生成以下方法：

- `Builder getFooBuilder()`

对于枚举字段类型，在消息类及其 `Builder` 中生成一个附加的访问器方法：

- `int getFooValue()`

编译器将仅在消息的 `Builder` 中生成以下附加方法：

- `Builder setFooValue(int value)`

### Repeated 字段
```protoc
repeated int32 foo = 1;
```
编译器将在消息类及其 `Builder` 中生成以下访问器方法：

- `int getFooCount()`
- `int getFoo(int index)`
- `List<Integer> getFooList()` 返回的列表对于消息类是不可变的

编译器将仅在消息的 `Builder` 中生成以下方法：

- `Builder setFoo(int index, int value)`
- `Builder addFoo(int value)`
- `Builder addAllFoo(Iterable<? extends Integer> value)`
- `Builder clearFoo()`

编译器在消息类及其 `Builder` 中为消息类型生成以下附加访问器方法，允许访问相关的子 `Builder`：

- `FooOrBuilder getFooOrBuilder(int index)` 如果从消息类调用的，将始终返回消息
- `List<FooOrBuilder> getFooOrBuilderList()` 如果从消息类调用，将始终返回不可变的消息列表

编译器将仅在消息的 `Builder` 中生成以下方法：

- `Builder getFooBuilder(int index)`
- `Builder addFooBuilder(int index)`
- `Builder addFooBuilder()`
- `Builder removeFoo(int index)`
- `List<Builder> getFooBuilderList()`

对于 `repeated` 枚举字段，编译器将在消息类及其 `Builder` 中生成以下附加方法：

- `int getFooValue(int index)`
- `List getFooValueList()`

对于 `repeated` 枚举字段，编译器将仅在消息的 `Builder` 中生成以下附加方法：

- `Builder setFooValue(int index, int value)`

### Oneof 字段
```protoc
oneof oneof_name {
    int32 foo = 1;
    ...
}
```
编译器将在消息类及其 `Builder` 中生成以下访问器方法：

- `boolean hasFoo()`
- `int getFoo()`

编译器将仅在消息的 `Builder` 中生成以下方法：

- `Builder setFoo(int value)`
- `Builder clearFoo()`

### Map 字段
```protoc
map<int32, int32> weight = 1;
```
编译器将在消息类及其 `Builder` 中生成以下访问器方法：

- `Map<Integer, Integer> getWeightMap()`
- `int getWeightOrDefault(int key, int default)`
- `int getWeightOrThrow(int key)`
- `boolean containsWeight(int key)`
- `int getWeightCount()`

编译器将仅在消息的 `Builder` 中生成以下方法：
- `Builder putWeight(int key, int value)`
- `Builder putAllWeight(Map<Integer, Integer> value)`
- `Builder removeWeight(int key)`
- `Builder clearWeight()`
- `@Deprecated Map<Integer, Integer> getMutableWeight()`


## Any
```protoc
import "google/protobuf/any.proto";

message ErrorStatus {
  string message = 1;
  google.protobuf.Any details = 2;
}
```
Any 提供以下代码来压缩、解压缩一个消息：

```java
class Any {
	public static Any pack(Message message);
	public static Any pack(Message message, String typeUrlPrefix);
	
	public <T extends Message> boolean is(class<T> clazz);
	
	public <T extends Message> T unpack(class<T> clazz) throws InvalidProtocolBufferException;
}
```


## Oneofs
```protoc
oneof oneof_name {
	int32 foo_int = 4;
	string foo_string = 9;
}
```
将生成以下代码：

```java
public enum OneofNameCase implements com.google.protobuf.Internal.EnumLite {
	FOO_INT(4),
	FOO_STRING(9),
	...
	ONEOFNAME_NOT_SET(0);
};
```
枚举类型的值具有以下特殊方法：

- `int getNumber()`
- `static OneofNameCase forNumber(int value)`

编译器将在消息类及其 `Builder` 中生成以下访问器方法：

- `OneofNameCase getOneofNameCase()`

编译器将仅在消息的 `Builder` 中生成以下方法：

- `Builder clearOneofName()`


## 枚举
```protoc
enum Foo {
	VALUE_A = 0;
	VALUE_B = 5;
	VALUE_C = 1234;
}
```
生成的枚举类型的值具有以下特殊方法：

- `int getNumber()`
- `EnumValueDescriptor getValueDescriptor()`
- `EnumDescriptor getDescriptorForType()`

`Foo` 枚举类型包含以下静态方法：

- `static Foo forNumber(int value)`
- `static Foo valueOf(int value)`
- `static Foo valueOf(EnumValueDescriptor descriptor)`
- `EnumDescriptor getDescriptor()`

枚举中允许字段的标签值相同，生成的枚举类中标签值相同的字段为别名：

```protoc
enum Foo {
	BAR = 0;
	BAZ = 0;
}
```
```java
static final Foo BAZ = BAR;
```


## 服务
打开生成服务代码的字段，编译器将生成特定的服务代码，默认为 `false`：

```protoc
option java_generic_services = true;
```

### 接口
```protoc
service Foo {
	rpc Bar(FooRequest) returns(FooResponse);
}
```
`protocol buffer` 编译器将生成一个抽象类 `Foo` 来表示服务；对于服务定义中定义的每个方法，`Foo` 将有一个抽象方法：

```java
abstract void bar(RpcController controller, FooRequest request, RpcCallback<FooResponse> done);
```
`Foo` 是 `Service` 接口的实现：

- `getDescriptorForType`
- `callMethod` 根据提供的方法描述符确定调用哪个方法，并直接调用它
- `getRequestPrototype`
- `getRequestPrototype` 返回给定方法的正确类型的请求或响应的默认实例

静态方法：

- `static ServiceDescriptor getDescriptor()`

在实现自己的服务时，有两个选择：

- `Foo` 子类化并根据需要实现其方法，然后将子类的实例直接传递给 RPC 服务器实现
- 实现 `Foo.Interface` 并使用 `Foo.newReflectiveService(Foo.Interface)` 构造一个包装它的服务，然后将包装器传递给 RPC 实现

### Stub
编译器还生成每个服务接口的“存根”实现，由客户端希望向执行服务的服务器发送请求使用  
`Foo.Stub` 是 `Foo` 的子类，它还实现了以下方法：

- `Foo.Stub(RpcChannel channel)`
- `RpcChannel getChannel()`

`Protocol Buffer` 不包括 RPC 实现。但是，它包括将生成的服务类连接到任意RPC实现所需的所有工具；只需要提供 `RpcChannel` 和 `RpcController` 的实现

### 阻塞式接口
上述 RPC 类都具有非阻塞语义：当您调用方法时，您将提供一个回调对象，该方法将在方法完成后被调用；`Protocol Buffer` 编译器还会生成您的服务类的阻止版本：

```protoc
abstract FooResponse bar(RpcController controller, FooRequest request) throws ServiceException;
```


## 插件插入点
可以使用给定的插入点名称插入以下类型的代码：

- `outer_class_scope` 属于文件外部类的成员声明
- `class_scope:TYPENAME` 属于消息类的成员声明
- `builder_scope:TYPENAME` 属于消息 `Builder` 类的成员声明
- `enum_scope:TYPENAME` 属于枚举类的成员声明
