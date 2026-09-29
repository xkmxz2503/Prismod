# Prismod 服务端 API 与命令

Prismod 服务端 API 是滤镜策略和状态源。服务端保存全局或指定玩家的调配状态，通过 `prismod:main` 网络通道发送公共请求；客户端收到后才调用客户端 API 执行渲染。服务端源码不加载 `net.minecraft.client`，也不直接依赖 `com.xkmxz.prismod.api.client`。

## 命令

服务端注册以下公开命令：

```text
/prismod help
/prismod status
/prismod filter select broadcast <id> [strength]
/prismod filter select player <target> <id> [strength]
/prismod filter override broadcast <owner> <id> <priority> [strength]
/prismod filter override player <target> <owner> <id> <priority> [strength]
/prismod filter clear broadcast <owner>
/prismod filter clear player <target> <owner>
```

直接执行 `/prismod` 等同于 `/prismod help`。`status` 会显示框架初始化状态、网络协议版本和通道 ID。`filter` 下的调配命令需要 OP 权限等级 2，`strength` 默认为 `1.0`，范围为 `0.0` 到 `1.0`。

## 服务端 API

服务端 API 位于 `com.xkmxz.prismod.api.server`，供 Prismod 后续服务端功能使用：

```java
import com.xkmxz.prismod.api.server.ServerApi;
import com.xkmxz.prismod.api.server.ServerStatus;

ServerStatus status = ServerApi.status();
boolean initialized = status.initialized();
```

`ServerStatus` 是不可变快照，包含 `initialized`、`networkChannel` 和 `protocolVersion`。首期 API 是 Prismod 内部扩展接口，不承诺外部模组兼容性。

服务端 Java API 可直接提交公共请求。广播请求会发送给当前在线客户端，并保存为后续登录玩家的初始状态；指定玩家请求只影响目标玩家并在其重连后补发。

```java
ResourceLocation id = ResourceLocation.fromNamespaceAndPath("prismod", "original");
ServerOperationResult selection = ServerApi.broadcastSelection(
        new FilterSelectionRequest(id, 1.0F));

ServerOperationResult override = ServerApi.broadcastOverride(
        new FilterOverrideRequest("server-rule", id, 1.0F, 100));
ServerOperationResult cleared = ServerApi.clearBroadcastOverride("server-rule");
```

覆盖请求在客户端以服务端专用 `FilterOverride` 执行，保留用户选择和其他模组覆盖。重复提交同一 owner 会替换服务端记录；清除只关闭 Prismod 创建的覆盖。

## 网络兼容

Prismod 使用 `prismod:main` 通道，协议版本为 `2`。双方都安装 Prismod 时必须匹配协议版本；缺少 Prismod 的原版客户端或服务端仍允许连接。调配消息包含选择、覆盖和清除操作、滤镜 ID、owner、请求 ID、优先级和强度。玩家加入或重连时，服务端会补发当前全局状态和该玩家状态。

客户端资源缺失或渲染失败时，客户端 API 会回退到原色并发布失败/回退状态；服务端请求本身不会因单个客户端资源问题中断。
