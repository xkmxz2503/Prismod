# Prismod Java API 规范

本文是客户端公开 Java API 的规范来源，适用于 Minecraft 1.20.1、Forge 47.3.32、Java 17。

## 1. 两个入口

外部客户端模组只通过 `com.xkmxz.prismod.api.client.FilterClientApi` 提交业务或读取数据：

```java
CompletionStage<FilterReceipt> submit(FilterRequest request);
<R> R query(FilterQuery<R> request);
```

`submit` 接收明确类型的请求并交付一次最终回执；`query` 返回最近发布的不可变数据，不修改状态。旧公开业务方法已移除，这是不兼容的客户端 API 调整，不保留 deprecated 别名。

专用服务器不得加载 `api.client`。外部模组不得依赖应用层、注册表、状态管理器、渲染器或内部句柄。

包目录先按两个入口划分，再按类型职责细分：

```text
api/client/
├─ FilterClientApi.java
├─ submit/                         输入相关契约
│  ├─ request/FilterRequest.java    业务请求
│  ├─ model/CustomFilterMetadata.java  注册输入数据
│  │       FilterAdjustments.java      稀疏高级调参
│  ├─ result/FilterReceipt.java     一次性最终回执
│  ├─ listener/                    状态、事件回调接口
│  └─ event/                       不可变通知及事件类型
└─ query/                          查询相关契约
   ├─ request/FilterQuery.java      查询请求
   └─ view/                        FilterSnapshot、FilterResourceView
```

监听通过 submit 建立，相关接口及通知归入 submit；通知引用 query.view 中的只读快照。共享基础类型位于 api.common.submit（结果代码、操作状态）与 api.common.query（共享视图、滤镜及生命周期状态）。此处仅调整包位置，业务语义不变；集成代码需更新 import。

## 2. 输入请求清单

请求位于 `api.client.submit.request.FilterRequest`，以下均为嵌套 record。

| 分类 | 请求 | 含义 |
| --- | --- | --- |
| 注册 | `Register(ownerId, logicalId, postEffect, metadata, sessionId)` | 注册 PostChain；可省略 sessionId，另有推导逻辑 ID 的便捷构造 |
| 释放 | `Release(resourceId)` | 释放注册、覆盖、订阅或 Session |
| 清理注册 | `ClearRegistrations(ownerId)` | 清理 owner 的注册 |
| 临时选择 | `Select(filter, strength[, adjustments])`、`ClearSelection()` | 不写玩家配置 |
| 强制 | `Force(filter, strength[, adjustments])`、`ClearForced()` | 替换或解除旧式强制覆盖 |
| 覆盖 | `Override(ownerId, filter, strength, priority[, adjustments])` | sessionId 可省略；同优先级较新者胜出 |
| 清理覆盖 | `ClearOverrides(ownerId)` | 清理 owner 的覆盖 |
| 监听 | `SubscribeState(ownerId, listener)`、`SubscribeEvents(ownerId, listener)` | 创建订阅，使用资源 ID 取消 |
| Session | `OpenSession(ownerId)`、`ClearSession(sessionId)`、`CloseSession(sessionId)` | 创建、清空或关闭会话 |
| 批量 | `Batch(sessionId, requests)` | 仅接收 Register 和 Override，按顺序尽力执行 |

逻辑滤镜 ID 使用完整 `namespace:path`，例如 `prismod:vintage`；`postEffect` 使用实际 PostChain JSON 路径，例如 `example:shaders/post/debug.json`，不能混用。

强度限制在 `[0, 1]`，非有限值按 `0` 处理。普通选择、强制、优先级覆盖沿用现有规则；强制覆盖可以优先于玩家总开关，渲染不可用时仍回退原色。

## 3. 只读查询清单

查询位于 `api.client.query.request.FilterQuery`，结果类型由查询本身决定。

| 查询 | 返回值 |
| --- | --- |
| `Snapshot()` | `api.client.query.view.FilterSnapshot` |
| `Filters(false)` | 全部滤镜的不可变 `List<FilterDescriptor>` |
| `Filters(true)` | 当前可用滤镜列表 |
| `Resource(resourceId)` | `Optional<FilterResourceView>` |
| `Resources(ownerId)` | owner 当前存活的资源列表 |
| `SessionResources(sessionId)` | Session 当前管理的注册和覆盖列表，不含 Session 自身 |

`FilterResourceView` 包含 UUID、种类、owner、Session UUID、滤镜、优先级、生命周期状态和诊断信息。资源存活不代表赢得覆盖优先级，最终画面状态应查询 Snapshot。

客户端 Snapshot 保存最终滤镜、强度、forced、渲染可用性、玩家选择、回退原因和 generation。它与 `api.common.query.view.FilterSnapshot` 不同，通过 `toCommonSnapshot()` 和 `fromCommonSnapshot(...)` 显式转换。

## 4. 回执、线程与生命周期

```text
submit(创建) → 排队／执行 → 最终 FilterReceipt
                              └─ resourceIds
                                   ├─ query(Resource) 读取当前状态
                                   └─ submit(Release) 结束生命周期
```

`FilterReceipt` 是不可变 record：

| 字段 | 含义 |
| --- | --- |
| `requestId` | 本次客户端操作的 UUID |
| `status`、`code` | 最终状态及稳定反馈代码 |
| `failureReason`、`failureDetail` | 失败分类及诊断详情 |
| `affectedCount` | 影响数量，无变化可成功且为零 |
| `resourceIds` | 本次建立的资源标识；注册失败时也可能包含用于查询诊断和释放的资源 ID |
| `items` | 按输入顺序排列的批量逐项回执 |

