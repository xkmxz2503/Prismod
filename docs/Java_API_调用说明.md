# Prismod Java API 规范

本文档是客户端公开 Java API 的事实来源，适用于 Minecraft 1.20.1、Forge 47.3.32 和 Java 17。

## 1. API 使用边界

客户端公开入口位于 `com.xkmxz.prismod.api.client`。外部模组只依赖公开 API，不直接访问 `ClientFilterApplication`、`FilterManager`、`FilterController`、`FilterRegistry` 或渲染器。

专用服务器不得加载 `api.client` 或任何 `net.minecraft.client` 类。逻辑滤镜 ID 使用完整的 `namespace:path`；注册自定义 PostChain 时，`postEffect` 使用实际资源路径，例如 `example:shaders/post/debug.json`。

## 2. 客户端 API 总览

### 2.1 注册与注销

**规范入口：**

```java
FilterRegistration registration = FilterClientApi.registerCustomFilter(
        "example-mod",
        ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
        new CustomFilterMetadata("filter.example.debug", 0.75F));

registration.close();
```

支持显式逻辑 ID 的重载：

```java
FilterRegistration registration = FilterClientApi.registerCustomFilter(
        ResourceLocation.fromNamespaceAndPath("example", "debug"),
        "example-mod",
        postEffect,
        metadata);
```

`ownerId` 不能为空；强度规范化到 `[0.0, 1.0]`，非有限值按 `0.0` 处理。`FilterRegistration` 表示一个注册句柄，关闭后该注册不再生效。

**兼容入口：** `registerCustomFilterOperation(...)` 继续保留并标记为 deprecated。新代码使用 `registerCustomFilter(...)`，通过返回句柄的 `operation()` 查看创建结果。

### 2.2 普通选择

会话选择不修改玩家配置，适合临时选择：

```java
FilterClientApi.setSessionSelection(filter, 0.5F);
FilterClientApi.clearSessionSelection();
```

不需要观察排队结果时使用以上 void 入口；需要结果时使用 `setSessionSelectionOperation(...)` 和 `clearSessionSelectionOperation()`。

### 2.3 强制滤镜

强制滤镜优先于总开关、F8 循环和普通选择：

```java
FilterClientApi.setForcedFilter(filter, 0.75F);
FilterClientApi.clearForcedFilter();
```

需要观察结果时使用 `setForcedFilterOperation(...)` 和 `clearForcedFilterOperation()`。

### 2.4 覆盖

覆盖按优先级决定生效项，同一优先级使用较新的覆盖：

```java
FilterOverride override = FilterClientApi.createOverride(
        "example-mod", filter, 0.8F, 100);

override.close();
FilterClientApi.clearOverrides("example-mod");
```

**规范入口：** `createOverride(...)`，通过 `override.operation()` 查看创建操作。

**兼容入口：** `createOverrideOperation(...)` 继续保留并标记为 deprecated。

覆盖句柄的生命周期状态只有 `PENDING`、`ACTIVE` 和 `CLOSED`。如果排队创建失败，失败状态、`FilterFailureReason` 和诊断详情以 `override.operation()` 为准；句柄本身不会增加独立的 `FAILED` 状态。

### 2.5 快照查询

```java
FilterSnapshot snapshot = FilterClientApi.snapshot();
snapshot.filter();
snapshot.strength();
snapshot.forced();
snapshot.renderAvailable();
snapshot.selectedFilter();
snapshot.fallbackReason();
snapshot.generation();
```

`snapshot()` 返回不可变的最终渲染视图。渲染资源不可用时，最终滤镜可能回退为 `prismod:original`，但用户选择、回退原因和 generation 会保留。

滤镜列表：

```java
List<FilterDescriptor> all = FilterClientApi.filters();
List<FilterDescriptor> available = FilterClientApi.availableFilters();
```

### 2.6 状态监听

```java
FilterSubscription subscription = FilterClientApi.subscribe(snapshot -> {
    System.out.println(snapshot.filter());
});

FilterSubscription events = FilterClientApi.subscribeEvents(event -> {
    System.out.println(event.type());
});

subscription.close();
events.close();
```

订阅不会补发初始状态；回调在 Minecraft 客户端线程按变更顺序同步执行。监听器异常会被隔离并记录日志。

### 2.7 Owner 会话与批量操作

```java
try (FilterClientSession session = FilterClientApi.openSession("example-mod")) {
    FilterBatchResult<FilterRegistration> result = session.registerAll(requests);
}
```

