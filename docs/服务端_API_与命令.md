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

## 服务端 API

```java
import com.xkmxz.prismod.api.common.request.FilterOverrideRequest;
import com.xkmxz.prismod.api.common.request.FilterSelectionRequest;
import com.xkmxz.prismod.api.server.FilterServerApi;
import net.minecraft.resources.ResourceLocation;

ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "original");
var selection = FilterServerApi.broadcastSelection(
        new FilterSelectionRequest(id, 1.0F));
var override = FilterServerApi.broadcastOverride(
        new FilterOverrideRequest("server-rule", id, 1.0F, 100));
var cleared = FilterServerApi.clearBroadcastOverride("server-rule");
```

可用操作还包括指定玩家选择/覆盖、清理玩家覆盖、清理全部覆盖和清理选择。返回值为不可变 `OperationResult`，包含状态、稳定反馈代码、请求 ID、目标、影响数量和 generation，不包含自然语言详情。

```java
var status = FilterServerApi.status();
status.initialized();
status.networkChannel();
status.protocolVersion();
```

服务端 API 与命令调用同一个应用层，因此不会出现两套业务规则。

## 冻结网络协议

网络通道固定为 `prismod:policy`，协议版本固定为 `1`。旧通道、旧协议和旧兼容协商不再接受。

消息操作固定为：

- `SELECT`
- `OVERRIDE`
- `CLEAR_SELECTION`
- `CLEAR_OVERRIDE`
- `CLEAR_ALL_OVERRIDES`

消息字段固定为 request ID、owner、filter、strength、priority。广播和指定玩家是传输目标，不写入业务消息。`SimpleChannel`、`PacketDistributor` 和编解码器只存在于网络实现内部；服务端业务只能使用 `PolicyTransport` 端口。

网络首版只负责策略下发和登录补发，不提供状态查询、通用 RPC 或操作回执。客户端收到策略后只调用 `FilterClientApi`，重复 request ID 的覆盖消息必须幂等处理。
