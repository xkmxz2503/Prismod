# Prismod API、指令与网络架构审查稿

本文档用于审查 Prismod 当前的 API、指令、抽象层和网络层。它以源码现状为事实来源，区分已经实现的调用链、必须保持的边界和需要在后续审查中确认的架构决策。

适用版本：Minecraft 1.20.1、Forge 47.3.32、Java 17。

## 1. 审查范围和目标

本次审查覆盖：

- 客户端公开 API；
- 服务端公开 API；
- 服务端管理指令；
- 客户端 API 测试指令；
- 服务端 API 测试指令；
- 客户端和服务端的应用/抽象层；
- `prismod:policy` 冻结网络层；
- 客户端网络接收适配器；
- 客户端、服务端和网络之间的依赖方向。

审查目标不是增加另一套兼容 API，而是确认所有入口都通过统一的契约和用例层完成调用，避免指令或网络实现直接修改内部状态。

## 2. 当前实现分层

### 2.1 客户端调用链

```mermaid
flowchart LR
    A["外部客户端模组"] --> B["FilterClientApi"]
    T["/prismod_client api 测试指令"] --> B
    B --> C["ClientFilterApplication"]
    C --> D["FilterRegistry"]
    C --> E["FilterManager"]
    E --> F["不可变快照与事件"]
    E --> G["WorldFilterRenderer"]
```

实际职责如下：

| 层 | 当前实现 | 允许承担的职责 |
| --- | --- | --- |
| 公开 API | `api/client/FilterClientApi` | 提供稳定方法、请求入口和公开返回类型 |
| 客户端应用层 | `client/application/ClientFilterApplication` | 参数检查、owner 归一化、客户端线程调度、状态操作和操作结果发布 |
| 注册表适配器 | `client/filter/registry/FilterRegistry` | 发现、注册、注销和描述滤镜 |
| 状态适配器 | `client/filter/state/FilterManager` | 选择、强制状态、覆盖、订阅和渲染可用性 |
| 渲染适配器 | `client/render/WorldFilterRenderer` | 消费内部状态并执行世界画面后处理 |
| 测试入口 | `command/test/client/PrismodClientApiCommands` | 模拟外部模组调用公开 API，并显示测试结果 |

外部模组不应引用 `ClientFilterApplication`、`FilterRegistry`、`FilterManager` 或渲染类。客户端测试指令也不应绕过 `FilterClientApi`。

### 2.2 服务端调用链

```mermaid
flowchart LR
    M["服务端模组"] --> A["FilterServerApi"]
    C["/prismod 管理指令"] --> A
    T["/prismod_server api 测试指令"] --> A
    A --> U["ServerFilterApplication"]
    U --> P["PolicyTransport"]
    P --> N["PolicyNetwork"]
    N --> R["客户端网络接收适配器"]
    R --> CA["FilterClientApi"]
```

实际职责如下：

| 层 | 当前实现 | 允许承担的职责 |
| --- | --- | --- |
| 服务端公开 API | `api/server/FilterServerApi` | 暴露服务端查询、选择、覆盖和清理操作 |
| 服务端应用层 | `server/application/ServerFilterApplication` | 校验请求、保存策略、递增 generation、调用网络端口、登录补发 |
| 管理指令适配器 | `command/server/PrismodServerCommands` | 解析 Brigadier 参数、创建公共请求、转换 i18n 反馈 |
| 测试指令适配器 | `command/test/server/PrismodServerApiCommands` | 通过公开服务端 API 执行逐步测试 |
| 网络抽象端口 | `network/contract/PolicyTransport` | 只暴露广播和指定玩家发送能力 |
| Forge 网络实现 | `network/transport/PolicyNetwork` | 创建通道、编解码、路由和客户端 sink 分发 |
| 客户端接收适配器 | `client/network/PrismodClientNetwork` | 把策略消息翻译为客户端 submit 请求，按策略 ID 跟踪最近请求及客户端创建结果 |

服务端命令不得直接调用 `ServerFilterApplication` 的状态表；服务端应用层不得直接依赖 `SimpleChannel`、`PacketDistributor` 或编解码细节。

## 3. 抽象层设计

### 3.1 抽象层的边界

```text
外部调用者
    -> 公开 API 契约
    -> 请求归一化和用例层
    -> 平台/状态/渲染/网络适配器
    -> 外部系统
```

抽象层的作用是固定上层看到的输入、输出和生命周期，不让上层感知后端存储、Forge 网络类型或客户端渲染实现。

### 3.2 客户端抽象契约

客户端公开契约包括：

