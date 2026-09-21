# Prismod 后续开发引导提示词

你正在维护 Prismod：一个面向 Minecraft 1.20.1、Forge 47.3.32、Java 17 的客户端世界画面滤镜模组。开始修改前先阅读本文件、`README.md`、`docs/自定义滤镜资源包制作说明.md`、`docs/Java_API_调用说明.md` 和相关源码。除非用户明确要求，不要改变已经确定的配置格式、公开 API、渲染时序或客户端/服务端边界。

## 当前项目状态

项目已实现并能构建：

- 内置滤镜：原色、黑白、暖色、冷色、复古、夜视。
- F8 按配置顺序循环；自定义滤镜使用 `namespace:path` 的 `FilterKey` 参与循环。
- F8 提示使用当前 `FilterDefinition.displayName()`：优先显示翻译名称，没有翻译时显示真实自定义 ID，不得通过旧版 `FilterState.id()` 把自定义滤镜显示成原色。
- 一级滤镜配置页：总开关、当前选择、滤镜强度、循环顺序、保存/取消/Esc。
- 二级资源包管理页：支持将目录或 ZIP 直接拖入页面导入，也可打开 Prismod 专用资源包目录手动放置；导入后刷新并显示资源包、启用/禁用资源包、滚动列表、独立保存/取消。顶部“创建资源包”向导只生成空的 Prismod v1 目录包，可填写显示名称、namespace 和是否生成根目录 README；滤镜不在创建阶段生成，创建成功后在编辑页手写或导入。语言编辑页可按需创建 `zh_cn` 或 `en_us` 空模板，模板先进入共享草稿，主编辑页保存后才落盘且不会覆盖已有文件。每个已有条目提供全屏编辑入口，可修改资源包元数据、滤镜清单和受支持文本资源。编辑页的新建 PostChain/LUT 按钮进入滤镜预设向导，支持填写 ID、默认强度、各语言名称，拖入完整滤镜目录或单个源文件，并在加入草稿前生成和校验可运行模板。
- 独立滤镜管理页：控制单个滤镜是否展示、滚动列表、独立保存/取消。
- 当前实际生效滤镜来自某个资源包时，该资源包暂时不能禁用；被禁用资源包中的滤镜不会出现在管理页，也不能单独切换展示状态。
- 隐藏滤镜不会出现在一级配置页或 F8 循环；取消隐藏后恢复其原有循环顺序。
- 资源包加载失败、shader 编译失败或 OpenGL 状态异常时回退原色并提示；资源重载后允许重新尝试。
- 调试预览每帧最多执行一次独立处理；渲染重入、调试界面被替换、世界切换或资源重载时会立即停止并释放 PostChain、LUT 纹理和临时 framebuffer。调试上传或处理失败后保留诊断信息但停止逐帧重试，必须重新打开调试页才会再次创建 GPU 资源。
- 自定义滤镜通过客户端 API 注册时仍支持强制覆盖和动态注销。

构建产物默认位于 `build/libs/prismod-1.0.jar`。当前工作区可能存在未提交的功能修改，修改时必须保留用户已有改动。

## 不可改变的功能契约

客户端配置文件为 `config/prismod/config/prismod-client.toml`。顶层字段包括：

```toml
enabled = true
cycle_order = ["original", "grayscale", "warm", "cool", "vintage", "night_vision"]
custom_strengths = []
disabled_packs = []
hidden_filters = []
strength_original = 1.0
strength_grayscale = 1.0
strength_warm = 1.0
strength_cool = 1.0
strength_vintage = 1.0
strength_night_vision = 1.0
```

`cycle_order` 使用 `FilterKey` 序列化值：内置滤镜继续接受短名称，自定义滤镜使用 `namespace:path`。解析时忽略非法项和重复项；如果解析后为空则回退内置默认顺序。资源重载完成时会补充已发现滤镜，并清理 `cycle_order`、`custom_strengths`、`hidden_filters` 中已经不再被注册表或现存资源包清单提供的滤镜；配置页面点击保存时也会执行一次同样的清理，不使用循环任务。暂时禁用的资源包仍在清单检查范围内，调试预设也不参与清理。

`custom_strengths` 使用 `namespace:path=value` 字符串保存自定义滤镜强度；全部强度都会规范化到 `[0.0, 1.0]`，NaN 和无穷值按 `0.0` 处理。`disabled_packs` 保存资源包 namespace，`hidden_filters` 保存滤镜 ID。

资源包扫描发生在 Forge 客户端配置加载之前。此阶段必须默认允许资源包进入首次扫描，不能直接读取尚未加载的 `ForgeConfigSpec.ConfigValue`；配置加载完成后由客户端 tick 触发一次资源重载，再应用 `disabled_packs`。不要删除这一启动兼容逻辑。