会话只管理该 owner 创建的注册和覆盖。批量操作按输入顺序尽力执行，混合成功和失败时总体状态为 `PARTIAL`。关闭会话不会取消已经排队的项目；项目执行后会立即关闭对应句柄。

## 3. 服务端 API 总览

服务端公开入口是 `com.xkmxz.prismod.api.server.FilterServerApi`，详细协议说明见 [`API_使用指南.md`](API_使用指南.md) 和 [`服务端_API_与命令.md`](服务端_API_与命令.md)。

| 用途 | 规范入口 |
| --- | --- |
| 查询服务状态 | `status()`、`filterStatus()` |
| 广播选择 | `broadcastSelection(...)` |
| 指定玩家选择 | `sendSelection(...)` |
| 广播覆盖 | `broadcastOverride(...)` |
| 指定玩家覆盖 | `sendOverride(...)` |
| 清理策略 | `clearBroadcastOverride(...)`、`clearPlayerOverride(...)`、`clearAllOverrides()`、`clearSelection()` |

服务端返回 `OperationResult`，表示服务端是否接受并保存策略，不表示客户端已经完成渲染。

服务端写操作使用两个公共请求模型：

- `FilterSelectionRequest(filter, strength)`：`filter` 不能为空；`strength` 在构造时归一化到 `0.0–1.0`，非有限值按 `0.0` 处理；
- `FilterOverrideRequest(ownerId, filter, strength, priority)`：`ownerId` 会去除首尾空白且不能为空，`filter` 不能为空，`strength` 同样归一化到 `0.0–1.0`；同一作用域内使用相同 `ownerId` 会更新该 owner 的覆盖。

这些规则在请求对象创建时执行，因此调用服务端 API 前不需要再复制一套强度或 owner 校验。

## 4. Operation、句柄和线程语义

```text
注册 / 创建覆盖
        │
        ▼
返回生命周期句柄
        │
        ├─ state()
        ├─ isActive()
        ├─ operation()
        └─ close()

Operation：QUEUED → SUCCESS / FAILED
```

- 写操作会自动调度到 Minecraft 客户端线程；
- void 入口适合不关心执行结果的调用；
- `...Operation` 入口适合需要观察排队、完成和失败原因的调用；
- 重复 `close()` 没有副作用；
- 排队期间关闭句柄不会取消已提交的创建；
- 覆盖创建失败通过覆盖句柄的 `operation()` 读取，覆盖状态仍按 `PENDING`、`ACTIVE`、`CLOSED` 表示生命周期；
- 无变化操作仍可返回 `SUCCESS`，但 `affectedCount()` 为 `0`，generation 不递增；
- 状态事件先发布，随后发布一次 `OPERATION_COMPLETED`。

## 5. 规范入口与兼容入口

| 类别 | 方法 | 规则 |
| --- | --- | --- |
| 规范入口 | `registerCustomFilter(...)` | 新代码使用 |
| 兼容入口 | `registerCustomFilterOperation(...)` | deprecated，仅兼容旧调用方 |
| 规范入口 | `createOverride(...)` | 新代码使用 |
| 兼容入口 | `createOverrideOperation(...)` | deprecated，仅兼容旧调用方 |
| 简单调用入口 | `setForcedFilter(...)`、`setSessionSelection(...)` 等 void 方法 | 不需要观察结果时使用 |
| 可观察入口 | `...Operation(...)` 方法 | 需要线程排队或失败信息时使用 |
| 内部实现 | `ClientFilterApplication`、`FilterManager`、`FilterController`、`FilterRegistry` | 外部模组不得依赖 |

本步骤不删除任何兼容入口，也不修改方法签名。

## 6. 最小示例

```java
ResourceLocation filter = ResourceLocation.fromNamespaceAndPath("prismod", "vintage");

FilterClientApi.setForcedFilter(filter, 0.75F);
FilterSnapshot snapshot = FilterClientApi.snapshot();

// 功能关闭时恢复普通选择。
FilterClientApi.clearForcedFilter();
```

## 7. 常见错误

- 把逻辑滤镜 ID 和 PostChain 资源路径混用；
- 从专用服务器加载 `api.client`；
- 直接调用内部状态类；
- 忘记保存并关闭注册或覆盖句柄；
- 把服务端 `OperationResult` 当成客户端渲染回执；
- 只读取最终 `filter()`，却忽略回退时的 `selectedFilter()` 和 `fallbackReason()`。
