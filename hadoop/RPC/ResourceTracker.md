# ResourceTracker RPC 请求流程

## proto 定义
```protoc
option java_package = "org.apache.hadoop.yarn.proto";  // 输出 java package
option java_outer_classname = "ResourceTracker";       // 输出 java 类名
option java_generic_services = true;                   // 编译 Service
option java_generate_equals_and_hash = true;           // 生成 equals() 和 hash()
package hadoop.yarn;

import "yarn_server_common_service_protos.proto";      // 导入引用 proto

service ResourceTrackerService {
	// RegisterNodeManagerResponseProto  registerNodeManager(RegisterNodeManagerRequestProto);
	rpc registerNodeManager(RegisterNodeManagerRequestProto) returns (RegisterNodeManagerResponseProto);
	// NodeHeartbeatResponseProto nodeHeartbeat(NodeHeartbeatRequestProto);
	rpc nodeHeartbeat(NodeHeartbeatRequestProto) returns (NodeHeartbeatResponseProto);
}
```