- `FilterRequest`：有类型的业务输入，包含监听和生命周期请求；
- `FilterQuery<R>`：有类型的只读查询；
- `FilterReceipt`：本次操作的不可变最终回执；
- `FilterResourceView`：资源 UUID、归属和生命周期的只读视图；
- `FilterSnapshot`、`FilterDescriptor`：状态和滤镜描述；
- `FilterEvent`：通知数据，操作完成事件携带 `receipt()`；
- `CustomFilterMetadata`：显示翻译键和默认强度。

注册、覆盖、订阅及 Session 的可操作句柄仅在内部使用。公开契约不暴露状态管理器、渲染资源或 Forge 网络对象。

### 3.3 服务端抽象契约

服务端公开契约包括：

- `FilterSelectionRequest`：逻辑滤镜 ID 和强度；
- `FilterOverrideRequest`：owner、逻辑滤镜 ID、强度和 priority；
- `ServerFilterRequest`：Select、Override、ClearOverride、ClearAllOverrides、ClearSelection 五类输入；
- `ServerFilterTarget`：广播或玩家 UUID，不保存 ServerPlayer；
- `ServerFilterQuery<R>`：Status 和 FilterStatus 两类只读查询；
- `OperationResult`：请求 ID、状态、反馈代码、失败原因与详情、目标、影响数量、策略 ID 列表和 generation；
- `ServerStatus`：初始化状态、网络通道和协议版本；
- `ServerFilterStatus`：服务端策略计数和全局选择摘要。

服务端结果不以自然语言代替稳定代码。failureDetail 用于诊断；命令适配器根据 `status` 和 `code` 选择翻译键。回执不可变，禁止 QUEUED，不包含参数 Map、可操作句柄或后续查询。

### 3.4 抽象层的统一入口规则

| 调用来源 | 正确入口 | 禁止路径 |
| --- | --- | --- |
| 客户端外部模组 | `FilterClientApi` | 直接操作 `FilterManager` 或 `FilterRegistry` |
| 客户端测试指令 | `FilterClientApi` | 直接构造内部覆盖或修改配置 |
| 服务端外部模组 | `FilterServerApi` | 直接操作 `ServerFilterApplication` 状态表 |
| 服务端管理指令 | `FilterServerApi` | 指令中重复实现业务规则 |
| 服务端 API 测试指令 | `FilterServerApi` | 直接调用网络或策略状态 |
| 服务端用例层 | `PolicyTransport` | 直接使用 `SimpleChannel` 或 `PacketDistributor` |
| 客户端网络接收 | `FilterClientApi` | 直接调用 `FilterManager` |

## 4. API 与指令架构

### 4.1 客户端 API

公开门面 `FilterClientApi` 只保留 `submit(FilterRequest)` 和 `query(FilterQuery<R>)`。

submit 承接注册、选择、强制、覆盖、订阅和 Session，返回 CompletionStage，最终交付一次 FilterReceipt。query 读取最近发布的不可变快照、列表及资源视图，不创建资源或订阅。旧公开业务方法已删除，不保留兼容入口。

创建回执返回 UUID，释放使用 Release，后续状态通过 query 查询。资源不存在返回空；重复释放成功且影响数量为零。Session 只管理自身注册和覆盖，清空可复用，关闭后拒绝新增；批量按顺序尽力执行。

业务状态及资源视图发布后交付回执及事件，回调内重入的写请求排后。内部继续复用注册、状态和渲染实现；不改变现有优先级或画面行为。调参留待独立步骤，FilterDebugSettings 不迁移、不直接公开。

### 4.2 服务端 API

公开入口为 `com.xkmxz.prismod.api.server.FilterServerApi`，业务方法只有 `CompletionStage<OperationResult> submit(ServerFilterRequest)` 和 `<R> R query(ServerFilterQuery<R>)`。频道和协议常量继续保留，旧业务方法删除，无兼容别名。

类型按入口职责归类：服务端目标、请求与操作结果位于 `api.server.submit.model`、`api.server.submit.request`、`api.server.submit.result`，查询请求与状态快照位于 `api.server.query.request`、`api.server.query.view`。共享类型也先按 `api.common.submit`、`api.common.query` 分组，再区分结果、视图和状态；不复制跨入口复用的枚举。

- submit 接收选择、覆盖和清理请求，使用明确的广播或玩家 UUID 目标；
- query 读取最近发布的不可变 ServerStatus 与 ServerFilterStatus；
- 写请求在服务端线程串行执行，状态发布后交付一次最终回执；
- 请求 ID 每次独立生成；同目标范围、同 owner 更新覆盖时策略 ID 稳定，参数替换；
- 服务器不可用返回 UNAVAILABLE，指定玩家离线返回 NOT_FOUND 且不保存请求；
- 重复清理成功且影响数量为零；新建或更新回执携带策略 ID，失败和清理为空。

