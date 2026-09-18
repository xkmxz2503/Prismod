# Prismod 后续开发引导提示词

你正在维护 Prismod：一个面向 Minecraft 1.20.1、Forge 47.3.32、Java 17 的客户端画面滤镜模组。请先阅读本文件、`README.md` 和相关源码，再进行任何修改。除非用户明确要求，不要改变已经确定的配置格式、公开 API、渲染时序或客户端/服务端边界。

## 当前项目状态

Prismod 初版已经实现并能构建，功能包括：

- 原色、黑白、暖色、冷色、复古、夜视六种滤镜。
- F8 按配置顺序循环，任何时刻最多激活一个滤镜。
- 独立 Forge 配置屏幕：总开关、六个强度滑块、当前滤镜选择、循环顺序拖拽/上下调整、保存/取消/Esc。
- 只处理世界渲染结果（包括手持物品）；HUD、聊天、容器、菜单保持原色。
- 客户端强制滤镜 API，可覆盖按键和用户配置，清除后恢复用户选择。
- 不把 Oculus 设为依赖；在当前主渲染目标上追加一次自有后处理。
- shader、状态机、配置校验和真实 OpenGL 测试已经加入。

构建产物：`build/libs/prismod-1.0.jar`。

## 不可改变的功能契约

客户端配置文件为 `config/prismod/config/prismod-client.toml`，字段固定为：

```toml
enabled = true
cycle_order = ["original", "grayscale", "warm", "cool", "vintage", "night_vision"]
strength_original = 1.0
strength_grayscale = 1.0
strength_warm = 1.0
strength_cool = 1.0
strength_vintage = 1.0
strength_night_vision = 1.0
```

`cycle_order` 必须包含六个唯一滤镜 ID；非法列表整体回退默认顺序并记录警告。强度始终钳制到 `[0.0, 1.0]`。当前选择不跨启动保存，每次客户端会话从 `ORIGINAL` 开始；用户配置本身需要持久化。

自定义滤镜只从 `config/prismod/resourcepacks/` 的直接子目录和 `.zip` 加载。每个资源包根目录必须有 `prismod.meta.json`，格式至少为 `{"namespace":"example"}`；不再读取原版 `resourcepacks/` 或 `pack.mcmeta` 中的 Prismod 字段。

公开客户端 API 的签名和语义如下：

```java
package com.xkmxz.prismod.api.client;

public final class FilterApi {
    public static void setActiveFilter(FilterId id, float strength);
    public static void clearForcedFilter();
    public static FilterState getEffectiveState();
}
```

`setActiveFilter` 设置强制状态并覆盖总开关、F8 和配置；重复调用替换强制状态。`clearForcedFilter` 恢复用户原来的选择。强度自动限制在 `[0, 1]`；空 ID 按原色处理，NaN 和无穷值按零处理。非客户端线程调用写 API 时必须提交到 Minecraft 主线程。API 位于 `api.client`，专用服务器不得加载客户端类。

## 架构和模块职责

### 状态层

- `client/FilterId.java`：六个滤镜 ID、序列化名称、翻译键和默认顺序。
- `client/FilterState.java`：不可变状态快照，包含 `id`、`strength`、`forced`。
- `client/FilterController.java`：纯 Java 状态机，维护用户选择、强制覆盖、总开关、循环顺序、强度和渲染可用状态。
- `client/FilterManager.java`：客户端状态门面，负责配置刷新、会话重置和失败提示。
- `client/PrismodClientConfig.java`：Forge `ModConfig.Type.CLIENT` 配置定义和顺序/强度读写。

优先级为：渲染失败时临时原色 > 强制状态 > 用户总开关与选择。渲染失败会禁用当前滤镜直到资源重载或客户端重启，但保留强制标记以便 API 状态可诊断；离开世界时清除本次会话强制覆盖。

### 客户端入口和界面

- `Prismod.java`：通用模组入口；客户端分支通过 `DistExecutor` 创建 `PrismodClient`。
- `client/PrismodClient.java`：注册 F8、客户端配置屏幕、资源重载监听器和客户端 tick。
- `client/FilterConfigScreen.java`：配置草稿界面；保存后刷新配置，取消和 Esc 放弃草稿。

F8 只在世界内、没有打开屏幕且没有强制状态时响应。按键冲突只提示一次，不修改玩家绑定。

### 渲染层

