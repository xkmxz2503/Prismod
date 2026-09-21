# Prismod Java API 调用说明

Prismod API 位于 `com.xkmxz.prismod.api.client`，只应在客户端代码中调用。专用服务器不得加载 `api.client` 或任何 `net.minecraft.client` 类。

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
            ResourceLocation.fromNamespaceAndPath("example", "shaders/post/grayscale.json"),
            new CustomFilterMetadata("filter.example.debug", 0.75F)
    );
}
```

参数说明：

- `ownerId` 是调用方的稳定标识，不能为空。同一个 owner 和同一个逻辑滤镜 ID 重复注册会替换旧注册。
- `postEffect` 是实际的 PostChain JSON 资源路径，不是逻辑滤镜 ID。它必须能被 Prismod 校验和加载。
- `metadata.translationKey` 是可选显示名称键；为空或没有翻译时显示完整 `namespace:path`。
- `metadata.defaultStrength` 会规范化到 `0.0` 到 `1.0`；`NaN` 和无穷值按 `0.0` 处理。

注册返回的句柄负责注销：

```java
if (debugRegistration != null) {
    debugRegistration.close();
    debugRegistration = null;
}
```

旧句柄不会删除同一个 owner 后续注册的新版本。注销后，逻辑滤镜会从可用列表和循环列表中移除。

## 2. 强制使用滤镜

强制 API 只接受逻辑滤镜 ID。内置滤镜使用 `prismod:<name>`，自定义滤镜使用资源包声明的完整 `namespace:path`：

```java
import com.xkmxz.prismod.api.client.FilterApi;
import net.minecraft.resources.ResourceLocation;

FilterApi.setForcedFilter(
        ResourceLocation.fromNamespaceAndPath("prismod", "warm"),
        0.8F
);

FilterApi.setForcedFilter(
        ResourceLocation.fromNamespaceAndPath("example", "grayscale"),
        0.8F
);
```

强制滤镜优先于玩家总开关、普通选择和 F8 循环。重复设置会替换当前强制状态，不会叠加。使用完毕后清除：

```java
FilterApi.clearForcedFilter();
```

强度会限制在 `[0.0, 1.0]`；`NaN` 和无穷值按 `0.0` 处理。离开世界时 Prismod 也会清除强制覆盖，但不会丢失玩家选择。

## 3. 查询快照

API 使用独立的不可变 `FilterSnapshot`，外部模组不需要依赖 Prismod 的内部状态类：

```java
import com.xkmxz.prismod.api.client.FilterApi;
import com.xkmxz.prismod.api.client.FilterSnapshot;

FilterSnapshot effective = FilterApi.getEffectiveFilter();
FilterSnapshot selected = FilterApi.getSelectedFilter();

ResourceLocation effectiveId = effective.filter();
float strength = effective.strength();
boolean forced = effective.forced();
boolean renderAvailable = effective.renderAvailable();
```

字段含义：

- `filter` 是完整逻辑滤镜 ID，始终保留 `namespace:path`。
- `strength` 是规范化后的强度。
- `forced` 表示快照是否来自有效强制覆盖。
- `renderAvailable` 表示当前滤镜渲染器是否可用。

`getEffectiveFilter()` 返回最终用于渲染的快照。渲染不可用、总开关关闭或滤镜不可用时，它可能暂时返回 `prismod:original`。`getSelectedFilter()` 返回玩家选择，即使最终渲染暂时回退原色，也保留用户选择的完整 ID。

## 4. 线程与资源重载

`setForcedFilter` 和 `clearForcedFilter` 会自动调度到 Minecraft 客户端线程，可以从其他线程调用。调用后立即读取快照时，不保证已经观察到尚未执行的排队写操作。

`getEffectiveFilter` 和 `getSelectedFilter` 返回最近一次已发布的不可变快照，可安全跨线程读取。

资源重载时 Prismod 会重新扫描 v1 资源包、重新校验 API 注册资源、释放旧 PostChain，并恢复仍然有效的滤镜。资源暂时缺失时，注册句柄和逻辑 ID 会保留，资源恢复并再次重载后自动重新可用。

## 5. 完整生命周期示例

```java
public final class DebugFilterClient {
    private FilterRegistration registration;

    public void enable() {
        if (registration == null) {
            registration = FilterApi.registerCustomFilter(
                    "example-debug-mod",
                    ResourceLocation.fromNamespaceAndPath(
                            "example", "shaders/post/debug.json"),
                    new CustomFilterMetadata("filter.example.debug", 0.75F));
        }
        FilterApi.setForcedFilter(
                ResourceLocation.fromNamespaceAndPath("example", "debug"),
                0.75F);
    }

    public void disable() {
        FilterApi.clearForcedFilter();
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
```

不要直接操作 `FilterRegistry`、`FilterController`、`FilterSelection`、`FilterKey` 或 `WorldFilterRenderer`。这些属于 Prismod 内部实现；稳定公开契约只有 `FilterApi`、`FilterSnapshot`、`CustomFilterMetadata` 和 `FilterRegistration`。
