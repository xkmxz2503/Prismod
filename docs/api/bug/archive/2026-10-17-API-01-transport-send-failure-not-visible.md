# API-2026-10-17-01：策略发送异常被传输层静默吞掉

- 类别：缺陷（诊断不可见）
- 优先级：P1
- 状态：已修复并验证
- 发现日期：2026-10-17
- 关联记录：无

## 证据

历史实现中，`PolicyNetwork.broadcast` 和 `PolicyNetwork.send` 曾捕获 `RuntimeException` 后忽略异常（`../../../../src/main/java/com/xkmxz/prismod/network/transport/PolicyNetwork.java`）。`ServerFilterDispatcher` 外层虽然会记录发送异常，但传输层吞掉异常后外层无法观察到它（`../../../../src/main/java/com/xkmxz/prismod/server/application/ServerFilterDispatcher.java`）。该段描述的是修复前行为。

修复实现已改为统一失败报告路径：发送异常仍不向调用方抛出，但会记录包含方向、通道、操作、请求 ID、策略 ID、玩家 UUID 和异常堆栈的警告。

现有 `ServerFilterDispatcherTest.transportFailureDoesNotChangeMeaningOfCommittedReceipt` 验证发送失败不会改变已提交回执；`PolicyNetworkDiagnosticsTest` 覆盖广播、指定玩家和空消息的传输诊断路径。

## 影响

服务端策略状态和回执仍表示本地提交成功，这是既定契约；但实际通道发送抛出异常时没有可检索诊断，运维者难以解释客户端状态未更新。

## 复现或验证步骤

静态证据已确认。Forge 运行时网络故障仍未在游戏内复现；自动化测试验证失败上下文进入错误处理器且保留原始异常。

已执行并通过：

```text
.\\gradlew.bat cleanTest test --rerun-tasks --tests com.xkmxz.prismod.network.transport.PolicyNetworkDiagnosticsTest --tests com.xkmxz.prismod.network.PolicyNetworkContractTest --tests com.xkmxz.prismod.server.application.ServerFilterDispatcherTest --console=plain
```

## 处理

保持“回执只表示服务端本地提交”的语义。`PolicyTransportErrorHandler` 负责统一规范化失败上下文并记录可检索警告，不向业务层传播、不重试、不回滚。

## 修复与验证结果

已实施传输层失败诊断，并增加广播、指定玩家发送失败和空消息的回归测试。指定测试已通过，服务端提交回执语义保持不变，因此本记录归档。
