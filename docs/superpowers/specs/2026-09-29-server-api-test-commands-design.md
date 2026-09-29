# 服务端 API 测试命令设计

## 目标

提供一组专用于手工验证 `FilterServerApi` 的服务端命令。命令只调用公开服务端 API，反馈展示 API 返回的结构化结果；客户端实际滤镜效果由测试人员在游戏中观察。

## 命令入口

测试命令使用独立根命令，避免与日常运维命令混淆：

```text
/prismod_server api help
/prismod_server api status
/prismod_server api select broadcast <filter> [strength]
/prismod_server api select player <target> <filter> [strength]
/prismod_server api override broadcast <filter> <priority> [strength]
/prismod_server api override player <target> <filter> <priority> [strength]
/prismod_server api clear selection
/prismod_server api clear owner
/prismod_server api clear all
/prismod_server api reset
```

所有测试覆盖使用固定 owner `prismod-server-api-test`。命令要求权限等级 2。

## 行为约束

- `select` 分别调用 `FilterServerApi.broadcastSelection` 和 `FilterServerApi.sendSelection`。
- `override` 分别调用 `FilterServerApi.broadcastOverride` 和 `FilterServerApi.sendOverride`，测试 owner 固定为上述值。
- `clear selection` 调用 `FilterServerApi.clearSelection`。
- `clear owner` 调用 `FilterServerApi.clearBroadcastOverride`，只清理测试 owner。
- `clear all` 调用 `FilterServerApi.clearAllOverrides`。
- `reset` 依次调用 `clearSelection` 和 `clearAllOverrides`，用于清理测试前后的服务端状态。
- `status` 只读取 `FilterServerApi.status` 和 `FilterServerApi.filterStatus`。
- 命令适配器不得引用服务端应用实现、策略状态或网络实现。

每个变更操作都显示 `OperationResult` 的状态、反馈代码、request ID、目标、影响数量和 generation。状态命令显示协议、通道、全局选择、强度、覆盖数量、玩家选择数量、玩家覆盖数量和 generation。

## 国际化

帮助、状态、结果和 reset 反馈全部使用 `Component.translatable`。新增键必须同时存在于 `en_us.json` 和 `zh_cn.json`，Java 命令代码不得包含自然语言文本。

## 验收

- 命令树可以解析上述所有路径。
- 每条变更命令只通过 `FilterServerApi` 访问业务。
- 非法滤镜、非法强度和非法优先级由 Brigadier 参数校验处理。
- API 返回的失败结果通过失败翻译键反馈，命令返回值为 0；成功返回值为 1。
- `reset` 可重复执行且不会抛出异常。
