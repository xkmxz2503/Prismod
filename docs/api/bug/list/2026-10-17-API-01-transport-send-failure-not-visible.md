# API-2026-10-17-01：策略发送异常被传输层静默吞掉

- 类别：缺陷（诊断不可见）
- 优先级：P1
- 状态：待修复
- 发现日期：2026-10-17
- 关联记录：无

## 证据

`PolicyNetwork.broadcast` 和 `PolicyNetwork.send` 捕获 `RuntimeException` 后忽略异常（`../../../../src/main/java/com/xkmxz/prismod/network/transport/PolicyNetwork.java`）。`ServerFilterDispatcher` 外层虽然会记录发送异常，但传输层吞掉异常后外层无法观察到它（`src/main/java/com/xkmxz/prismod/server/application/ServerFilterDispatcher.java`）。

现有 `ServerFilterDispatcherTest.transportFailureDoesNotChangeMeaningOfCommittedReceipt` 验证发送失败不会改变已提交回执，但测试注入的是会抛异常的 runtime，不覆盖真实 `PolicyNetwork` 的吞异常路径。

## 影响

服务端策略状态和回执仍表示本地提交成功，这是既定契约；但实际通道发送抛出异常时没有可检索诊断，运维者难以解释客户端状态未更新。

## 复现或验证步骤

静态证据已确认。尚无 Forge 运行时网络故障复现，也没有针对传输层日志行为的测试。

## 处理

保持“回执只表示服务端本地提交”的语义。修复时应让底层发送异常至少产生可检索警告；是否由传输层记录或向应用层传播，尚未实施决定。

## 修复与验证结果

未实施；待补充传输层失败诊断后，增加针对实际传输适配器的验证，并确认回执语义不变。
