# Prismod 服务端 API 与命令

Prismod 现在包含一个可选的服务端基础框架。服务端框架不控制滤镜，也不加载 `net.minecraft.client` 或 `com.xkmxz.prismod.api.client`。

## 命令

服务端注册以下公开命令：

```text
/prismod help
/prismod status
```

直接执行 `/prismod` 等同于 `/prismod help`。`status` 会显示框架初始化状态、网络协议版本和通道 ID。首期命令没有修改世界或玩家状态的操作；未来管理类命令预留 OP 权限等级 2。

## 服务端 API

服务端 API 位于 `com.xkmxz.prismod.api.server`，供 Prismod 后续服务端功能使用：

```java
import com.xkmxz.prismod.api.server.ServerApi;
import com.xkmxz.prismod.api.server.ServerStatus;

ServerStatus status = ServerApi.status();
boolean initialized = status.initialized();
```

`ServerStatus` 是不可变快照，包含 `initialized`、`networkChannel` 和 `protocolVersion`。首期 API 是 Prismod 内部扩展接口，不承诺外部模组兼容性。

## 网络兼容

Prismod 使用 `prismod:main` 通道，协议版本为 `1`。双方都安装 Prismod 时必须匹配协议版本；缺少 Prismod 的原版客户端或服务端仍允许连接。首期只注册一个无字段扩展消息，不会在玩家加入或登录时自动发送。