自定义资源包是 Prismod 自己的模组资源包格式，不是 Minecraft 原版资源包：

- 只扫描 `config/prismod/resourcepacks/` 的直接子目录和 `.zip` 文件。管理页支持将资源包直接拖入页面导入，也可以使用“打开资源包文件夹”按钮通过 Minecraft `Util.getPlatform().openFile(...)` 打开该目录；用户手动放入资源包后重新打开管理页即可刷新列表，目录无法自动打开时允许手动放置。
- 根目录必须有 `prismod.pack.json`，且 `schema` 为 `prismod.resource_pack`、`format_version` 为 `1`。
- 清单中的 `filters` 是滤镜唯一来源；每个条目必须声明合法 `id` 和 `assets/<namespace>/filters/` 下的目录，并包含 `filter.json`。
- 资源包支持 `post_chain` 和 Adobe `lut3d`；未知滤镜类型只跳过对应条目。旧版 `prismod.meta.json` 和 `assets/<namespace>/shaders/...` 格式不兼容。
- `src/main/resources/assets/prismod/custom/prismod_default_filters/` 是随模组发布的内置 v1 默认包，目录内自包含 `prismod.pack.json`、`assets/prismod/filters/<id>/` 和 `assets/prismod/lang/`；其中 `assets/prismod/runtime/` 只存空链和 LUT 类型处理器的运行时辅助资源，不参与滤镜扫描。滤镜名称只从所属资源包的 `assets/<namespace>/lang/<语言>.json` 解析，模组外层 `assets/prismod/lang/` 只属于模组自身界面、按键和消息。
- 内置包采用与 TACZ 默认枪包相同的导出方式：首次客户端资源重载时把上述完整目录导出到 `config/prismod/builtin/prismod_default_filters/`，之后作为普通目录资源包由 Prismod 私有资源管理器读取；`filters/` 和 `lang/` 中已有文件不覆盖用户编辑，但 `assets/prismod/runtime/` 运行时处理器会随模组版本同步更新，避免旧 shader 清单遮蔽实现。它仍由包内的 `prismod.pack.json` 驱动，不调用 Minecraft 原版资源包管理器。
- 可选 `name` 只作为资源包在管理界面的显示名称。
- 可选 `dependencies` 声明 Forge 模组版本范围。
- `post_chain` 的 PostChain、program JSON 和 GLSL 位于当前滤镜目录；`lut3d` 的 `source` 指向同目录 `.cube` 文件。`.cube` 的 `LUT_3D_SIZE` 支持 1 到 64，数据点数量必须与尺寸的三次方一致，颜色空间为 sRGB；默认模板仍生成 32³ identity LUT。0 或超过 64 的尺寸没有可渲染数据而不可用。支持调试的滤镜必须在 pass、program 和 GLSL 中声明 `Intensity`、`Exposure`、`Contrast`、`Highlights`、`Shadows`、`Saturation`、`Temperature`、`Tint`、`Gamma` 九个 float uniform。
- 不需要 `pack.mcmeta`，不读取原版 `resourcepacks/`，也不使用 Minecraft 原版资源包界面管理。
- 同一 namespace 只接受按文件名升序扫描到的第一个有效资源包；导入时拒绝重复 namespace 和同名目标，不覆盖已有文件。

自定义滤镜名称通过资源包语言文件提供，例如 `assets/example/lang/zh_cn.json` 中为 `filter.example.debug` 提供翻译。资源包重载时 Prismod 将当前语言和 `en_us` 加载到私有显示翻译表，不注册到 Minecraft 全局 `LanguageManager`。当前语言缺失时回退 `en_us`；没有翻译时界面和 F8 提示必须显示 `example:debug`，不得回退为模组语言文件或内置滤镜名称。

滤镜调试界面在世界内从支持统一 uniform 契约的 LUT 或 `post_chain` 滤镜行进入，不改变 `FilterManager` 普通选择或 `custom_strengths`。它提供强度、曝光、对比度、高光、阴影、饱和度、色温、色调和伽马参数；处理顺序为预处理、滤镜处理、后处理和强度混合。调试预设独立保存于 `config/prismod/config/resourcepacks/<namespace>/<filter-id>.json`，文件包含 `schema: prismod.filter_debug`、`format_version: 1`、完整 `filter` 身份和 `settings`。保存按钮才写文件，取消/关闭/窗口重建不保存临时修改；未知版本、身份不匹配或损坏文件回退默认值。缺少任意 uniform 的普通 post_chain 仍可正常使用，但不显示调试入口并记录资源校验错误。旧 `lut-presets.json` 不读取，资源包暂时缺失时调参文件保留。