所有变更最终进入 `ServerFilterApplication`，由该层统一完成状态变更、generation 递增和策略消息发送。

本步不开放远程注册、Session、批量、订阅、调参或客户端状态查询；客户端公开契约、渲染行为不变。

### 4.3 服务端管理指令

```text
/prismod help
/prismod status
/prismod filter select broadcast <id> [strength]
/prismod filter select player <target> <id> [strength]
/prismod filter override broadcast <owner> <id> <priority> [strength]
/prismod filter override player <target> <owner> <id> <priority> [strength]
/prismod filter clear selection
/prismod filter clear broadcast <owner>
/prismod filter clear player <target> <owner>
/prismod filter clear all
```

`filter` 管理分支要求 OP 权限等级 2。命令代码只负责 Brigadier 参数解析、公共请求创建、调用 API 和 i18n 输出。命令反馈必须使用 `Component.translatable(...)`，不得把自然语言写入 Java。

### 4.4 API 测试指令

客户端测试指令：

```text
/prismod_client api help
/prismod_client api list
/prismod_client api snapshot
/prismod_client api force <filter> [strength]
/prismod_client api clear
/prismod_client api watch
/prismod_client api watch_events
/prismod_client api owner_clear
```

固定 owner 为 `prismod-api-test-mod`，用于验证注册、快照、覆盖、清理和事件发布。

服务端测试指令：

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

固定 owner 为 `prismod-server-api-test`，要求 OP 权限等级 2。测试指令只能通过 `FilterServerApi` 验证服务端请求、状态和结果，实际画面由测试人员在客户端观察。

## 5. 网络层架构

### 5.1 冻结契约

| 项目 | 当前固定值 |
| --- | --- |
| 通道 | `prismod:policy` |
| 协议版本 | `2`；拒绝协议 `1`，无兼容分支 |
| 抽象端口 | `PolicyTransport` |
| Forge 实现 | `PolicyNetwork` |
| 消息类型 | `PolicyMessage` |
| 编解码 | `FriendlyByteBuf`，仅存在于网络实现边界 |

`PolicyTransport` 当前只提供服务端发送能力：

```java
void broadcast(PolicyMessage message);
void send(ServerPlayer player, PolicyMessage message);
```

客户端接收 sink 注册属于 `PolicyNetwork` 的客户端实现能力，不属于服务端业务端口；客户端启动时直接注册 `PrismodClientNetwork::handle`。

业务层只能依赖这个端口；`SimpleChannel`、`PacketDistributor`、`NetworkEvent.Context` 和 packet wrapper 不应向上泄漏。

### 5.2 PolicyMessage 字段

```text
operation   = SELECT | OVERRIDE | CLEAR_SELECTION | CLEAR_OVERRIDE | CLEAR_ALL_OVERRIDES
requestId   = UUID
policyId    = UUID；CLEAR_SELECTION、CLEAR_ALL_OVERRIDES 不携带
owner       = String
filter      = ResourceLocation，可为空
strength    = float，规范化到 0.0 到 1.0
priority    = int
```

广播和指定玩家不是消息字段，而是发送端口的目标：

```text
ServerFilterApplication
    -> PolicyTransport.broadcast(message)
    -> 所有客户端

ServerFilterApplication
    -> PolicyTransport.send(player, message)
    -> 指定客户端
```

### 5.3 操作映射

| 网络操作 | 客户端接收行为 |
| --- | --- |
| `SELECT` | 提交 `FilterRequest.Select` |
| `OVERRIDE` | 提交 `FilterRequest.Override` |
| `CLEAR_SELECTION` | 提交 `FilterRequest.ClearSelection` |
| `CLEAR_OVERRIDE` | 按 policy ID 找到资源 UUID 并提交 Release |
| `CLEAR_ALL_OVERRIDES` | 释放并清空全部网络覆盖资源 |

客户端网络适配器按策略 ID 记录最近请求及创建结果。同策略同请求不重复创建；同策略新请求释放旧覆盖并创建新覆盖。排队中的旧创建回执只释放自身 UUID；更新或清理不能误释放新覆盖。退出世界时释放网络创建的覆盖并清除会话选择，不影响本地其他模组的覆盖。连接映射重置后，旧连接延迟回执不能修改新连接状态。

### 5.4 登录补发

玩家登录时，服务端应用层向该玩家补发：

1. 当前全局选择；
2. 当前全局覆盖；
3. 该玩家的个人选择；
4. 该玩家的个人覆盖。

补发保留策略 ID，并重新生成请求 ID。广播选择后再次发送玩家个人选择，保持个人选择优先。

