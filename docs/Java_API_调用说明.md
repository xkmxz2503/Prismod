# Prismod Java API 调用说明

Prismod API 位于 `com.xkmxz.prismod.api.client`，只应在客户端代码中调用。

## 1. 注册自定义滤镜

```java
import com.xkmxz.prismod.api.client.CustomFilterMetadata;
import com.xkmxz.prismod.api.client.FilterApi;
import com.xkmxz.prismod.api.client.FilterRegistration;
import net.minecraft.resources.ResourceLocation;

private FilterRegistration debugRegistration;

public void registerDebugFilter() {
    debugRegistration = FilterApi.registerCustomFilter(
            "example-debug-mod",
            ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
            new CustomFilterMetadata("filter.example.debug", 0.75F)
    );
}
```

参数说明：

- `ownerId`：调用方的稳定标识。不能为空；同一个 owner 和资源 ID 重复注册会更新原条目。
- `postEffect`：已有的后处理 JSON 资源。API 不接收运行时 GLSL 字符串。
- `metadata.translationKey`：可选翻译键。为空或没有翻译时显示完整资源 ID。
- `metadata.defaultStrength`：默认强度，会规范化到 `0.0` 到 `1.0`。

资源必须满足 Prismod 自定义模组资源包契约，否则会被登记为不可用；这不要求 Minecraft 原版 `pack.mcmeta`。

## 2. 注销滤镜

注册返回的句柄对应一次具体注册版本：

```java
if (debugRegistration != null) {
    debugRegistration.close();
    debugRegistration = null;
}
```

句柄实现了 `AutoCloseable`，也可以使用：

```java
try (FilterRegistration registration = FilterApi.registerCustomFilter(
        "example-debug-mod",
        ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
        CustomFilterMetadata.defaults())) {
    // 注册期间使用滤镜
}
```

旧句柄不会删除同一 `ownerId + resourceId` 后续注册的新版本。owner 注销后，滤镜会从可用列表和循环列表中移除。

## 3. 强制使用滤镜

保留旧版内置滤镜 API：

```java
import com.xkmxz.prismod.client.filter.FilterId;

FilterApi.setActiveFilter(FilterId.WARM, 0.8F);
```

也可以通过后处理资源 ID 强制使用自定义滤镜：

```java
FilterApi.setActiveFilter(
        ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
        0.8F
);
```

强制滤镜优先于普通选择和 F8 循环。强度会限制在 `0.0` 到 `1.0`；`NaN` 和无穷值按 `0.0` 处理。

使用完毕后必须清除强制覆盖：

```java
FilterApi.clearForcedFilter();
```

## 4. 查询当前状态

```java
import com.xkmxz.prismod.client.filter.FilterState;

FilterState state = FilterApi.getEffectiveState();
FilterId id = state.id();
float strength = state.strength();
boolean forced = state.forced();
```

`getEffectiveState()` 保持旧版兼容，只能表达内置 `FilterId`。自定义滤镜通过旧状态查询时会回退为 `FilterId.ORIGINAL`；自定义滤镜的内部动态状态由 Prismod 客户端控制器使用。

## 5. 线程要求

`setActiveFilter` 和 `clearForcedFilter` 会自动切换到 Minecraft 客户端线程，可以从其他线程调用。

`registerCustomFilter` 访问客户端资源管理器，建议在客户端初始化、资源加载完成后或客户端线程中调用。不要在服务端类加载或服务端逻辑中引用这些 API。

## 6. 资源重载行为

资源重载时 Prismod 会：

1. 重新扫描所有资源包中的 `assets/*/shaders/post/*.json`。
2. 重新校验 API 注册的资源。
3. 释放旧的 post chain。
4. 恢复当前仍然有效的滤镜。

如果资源暂时不存在，注册记录和配置中的 ID 会保留；资源恢复并再次重载后会自动重新可用。

## 7. 建议的生命周期

推荐在客户端模组生命周期中保存句柄，并在模组卸载或功能关闭时关闭句柄：

```java
public final class DebugFilterClient {
    private FilterRegistration registration;

    public void enable() {
        if (registration == null) {
            registration = FilterApi.registerCustomFilter(
                    "example-debug-mod",
                    ResourceLocation.fromNamespaceAndPath("example", "shaders/post/debug.json"),
                    new CustomFilterMetadata("filter.example.debug", 0.75F));
        }
    }

    public void disable() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
        FilterApi.clearForcedFilter();
    }
}
```

不要直接操作 `FilterRegistry`、`FilterController` 或 `WorldFilterRenderer`；这些属于 Prismod 内部实现，公开兼容入口是 `FilterApi`、`CustomFilterMetadata` 和 `FilterRegistration`。