公开客户端 API 位于 `com.xkmxz.prismod.api.client`，外部模组不得依赖 `client.filter` 内部类：

```java
FilterApi.setForcedFilter(ResourceLocation.fromNamespaceAndPath("prismod", "vintage"), 0.75F);
FilterApi.clearForcedFilter();
FilterSnapshot effective = FilterApi.getEffectiveFilter();
FilterSnapshot selected = FilterApi.getSelectedFilter();
FilterRegistration registration = FilterApi.registerCustomFilter(ownerId, postEffect, metadata);
```

`setForcedFilter` 只接受逻辑滤镜 ID：内置滤镜使用 `prismod:<name>`，自定义滤镜使用完整 `namespace:path`；注册 API 的 `postEffect` 才使用实际 PostChain 资源路径。强制滤镜优先于玩家总开关、F8 和普通选择；重复设置会替换原强制状态，清除后恢复用户选择和最新配置。写 API 会调度到 Minecraft 客户端线程，读取返回最近一次已应用的不可变 `FilterSnapshot`。`getEffectiveFilter()` 返回最终渲染状态，`getSelectedFilter()` 保留玩家选择；快照包含逻辑 ID、强度、forced 和 renderAvailable。专用服务器不得加载 `api.client` 或任何 `net.minecraft.client` 类。

## 架构和模块职责

资源包编辑器的完整版本备份位于 `config/prismod/backup/resourcepacks/.prismod-backup/<资源包目录>/`；删除滤镜的文件按时间批次位于 `config/prismod/backup/resourcepacks/.prismod-recycle/<资源包>/<时间戳>/`，两者都不进入 `config/prismod/resourcepacks/` 的资源包本体。资源包管理页的“备份管理”页面分别管理两类备份，不处理旧版资源包内部回收站。

以上备份路径是当前实现的准确信息；下方早期示例中的旧 `.prismod-backup` 和包内 `.prismod-recycle` 路径已废弃。

### 状态与配置

- `client/FilterKey.java`：内置和自定义滤镜的稳定 `ResourceLocation` 身份。
- `client/FilterDefinition.java`：后处理资源、翻译键、默认强度、内置标记和资源包 namespace；`displayName()` 是 UI/F8 的名称来源。
- `client/FilterSelection.java`：内部动态选择，保留自定义 key、强度和 forced 标记。
- `api/client/FilterSnapshot.java`：公开的不可变客户端状态快照；只使用 API 包和 Minecraft 基础值类型。
- `client/FilterStrength.java`：内部强度规范化工具。
- `client/FilterController.java`：纯 Java 状态机，维护选择、强制覆盖、总开关、循环、强度、可见性和渲染可用状态。
- `client/FilterManager.java`：状态门面、配置刷新、资源失败处理和当前滤镜名称解析。
- `client/PrismodClientConfig.java`：Forge 客户端配置、资源包禁用列表、隐藏滤镜列表、顺序和强度读写。

有效状态优先级为：渲染不可用时临时原色 > 有效强制状态 > 用户总开关与选择。强制或普通选择指向隐藏/不可用滤镜时，实际渲染回退原色，但保留用户选择用于恢复。离开世界清除强制覆盖，不丢失用户选择。

### 客户端入口和界面

- `Prismod.java`：通用入口；客户端分支通过 `DistExecutor` 创建客户端入口。
- `client/PrismodClient.java`：注册 F8、客户端配置、资源包发现、资源重载监听和客户端 tick。
- `client/FilterConfigScreen.java`：一级滤镜配置页。
- `client/ResourcePackManagerScreen.java`：独立资源包管理页，负责拖放或手动导入资源包、打开资源包目录、刷新资源包和资源包开关。
- `client/ResourcePackEditorScreen.java`：资源包常用编辑页，负责名称、namespace、抽象滤镜列表和草稿保存；README、滤镜结构、语言名称和原始文件分别进入独立子页面。高级文件编辑页使用三层文件树，按滤镜目录、语言文件和说明文件归类，展开到具体文件后编辑原始文本。`client/pack/ResourcePackEditorDraft.java` 在这些页面之间共享未保存内容，`client/pack/ResourcePackEditorService.java` 负责 ZIP `.editable` 工作区、白名单、校验、备份和原子保存。
- `client/FilterVisibilityManagerScreen.java`：独立滤镜管理页，只负责滤镜展示状态。