- `client/WorldFilterRenderer.java`：只在渲染线程运行，管理自有 `PostChain`、临时 framebuffer、resize、资源重载和失败降级。
- `mixin/client/GameRendererMixin.java`：注入 `GameRenderer.render`，位置必须保持在世界/手持物品及原版后处理完成、HUD 绘制开始之前。
- `mixin/client/PostChainAccessor.java`：访问 `PostChain` 的 pass 列表并调用私有资源加载方法。
- `mixin/client/BlendModeAccessor.java`：恢复 `BlendMode.lastApplied`，避免后续 HUD 暗角因静态缓存污染而黑屏。

渲染链固定为：

```text
minecraft:main --(一个滤镜 pass)--> swap --(颜色 blit)--> minecraft:main
```

成功处理前不得清空主目标；后处理失败、shader 编译失败、FBO 错误或新的 OpenGL 错误都必须保留原画面、停用当前滤镜并显示一次提示。必须恢复 blend、depth、cull、depth mask、blend factors、blend equations 和 `BlendMode.lastApplied`，确保 HUD 使用原始颜色。原色或强度为零时不创建/执行滤镜 pass。

每个滤镜只允许一个 pass，资源重载和窗口尺寸变化必须重建或 resize 临时目标。不要调用 Oculus 私有类或内部 API；Oculus 只能作为可选共存模组处理。

### Shader 资源

资源位于 `src/main/resources/assets/prismod/shaders/`：

- `post/{grayscale,warm,cool,vintage,night_vision}.json`
- `program/{grayscale,warm,cool,vintage,night_vision}.json`
- 对应 `.fsh` 以及公共 `fullscreen.vsh`

每个 fragment shader 接受 `DiffuseSampler`、`Intensity` 和 `ScreenSize`。黑白使用 Rec.709 灰度；暖色提升红黄通道；冷色提升蓝青通道；复古降低对比度并褪色；夜视采用绿色偏移、暗部抬升和整体提亮。原色不需要 shader 资源。

## 测试和验证证据

已有测试位于 `src/test/java/com/xkmxz/prismod/client/`：

- `FilterControllerTest`、`FilterOrderTest`、`FilterStateTest`：状态优先级、循环、非法顺序、强度裁剪和强制状态。
- `ShaderResourceTest`：shader JSON、uniform 和资源关系。
- `FilterShaderGlTest`：隐藏 OpenGL 上下文中真实编译 GLSL、像素/alpha 验证及性能采样。

此前完整命令已通过 40 项测试：

```powershell
.\gradlew.bat build -PprismodRenderTests --offline --console=plain "-Dorg.gradle.jvmargs=-Xmx3G -Dfile.encoding=COMPAT"
```

游戏内已验证：F8 循环五种实际滤镜和原色、配置保存、HUD 原色隔离、F3+T 重载、窗口/全屏尺寸变化、损坏 shader 后回退及修复后恢复。RTX 4050 Laptop、1920x1080、夜视滤镜预热后 300 帧采样为 P95 `0.185344 ms`，满足 1 ms 目标。独立 OpenGL 测试中的滤镜+blit P95 约 `0.0625-0.0635 ms`。

## 尚未完成的验证

不要把以下事项描述为已通过：

- Oculus 安装并启用 shaderpack 的实际兼容性和性能。
- 专用服务器真实启动及服务器连接回归。
- 所有滤镜在完整 Minecraft 场景下分别完成 1080p P95 采样。

当前环境偶尔会因 ForgeGradle 证书探测导致重新构建失败；这属于依赖解析环境问题，不应通过修改源代码绕过。优先使用项目已有的 Gradle 缓存和 Java 17 工具链，并在报告中区分“代码测试通过”和“环境无法重跑”。

## 后续修改规则

1. 先阅读实际映射和现有调用链，再修改 Mixin descriptor 或渲染时机。
2. 不把 Oculus、OptiFine 或其他渲染前置变成 Prismod 发布包的硬依赖。当前 `build.gradle` 中的 Oculus 坐标仅用于开发环境共存测试，发布 jar 不打包它；如调整依赖，必须保留 Curse Maven 仓库和可选共存语义。
3. 不把 HUD、聊天、菜单或容器绘制放进滤镜链。
4. 不在专用服务器路径引用 `net.minecraft.client`、`Minecraft` 或任何 `client` 包。
5. 修改渲染状态时必须补充失败回退和状态恢复测试；修改配置/API 时必须保留线程调度和不可变快照语义。
6. 使用 `apply_patch` 编辑，保留用户已有改动，不删除 `.vscode/` 等 IDE 文件。
7. 完成修改后至少运行覆盖变更的测试，并报告命令、结果、未验证项和产物路径。
