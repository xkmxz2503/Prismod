# Prismod Java API 调用说明

客户端公开 API 位于 `com.xkmxz.prismod.api.client`。专用服务器不得加载 `api.client` 或任何 `net.minecraft.client` 类。

## 注册自定义滤镜

```java
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.contract.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.contract.FilterRegistration;
import net.minecraft.resources.ResourceLocation;

FilterRegistration registration = FilterClientApi.registerCustomFilter(
        "example-mod",
        ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
        new CustomFilterMetadata("filter.example.debug", 0.75F));

// 模组卸载或功能关闭时注销。
registration.close();
```

`ownerId` 不能为空；`postEffect` 是实际的 PostChain JSON 路径，不是逻辑滤镜 ID。重复注册同一 owner 和逻辑 ID 时，新注册会替换旧注册。强度会规范化到 `[0.0, 1.0]`，非有限值按 `0.0` 处理。

## 选择、强制和覆盖

```java
ResourceLocation filter = ResourceLocation.fromNamespaceAndPath("prismod", "vintage");

FilterClientApi.setForcedFilter(filter, 0.75F);
FilterClientApi.clearForcedFilter();

FilterClientApi.setSessionSelection(filter, 0.5F);
FilterClientApi.clearSessionSelection();

var override = FilterClientApi.createOverride("example-mod", filter, 0.8F, 100);
override.close();
FilterClientApi.clearOverrides("example-mod");
```

写操作会自动调度到 Minecraft 客户端线程。会话选择和覆盖不会写入玩家配置；离开世界时会话状态会清理。覆盖按优先级决定生效项，同一优先级使用较新的覆盖。

## 查询快照和滤镜列表

```java
import com.xkmxz.prismod.api.client.FilterClientApi;
import com.xkmxz.prismod.api.client.contract.FilterSnapshot;

FilterSnapshot snapshot = FilterClientApi.snapshot();
ResourceLocation id = snapshot.filter();
float strength = snapshot.strength();
boolean forced = snapshot.forced();
long generation = snapshot.generation();

var all = FilterClientApi.filters();
var available = FilterClientApi.availableFilters();
```

所有返回列表和快照都是不可变视图。`snapshot()` 返回当前最终渲染快照；渲染不可用时可能暂时回退到 `prismod:original`，但会保留选择信息、回退原因和 generation。

## 操作结果和监听

批量注销返回 `FilterOperation`，只包含结构化状态，不包含自然语言详情：

```java
var operation = FilterClientApi.clearRegistrations("example-mod");
operation.status();
operation.code();
operation.affectedCount();
operation.completed();
```

状态监听不会暴露内部实现：

```java
var stateSubscription = FilterClientApi.subscribe(state -> {
    // 使用 state.filter()、state.strength() 和 state.generation()
});
var eventSubscription = FilterClientApi.subscribeEvents(event -> {
    // 使用 event.type() 和 event.operation()
});

stateSubscription.close();
eventSubscription.close();
```

不要直接操作 `FilterManager`、`FilterRegistry`、`FilterController`、`FilterKey` 或渲染器；这些属于 Prismod 内部适配器。