F8 只在世界内、没有打开屏幕且没有强制状态时响应。按键冲突只提示，不修改玩家绑定。资源包管理页保存后写入配置并触发 Prismod 自己的资源刷新，取消和 Esc 放弃草稿；不得调用 Minecraft 原版资源包仓库重载。

资源包编辑器保存行为：目录包直接在临时目录校验后原子替换，并把旧版本保留到 `config/prismod/resourcepacks/.prismod-backup/<资源包目录>/`；ZIP 包编辑时生成同名 `.editable` 目录并保留 ZIP；namespace 变更另存为新资源包，目标 namespace 冲突时阻止保存。资源包管理页的“一键清除备份”只删除统一备份目录和旧版同级 `.prismod-backup` 目录，不删除正常资源包。编辑扫描允许清单声明缺失滤镜文件的包进入编辑器；主编辑页的“清理无效声明”只修改草稿，保存后才落盘并触发重载。删除滤镜会同步移除清单声明、滤镜目录文件和资源包语言文件中的显示名称键；目录内容会写入包内 `.prismod-recycle/`，取消、关闭或窗口重建不会写入草稿。支持文件范围为 `prismod.pack.json`、滤镜 `filter.json`、PostChain/program JSON、GLSL、`.cube` 和资源包语言 JSON。新建向导生成的 PostChain 必须包含完整 pass 和九个调试 uniform；缺失显式 shader 引用、非法 JSON 或 LUT 数据点不匹配时阻止加入草稿。

### 资源包和注册表

- `client/PrismodPackLoader.java`：扫描、解析、校验、导入并为每个 namespace 创建 Prismod 私有资源管理器；目录和 ZIP 由 Prismod 直接读取，不注册到 Minecraft 原版资源包仓库。
- `client/FilterRegistry.java`：从 Prismod 资源包发现后处理滤镜，验证 post JSON、program JSON 和统一 9 个 float uniform；记录资源包 namespace，并标记是否支持调试。
- `client/PrismodClientConfig.java`：资源重载后补充新滤镜并清理禁用资源包中的滤镜。

内部可以使用 Forge 的 `RepositorySource`/`PackResources` 接入 Minecraft 资源管理，但这只是加载实现细节，不得把自定义资源包文档写成原版资源包格式。

### 渲染层

- `client/WorldFilterRenderer.java`：只在渲染线程运行，管理自有 `PostChain`、临时 framebuffer、resize、资源重载和失败降级；调试会话与调试页面绑定，页面消失、世界切换、资源重载或 native/GL 上传失败后立即失效并释放 GPU 资源，禁止同一帧重入或失败后每帧重复创建链。
- `mixin/client/GameRendererMixin.java`：注入 `GameRenderer.render`，位置保持在世界和手持物品及原版后处理完成、HUD 开始之前。
- `mixin/client/PostChainAccessor.java`：访问 `PostChain` 的 pass 列表和资源加载方法。
- `mixin/client/BlendModeAccessor.java`：恢复 `BlendMode.lastApplied`，避免 HUD 状态污染。

渲染链固定为：

```text
minecraft:main --(一个滤镜 pass)--> swap --(颜色 blit)--> minecraft:main
```

成功处理前不得清空主目标。失败时保留原画面、停用当前滤镜并提示一次；必须恢复 blend、depth、cull、depth mask、blend factors、blend equations 和 `BlendMode.lastApplied`。原色或强度为零时不执行后处理。调试预览的 `main -> swap -> main` 只允许一次处理调用；页面不再存在时不得继续提交旧链或旧目标纹理。LUT 上传必须隔离并恢复像素解包状态，失败创建的纹理必须立即删除。不得调用 Oculus 私有 API，Oculus 只能作为可选共存模组。

## 测试和验证

测试位于 `src/test/java/com/xkmxz/prismod/client/`，覆盖：

- `FilterControllerTest`：状态优先级、循环、隐藏滤镜、强制状态、失败回退、线程安全快照。
- `FilterOrderTest`、`FilterKeyTest`、`FilterStateTest`：ID 解析、顺序校验、强度规范化。
- `FilterRegistryTest`、`PrismodPackLoaderTest`：清单来源限制、显示名、ZIP/目录扫描、重复 ID 和 v1 版本校验。
- `ShaderResourceTest`、`FilterShaderGlTest`：shader 资源关系、GLSL 编译、像素/alpha 和 OpenGL 渲染验证。

普通测试命令：

```powershell
.\gradlew.bat --gradle-user-home C:\Users\xkmxz\.gradle --offline test --console=plain
```

OpenGL 测试需显式添加 `-PprismodRenderTests`，并且只代表独立 GL 测试，不等同于完整 Minecraft、HUD、Mixin 或 Oculus 集成测试。修改后必须报告实际运行的命令、结果和没有验证的项目，不得把未运行的游戏内回归写成已通过。

