# Prismod 服务端 API 与命令

服务端 API 和命令共用服务端应用层。服务端只保存策略状态并通过冻结网络端口下发策略，不加载 `net.minecraft.client` 或 `com.xkmxz.prismod.api.client`。

## 命令

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

`filter` 下的管理命令需要 OP 权限等级 2。所有帮助、状态、成功、失败和参数反馈都使用翻译键；动态值通过翻译参数注入。

## 服务端 API 测试命令

用于手工验证公开 `FilterServerApi` 的测试命令独立于日常管理命令，要求 OP 权限等级 2：

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

测试覆盖固定使用 owner `prismod-server-api-test`。每次变更命令异步显示最终回执的状态、反馈代码、请求 ID、目标、影响数量、策略 ID、失败详情和 generation；`status` 显示服务端协议、通道及策略快照。测试命令只调用 `FilterServerApi`，不直接访问服务端应用层、策略状态或网络实现。

## 服务端 API

入口仍为 `api.server.FilterServerApi`，其余类型先分输入和查询，再按职责细分：

```text
api/server/
├─ FilterServerApi.java
├─ submit/
│  ├─ request/    FilterSelectionRequest、FilterOverrideRequest
│  │             ServerFilterRequest（五类业务请求）
│  ├─ model/      ServerFilterTarget（广播或玩家 UUID）
│  └─ result/     OperationResult
└─ query/
   ├─ request/    ServerFilterQuery（Status、FilterStatus）
   └─ view/       ServerStatus、ServerFilterStatus

api/common/
├─ submit/
│  ├─ result/          FeedbackCode
│  └─ state/operation/ FilterOperationStatus
└─ query/state/filter/ FilterFailureReason

api/client/query/
├─ view/               FilterSnapshot、FilterDescriptor、FilterResourceView
└─ state/              客户端滤镜类型、回退原因和生命周期状态
```

公开业务方法只有 `CompletionStage<OperationResult> submit(ServerFilterRequest)` 和 `<R> R query(ServerFilterQuery<R>)`；频道、协议常量继续保留。旧业务方法已删除，这是不兼容的 API 调整。共享枚举仍供两端复用，不保留旧包桥接类。

```java
import com.xkmxz.prismod.api.server.submit.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.server.submit.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.server.submit.request.ServerFilterRequest;
import com.xkmxz.prismod.api.server.submit.model.ServerFilterTarget;
import com.xkmxz.prismod.api.server.query.request.ServerFilterQuery;
import com.xkmxz.prismod.api.server.FilterServerApi;
import net.minecraft.resources.ResourceLocation;

ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "original");
var target = new ServerFilterTarget.Broadcast();
FilterServerApi.submit(new ServerFilterRequest.Select(
        target, new FilterSelectionRequest(id, 100.0F)));
FilterServerApi.submit(new ServerFilterRequest.Override(
        target, new FilterOverrideRequest("server-rule", id, 100.0F, 100)))
        .thenAccept(receipt -> {
            // 本次最终结果；后续查询重新调用 query。
            boolean accepted = receipt.accepted();
        });
FilterServerApi.submit(new ServerFilterRequest.ClearOverride(target, "server-rule"));
```

指定玩家时目标改为 `new ServerFilterTarget.Player(playerId)`，请求不保存 ServerPlayer。其余请求为 `ClearAllOverrides()` 和 `ClearSelection()`。本步不开放远程注册、Session、批量、订阅、调参或客户端状态查询。

写请求在服务端线程串行执行，先发布状态再完成回执，不要阻塞等待。回执字段为 requestId、status、code、failureReason、failureDetail、target、affectedCount、policyIds、generation；禁止 QUEUED，不含任意参数 Map 或句柄。每次请求生成独立 UUID，失败也不例外。新建或更新策略返回策略 ID，失败和清理的 policyIds 为空。

同范围、同 owner 更新复用策略 ID 并替换参数。重复清理成功且影响数量为零；服务器不可用返回 UNAVAILABLE，指定玩家离线返回 NOT_FOUND 且不保存策略。回执表示服务端已接受并保存策略，不证明网络发送、客户端收到或渲染成功。

```java
var status = FilterServerApi.query(new ServerFilterQuery.Status());
status.initialized();
status.networkChannel();
status.protocolVersion();
var policies = FilterServerApi.query(new ServerFilterQuery.FilterStatus());
```

query 返回最近发布的线程安全不可变状态，无业务副作用。服务端 API 与命令调用同一个应用层，命令语法和 OP 权限保持不变。

## 冻结网络协议

网络通道为 `prismod:policy`，协议升级为 `2`，拒绝 `1`，不提供旧协议兼容分支。常量由 PolicyTransport 单一维护，公开服务端 API 和 Forge 通道都引用同一来源。

消息操作固定为：

- `SELECT`
- `OVERRIDE`
- `CLEAR_SELECTION`
- `CLEAR_OVERRIDE`
- `CLEAR_ALL_OVERRIDES`

消息字段为 operation、requestId、policyId、owner、filter、strength、priority。协议版本为 `3`，strength 使用 `0-100` 百分制。requestId 标识本次请求，policyId 标识持续策略；ClearSelection 和 ClearAllOverrides 对应消息不携带单个策略 ID。广播和指定玩家是传输目标，不写入业务消息。`SimpleChannel`、`PacketDistributor` 和编解码器只存在于网络实现内部；服务端业务只能使用 `PolicyTransport` 端口。

登录补发先发送全局选择，再发送个人选择；随后合并全局和个人覆盖，按原提交顺序发送，使同优先级较新者继续胜出。补发复用策略 ID、重新生成请求 ID。广播选择后重新发送个人选择，保持个人选择优先。

客户端按策略 ID 跟踪最近请求和创建结果：同请求不重复创建，新请求释放旧覆盖并创建新覆盖。排队中的旧创建完成后只释放其自身 UUID；断线重置映射，旧连接回执不能修改新连接映射。清理只释放网络创建的覆盖，不影响其他模组本地资源。

协议仅下行，不提供状态查询、通用 RPC、客户端回执、重试或乱序恢复。游戏内仍需验证双端更新、清理、重连和登录补发；编译及单元测试不能代替这些验证。
