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

## 6. 公共契约与多模组覆盖

客户端与未来服务端共同使用的无客户端依赖模型位于 `com.xkmxz.prismod.api.common`。其中滤镜 ID 使用 `ResourceLocation`，owner 使用字符串；公共模型不包含 `Minecraft`、渲染器、PostChain 或 GPU 对象，因此可以安全用于未来网络和指令适配器。

创建带优先级的临时覆盖：

```java
FilterOverride override = FilterApi.createOverride(
        "example-mod",
        ResourceLocation.fromNamespaceAndPath("example", "debug"),
        0.8F,
        10
);

// 优先级更高的覆盖会生效；同优先级按创建时间较新的请求生效。
override.close(); // 幂等关闭，恢复下一项覆盖或用户选择
```

多个模组可以同时创建覆盖。关闭单个句柄不会影响其他 owner；模组卸载时可调用 `FilterApi.clearOverrides(ownerId)` 批量清理。

监听状态变化：

```java
FilterSubscription subscription = FilterApi.subscribe(snapshot -> {
    ResourceLocation id = snapshot.filter();
    boolean fallback = snapshot.fallbackReason() != FilterFallbackReason.NONE;
});

subscription.close();
```

写操作必须在客户端线程执行；`setForcedFilter` 等兼容方法会自动排队到客户端线程。资源重载或渲染失败时，Prismod 保留用户选择和覆盖意图，暂时回退到 `prismod:original`，并通过快照的 `fallbackReason` 与注册状态暴露原因。

## 7. 获取实时滤镜列表

`FilterApi.getFilters()` 会在调用时读取完整注册表，包含暂不可用条目；`FilterApi.getAvailableFilters()` 只返回当前可用条目。两者均返回不可变的最新列表。资源包重载、API 注册或注销完成后，下一次调用即可看到变化；之前返回的列表不会被原地修改。

```java
List<FilterDescriptor> filters = FilterApi.getFilters();
for (FilterDescriptor filter : filters) {
    if (filter.available()) {
        ResourceLocation id = filter.id();
        float defaultStrength = filter.defaultStrength();
    }
}
```

列表中的 `available()` 反映当前资源校验状态；资源暂时缺失或渲染失败时，条目仍保留并通过 `failureReason()` 与 `failureDetail()` 提供诊断信息。

## 8. 客户端 API 手动测试命令

启动客户端后，在游戏内输入 `/prismod_client api help` 查看测试命令。它们模拟其他模组调用公开客户端 API，不代表服务端命令。

```text
/prismod_client api list
/prismod_client api snapshot
/prismod_client api force prismod:warm 0.75
/prismod_client api clear
/prismod_client api watch
/prismod_client api owner_clear
```

`force` 使用测试 owner `prismod-api-test-mod` 和优先级 `100` 创建覆盖；`clear` 关闭当前句柄，`owner_clear` 演示按 owner 批量清理，`watch` 演示 `FilterApi.subscribe` 的实时状态通知。

## 9. 生命周期、事件与异步清理

逻辑滤镜 ID 在运行时全局唯一。相同 owner 的重复注册会替换旧版本，旧句柄随即显示为 `CLOSED`；不同 owner 或内置/资源包已占用该 ID 时，注册句柄显示为 `FAILED`，失败原因是 `ID_CONFLICT`，不会影响已存在的滤镜。

`FilterApi.subscribeEvents(...)` 提供类型化事件，可区分快照、注册表、可用性与操作完成事件。批量清理请使用 `clearRegistrationsOperation(ownerId)` 或 `clearOverridesOperation(ownerId)`，返回的 `FilterOperation` 会从后台线程的 `QUEUED` 更新为最终状态，并给出实际影响数量。旧的 `int` 清理方法仍可用但已弃用。

`setSessionSelection` 仅设置会话选择，不写入玩家配置，离开世界后自动清除。`selectFilter` 保留为弃用别名。