网络层不提供服务端状态查询、通用 RPC 或操作回执。`OperationResult` 是服务端本地用例结果，不是客户端确认消息。当前 `PolicyNetwork.broadcast(...)` 和 `send(...)` 在通道未初始化或 Forge 网络发送抛出 `RuntimeException` 时会静默返回，因此 `OperationResult.SUCCESS` 只能证明服务端用例层已接受并保存策略，不能证明网络发送、客户端接收或客户端渲染成功；发送失败的可观测性保留为后续独立决策。

## 6. 依赖方向审查

### 6.1 允许的方向

```text
api/common  <- api/client / api/server
api/client  <- ClientFilterApplication / 客户端命令
api/server  <- ServerFilterApplication / 服务端命令
network/contract <- ServerFilterApplication / PolicyNetwork / 客户端接收适配器
network/transport -> Forge 网络实现
```

平台实现可以依赖公共契约，公共契约不能反向依赖客户端渲染或 Forge 网络实现。

### 6.2 必须阻止的依赖

- `api/common`、`api/server`、服务端应用层不得引用 `net.minecraft.client`；
- 服务端命令不得引用 `FilterManager`、`FilterRegistry` 或渲染类；
- 服务端应用层不得引用 `SimpleChannel`、`PacketDistributor` 或 packet wrapper；
- 客户端 API 不得把 `FilterManager`、`FilterRegistry` 或 `WorldFilterRenderer` 作为公开返回类型；
- 网络消息不得携带客户端渲染对象、GUI 对象或服务端状态表；
- 业务结果不得携带自然语言 detail 来代替稳定反馈代码；
- 命令 Java 代码不得使用 `Component.literal` 生成用户反馈。

## 7. 架构审查清单

### 已满足的检查项

- API 和命令共用服务端应用层；
- 客户端 API 写操作集中在客户端应用层调度；
- 服务端业务通过 `PolicyTransport` 发送策略；
- 网络通道和协议版本固定；
- 客户端网络消息转换回客户端 API；
- API 测试命令不直接修改内部状态；
- 命令反馈使用 i18n 翻译键；
- 网络消息字段和操作集合固定；
- 服务端登录补发和客户端重复消息幂等已有实现路径。

### 已固定决策与后续审查项

第 2、4、5 项已随本次适配固定；其余项目保留为后续独立审查，不扩大本步范围：

1. `PolicyTransport` 已收窄为服务端发送端口；客户端接收注册保留在 `PolicyNetwork` 实现中。后续只需验证是否需要独立的客户端接收接口，不应重新把接收能力放回服务端端口。
2. 已固定 `PolicyTransport` 为频道与协议常量的单一来源；FilterServerApi 与 PolicyNetwork 直接复用，不新增元数据类型。
3. `PolicyNetwork.broadcast(...)` 和 `send(...)` 当前捕获 `RuntimeException` 后静默返回。需要决定失败是否必须写入日志、暴露监控事件或反映到服务端操作结果。
4. 已分离 requestId 与 policyId：前者每次调用唯一，后者标识持续策略，修正客户端将覆盖更新当成重复请求的问题。
5. OperationResult 已移除 parameters，使用固定结果字段和策略 ID 列表；旧业务方法与协议 1 不兼容。
6. 当前网络层没有操作回执。需要确认服务端测试范围是否永远只验证服务端本地 API 结果，还是未来需要增加独立的客户端确认事件；若增加，必须扩展协议，而不能让命令读取客户端状态。

本步不增加双向回执、重试或乱序恢复。编译、单元测试与游戏内双端更新、清理、重连、登录补发应分别记录验证结果。

## 8. 建议的审查结论格式

对每个审查项记录：

```text
编号：ARCH-xxx
范围：API / 指令 / 抽象层 / 网络层
当前行为：源码实际做了什么
风险：对依赖方向、兼容性、可测试性或可观测性的影响
决策：保留 / 拆分 / 延后 / 拒绝
验收：测试、静态依赖检查或游戏内验证方式
```

审查完成前，不应仅凭命令输出判断端到端网络成功。服务端 `OperationResult`、网络发送、客户端接收和客户端最终快照是四个不同的观察点，应分别验证。

## 9. 关联文档和验证入口

- [`docs/API_使用指南.md`](API_使用指南.md)：面向 API 使用者的统一指南。
- [`docs/Java_API_调用说明.md`](Java_API_调用说明.md)：客户端 API 专题说明。
- [`docs/服务端_API_与命令.md`](服务端_API_与命令.md)：服务端 API、管理指令和网络协议说明。
- [`PRISMOD_GUIDE_PROMPT.md`](../PRISMOD_GUIDE_PROMPT.md)：项目边界和开发约定。

建议审查后运行：

```powershell
.\gradlew.bat compileJava --console=plain
.\gradlew.bat test --console=plain
git diff --check
```