## 后续修改规则

1. 先阅读实际调用链和资源加载顺序，再修改 Mixin descriptor、资源包来源或渲染时机。
2. 保留 `FilterKey`/`FilterSelection` 对自定义滤镜身份的支持；不要在 UI、F8 提示或新逻辑中重新使用旧版 `FilterState.id()` 代替自定义 key。
3. 不把 Oculus、OptiFine 或其他渲染前置变成发布包硬依赖；不要调用它们的私有 API。
4. 不把 HUD、聊天、菜单或容器绘制放进滤镜链。
5. 不在专用服务器路径引用 `net.minecraft.client`、`Minecraft` 或任何 `client` 包。
6. 修改配置/API 时保留线程调度、不可变快照、资源重载和启动阶段配置未加载兼容行为。
7. 使用 `apply_patch` 编辑，保留用户已有改动，不删除 IDE 文件或运行产物目录中的用户数据。
8. 完成修改后至少运行覆盖变更的测试，并报告测试命令、结果、未验证项和产物路径。

## 示例代码和功能文档规则

1. 删除一段当前不用的示范代码是允许的，但不能让示范用法永久丢失。应在本文件保留一份完整、可复制的示例，并明确说明它当前处于停用状态、重新启用需要改哪些位置。
2. 示例必须同时写清用途、启用步骤和具体事例，不能只保留一个无法运行的片段。示例代码中的注册名、事件类型和调用顺序要与当前 Forge/Minecraft 版本一致。
3. 实现重要功能时，除了源码和测试，还必须在本文件补充用户如何使用、配置字段如何填写、调用 API 的方式、资源文件的目录结构或界面操作步骤。可以在功能代码完成后集中补文档，但提交前不得遗漏。
4. 功能行为发生变化时，先同步更新本文件中的契约和示例，再继续后续开发；示例不得描述已经不存在的注册项、命令或配置字段。

### 当前停用的物品示范

`src/main/java/com/xkmxz/prismod/Prismod.java` 中的示例方块、示例物品和创造模式标签注册目前使用块注释保留，没有注册到游戏中。后续确实需要添加物品时，可恢复以下结构：

```java
public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

public static final RegistryObject<Item> EXAMPLE_ITEM = ITEMS.register(
        "example_item", () -> new Item(new Item.Properties()));

public Prismod() {
    IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
    ITEMS.register(modEventBus);
}
```

具体使用步骤：取消对应导入和注册代码的注释，在构造函数中调用 `ITEMS.register(modEventBus)`，再根据需要把 `EXAMPLE_ITEM` 放入创造模式标签；同时补充物品模型、语言键和纹理资源。只有在物品确实成为 Prismod 功能的一部分时才恢复，不能仅为验证模板而注册示例物品。

### 未来命令示范

Prismod 当前没有注册服务器命令。若未来需要增加命令，应保留客户端/服务端边界，并使用 Forge 的命令事件注册，例如：

```java
private void registerCommands(RegisterCommandsEvent event) {
    event.getDispatcher().register(Commands.literal("prismod")
            .then(Commands.literal("reload")
                    .requires(source -> source.hasPermission(2))
                    .executes(context -> {
                        // 这里只调用服务端安全的重载逻辑。
                        return 1;
                    })));
}
```

启用前需要导入 `RegisterCommandsEvent` 和 `Commands`，将监听器注册到正确的 Forge 事件总线，并明确命令只作用于服务端还是需要向客户端发送请求。滤镜渲染、`Minecraft`、`FilterApi` 等客户端类不能直接从专用服务器命令路径加载；命令的实际语法、权限和反馈文本也必须在本文件和用户文档中写出具体例子。

### 重要功能文档示例

新增客户端 API 时，至少应留下类似下面的完整用法，而不是只记录方法签名：

```java
// 在客户端入口调用：让 Prismod 临时使用复古滤镜，强度为 75%。
FilterApi.setForcedFilter(ResourceLocation.fromNamespaceAndPath("prismod", "vintage"), 0.75F);

FilterSnapshot effective = FilterApi.getEffectiveFilter();
FilterSnapshot selected = FilterApi.getSelectedFilter();

// 用完后解除强制状态，恢复玩家在配置界面中的选择。
FilterApi.clearForcedFilter();
```

同时说明调用环境、线程要求、状态优先级、配置是否落盘、失败时的回退行为，以及一个用户可复现的操作路径。例如资源包功能必须写明放置目录、`prismod.pack.json` 最小内容、导入入口、启用/禁用位置和资源重载时机。