异步返回值表示等待过程，最终回执没有 QUEUED。回执不包含 `close()`、`operation()` 或持续变化的资源状态。不要通过 `join()`／`get()` 阻塞客户端线程。

业务请求在客户端线程执行。业务状态和资源视图发布后交付回执及对应事件；回调中再次 submit 的写请求排在当前请求之后。业务失败通过回执表达；成功表示业务状态已提交，不表示 GPU 渲染已完成。

监听器在客户端线程按顺序执行、异常隔离，订阅时不补发初始快照。初始数据单独 query。`FilterEvent.receipt()` 提供操作完成事件的最终回执，替代公开 Operation。

释放后资源查询为空；重复释放成功且影响数量为零。注册替换保持现有版本规则，旧注册 ID 不能注销新版本。

注册失败回执若带有资源 ID，可通过 Resource 查询其 FAILED 状态，也可通过 Release 释放；ClearRegistrations 会一并清理该 owner 的失败注册资源。资源 ID 非空不代表操作成功，应先检查回执 status。

Session 只管理自身创建的注册和覆盖，同 owner 的其他资源不受影响。清空后可复用，关闭后拒绝新增操作；批量允许部分成功，不执行事务回滚。批量项目的 owner 必须与 Session 一致；未指定 sessionId 的项目继承批量 Session。

## 5. 最小客户端示例

```java
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.submit.model.FilterAdjustments;
import com.xkmxz.prismod.api.client.submit.request.FilterRequest;
import com.xkmxz.prismod.api.client.query.request.FilterQuery;
import net.minecraft.resources.ResourceLocation;

var filter = ResourceLocation.fromNamespaceAndPath("prismod", "vintage");

FilterClientApi.submit(new FilterRequest.Override("example-mod", filter, 75F, 100))
        .thenAccept(receipt -> {
            if (receipt.resourceIds().isEmpty()) {
                return; // 根据 status/code/failureReason 处理失败。
            }
            var resourceId = receipt.resourceIds().get(0);
            var resource = FilterClientApi.query(new FilterQuery.Resource(resourceId));
            var snapshot = FilterClientApi.query(new FilterQuery.Snapshot());
            // 示例到此结束；实际集成保存 ID，在业务结束时释放。
            FilterClientApi.submit(new FilterRequest.Release(resourceId));
        });
```

注册使用 `new FilterRequest.Register("example-mod", logicalId, postEffect, metadata)`；回执返回注册资源 ID，注销同样提交 Release。订阅返回订阅资源 ID，也通过 Release 取消。

## 6. 服务端与后续调参边界

服务端 `FilterServerApi` 也只保留 submit/query，使用独立的 ServerFilterRequest 与 ServerFilterQuery；客户端公开契约不变。服务端通过 `prismod:policy` 协议 `2` 下发，协议 `1` 不兼容。requestId 标识本次请求，policyId 标识持续策略，同策略新请求替换旧网络覆盖。服务端 OperationResult 只表示接受并保存策略，不是客户端渲染回执。完整边界见 [API 使用指南](API_使用指南.md) 和 [服务端 API 与命令](服务端_API_与命令.md)。

### 6.1 部分高级调参

高级参数通过 `api.client.submit.model.FilterAdjustments` 作为可选对象提交。它是稀疏更新：未填写的字段保持当前有效值，`clearXxx()` 让该字段恢复继承调试预设。

```java
var adjustments = FilterAdjustments.builder()
        .exposure(25F)
        .saturation(125F)
        .build();

FilterClientApi.submit(new FilterRequest.Select(filter, 75F, adjustments));
```

支持曝光、对比度、高光、阴影、饱和度、色温、色调和伽马。`strength` 仍表示整体混合强度。没有传入调参对象时，继续使用玩家调试预设；API 参数只覆盖明确填写的字段。现有 `client.filter.debug.FilterDebugSettings` 保持内部位置，服务端和网络协议暂未接入调参。

## 7. 常见错误

- 从专用服务器加载客户端 API，或继续调用已移除的旧方法。
- 把回执当成可持续查询的句柄，或把 UUID 当成滤镜逻辑 ID。
- 在客户端线程阻塞等待 CompletionStage。
- 忘记释放资源，或以为 Session 会清理同 owner 的所有资源。
- 只读最终 filter，忽略 selectedFilter、fallbackReason 和 renderAvailable。

## 参数单位（百分制）

客户端公开 API 的 `strength` 使用 `0-100`：`0` 表示不应用滤镜，`100` 表示完整应用。`FilterAdjustments` 是稀疏对象，字段单位如下：

| 字段 | 范围 | 中性值 |
|---|---:|---:|
| exposure | -100 到 100 | 0 |
| contrast | 0 到 200 | 100 |
| highlights / shadows | -100 到 100 | 0 |
| saturation | 0 到 200 | 100 |
| temperature / tint | -100 到 100 | 0 |
| gamma | 10 到 300 | 100 |

示例：

```java
FilterAdjustments.builder()
        .exposure(25)
        .contrast(120)
        .saturation(80)
        .gamma(100)
        .build();
```

调试预设 JSON 同样保存百分制值，格式版本为 `2`。旧格式不会按新单位解释，会回退到默认预设。
