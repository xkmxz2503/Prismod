# Prismod

Minecraft **1.20.1 / Forge 47.3.32 / Java 17** 的客户端世界画面滤镜初版。

## 使用

- 安装构建产物 `build/libs/prismod-1.0.jar`，不需要额外渲染前置。
- 进入世界后按 **F8**：原色 → 黑白 → 暖色 → 冷色 → 复古 → 夜视。可在游戏的“控制 → 按键绑定 → Prismod”中改键。
- 在“模组 → Prismod → 配置”中选择当前滤镜、调整各预设的强度和循环顺序。拖动顺序项可以排序；保存后生效，取消/ESC 放弃本次编辑。通过独立的“资源包管理”和“滤镜管理”子页面分别管理资源包开关与滤镜展示状态。
- 客户端配置存入 `config/prismod/config/prismod-client.toml`；每次启动从原色开始。原色或强度 0 跳过后处理。
- Prismod 自定义模组资源包目录为 `config/prismod/resourcepacks/`，支持直接子目录和 `.zip`；资源包根目录必须包含 `prismod.pack.json`，使用统一资源包格式 v1。这是 Prismod 自己的资源包格式，不需要 `pack.mcmeta`，也不使用原版资源包界面管理。旧版 `prismod.meta.json` 与 `assets/<namespace>/shaders/...` 格式不再兼容。
- 模组内置滤镜也使用同一套 v1 清单：完整默认包位于 `src/main/resources/assets/prismod/custom/prismod_default_filters/`，其中包含包内 `prismod.pack.json`、`assets/prismod/filters/<id>/`、`assets/prismod/lang/` 和运行时辅助 shader。它不是散落在模组根资源中的特殊路径。
- `filter.json` 的 `display_name` 必须由资源包自己的 `assets/<namespace>/lang/<语言>.json` 提供翻译；当前语言缺失时回退 `en_us`，仍缺失则显示完整 `namespace:path`。模组外层语言文件只负责 Prismod 自身界面和消息，不再提供默认滤镜名称。
- 首次启动时，Prismod 会把上述完整默认包导出到 `config/prismod/builtin/prismod_default_filters/`，随后像 TACZ 的默认枪包一样按普通目录资源包读取；滤镜和语言文件的已有编辑不会被覆盖，包内 `assets/prismod/runtime/` 运行时处理器则会随模组版本同步更新。
- 在 Prismod 的“资源包管理”页面可以直接将 ZIP 或资源包目录拖入窗口导入；也可以点击“打开资源包文件夹”手动放入上述目录。导入会执行清单、依赖、路径和重复项校验，成功后自动刷新列表，再单独保存启用/禁用设置。保存后由 Prismod 自己重新读取目录/ZIP，不会调用或修改 Minecraft 原版资源包管理。若系统无法自动打开目录，也可以在文件管理器中手动进入该路径。
- 资源包管理页的“创建资源包”向导会生成一个空的 Prismod v1 目录资源包：填写显示名称和 namespace，并选择是否生成根目录 `README.md`。创建过程会检查 namespace 冲突并使用临时目录和原子移动，不生成 `pack.mcmeta`、语言文件或滤镜文件。创建成功后进入资源包编辑页，再通过“新建滤镜”手写模板或导入已有滤镜。资源包目录示例为 `config/prismod/resourcepacks/example/`，其中至少有 `prismod.pack.json`，可选 `README.md`。
- 资源包编辑页的语言编辑入口提供“创建中文模板”和“创建英文模板”按钮。模板会先加入草稿，内容为合法空 JSON 对象；只有返回主编辑页并点击保存后才写入 `assets/<namespace>/lang/zh_cn.json` 或 `en_us.json`。已有语言文件不会被覆盖。
- 资源包列表中的“编辑”按钮会打开常用编辑页，可修改显示名称、namespace 和抽象滤镜信息；README、按语言分类的滤镜显示名称和原始文件编辑分别位于独立子页面。“高级文件编辑”使用文件树，将内容归类为“滤镜”“语言文件”“说明文件”；展开滤镜后可继续按 `program/` 等目录查看具体文件。所有子页面只修改草稿，主页面点击保存后才校验、备份、原子替换并触发 Prismod 私有资源重载。ZIP 编辑会保留原 ZIP，并解压到同名 `.editable` 目录；修改 namespace 会另存为新资源包，不覆盖原包。
- 在资源包编辑页点击“新建 PostChain”或“新建 LUT”会打开滤镜预设向导。向导允许填写滤镜 ID、默认强度，并按资源包语言分别填写显示名称；没有语言文件时会创建当前语言和 `en_us`。可将完整滤镜目录或 `.cube`、`post.json`、program JSON、GLSL 文件拖入向导。向导会自动补齐可运行模板：PostChain 生成完整 pass、program、顶点/片段 shader 和九个调试 uniform，LUT 默认生成有效的 32³ identity `.cube`。导入的 LUT 支持 `LUT_3D_SIZE` 为 1 到 64，并按实际尺寸校验和渲染；0 或超过 64 的尺寸不可渲染。导入内容只覆盖对应模板文件，引用缺失或 JSON、LUT、shader 契约校验失败时不会加入草稿。
- 只处理世界（包括手持物品），HUD、聊天、容器、菜单保持原色。夜视只是画面调色，不赋予药水效果，也不能恢复全黑像素中不存在的细节。
- F8 绑定冲突只提示，不擅自改动其他按键。打开界面时 F8 不切换。
- 在世界内的滤镜配置页，支持统一调试契约的 LUT 和 `post_chain` 行提供“调试”入口，可调整 9 个参数。调试预设按资源包 namespace 和滤镜 ID 独立保存到 `config/prismod/config/resourcepacks/<namespace>/<filter-id>.json`；保存后正常渲染自动生效，普通滤镜强度与调试 `intensity` 相乘。旧 `lut-presets.json` 保留但不再读取。

配置字段为顶层 `enabled`、`cycle_order`、`strength_original`、`strength_grayscale`、`strength_warm`、`strength_cool`、`strength_vintage`、`strength_night_vision`。顺序必须包含六个唯一 ID；非法列表整体回退默认顺序并记录警告。所有强度在 0.0–1.0 内。

## 客户端 API

```java
import com.xkmxz.prismod.api.client.FilterApi;
import com.xkmxz.prismod.client.filter.FilterId;
import com.xkmxz.prismod.client.filter.FilterState;

FilterApi.setActiveFilter(FilterId.VINTAGE, 0.75F);
FilterState current = FilterApi.getEffectiveState();
FilterApi.clearForcedFilter();
```

`setActiveFilter` 设置一个临时强制覆盖，重复调用替换该覆盖；它优先于玩家总开关、F8 和配置。解除后恢复玩家选择以及最新的配置强度。离开世界清除强制覆盖，覆盖从不写入磁盘。

写入若来自其他线程会排入 Minecraft 客户端线程；异步调用后立即读取不保证看到尚未执行的修改。查询返回最近已应用的不可变快照。NaN、正负无穷强度归零，其他值钳制至 `[0,1]`；空 ID 按原色处理。

这些 API **仅可在物理客户端调用**。未来网络接收器需要在客户端分支调用它们；本版没有网络协议、服务器命令或强制策略。其他模组应通过自身的客户端入口引用 `api.client`，不要在专用服务器静态初始化中加载此包。

## 渲染与兼容性

在 `GameRenderer.render(FJZ)V` 完成世界及原版后处理、重新绑定主目标之后注入，早于 HUD。Prismod 自有 PostChain 使用 **一次滤镜绘制（main → swap）+ 一次颜色 blit（swap → main）**，不占用原版旁观者滤镜槽位。强度为零时两步都不执行。

处理成功前保留主画面，资源或 GL 错误后停用本次滤镜并提示一次，F3+T 或重启后重试。resize 重设临时目标；资源重载/退出释放自有 GPU 资源。渲染失败期间 API 回报原色并保留 `forced` 标记。

Oculus 是可选共存模组，不是依赖。本版在当前主画面上尝试叠加，不读取其私有 API。**Oculus 启用 shaderpack 的兼容性必须按具体版本/光影包实测**；成功构建不代表通过此项。OptiFine 与其他重写 GameRenderer 的模组同样不作未经测试的兼容承诺。

## 构建与测试

Windows：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat test -PprismodRenderTests
```

Linux/macOS 使用 `./gradlew build`；可选 OpenGL 测试的本机库配置目前面向 Windows x64。Gradle wrapper 固定 8.8，项目工具链固定 Java 17；首次运行需联网取得工具链及依赖。

普通测试覆盖状态机、线程安全快照和 shader 资源关系。`-PprismodRenderTests` 额外建立隐藏 OpenGL 上下文，真实编译 GLSL、验证像素/alpha 和测量独立滤镜成本；它不等同于 Minecraft 内 Mixin、HUD 或 Oculus 的集成测试。

游戏内性能诊断：

```powershell
.\gradlew.bat runClient -PprismodProfileGpu=true
```

设为 1920×1080，选择一个非零滤镜；每个预设预热 60 帧后异步收集 300 个 GPU 时间戳样本，在 `run/logs/latest.log` 搜索 `Prismod GPU` 查看 P95。计时包含滤镜与颜色复制，不含预设首次编译；切换滤镜或分辨率会重新采样。目标为 P95 ≤ 1.0 ms。记录 GPU/驱动、分辨率、Oculus/光影包版本与开启状态；日志中的 `oculusInstalled` 仅表示安装，不能当作启用 shaderpack 的证明。

## 游戏内回归清单

1. 原色及五种效果分别检查 0、0.5、1 强度；HUD、聊天、背包、暂停菜单和 F1 隐藏 HUD 时显示正确。
2. F8 循环、改键、键位冲突提示；编辑配置后保存/取消/ESC；拖拽/键盘排序、窗口缩放和低 GUI 分辨率。
3. F3+T、全屏切换、尺寸变化、退出/重进世界和重启；损坏 shader 的资源包导致一次提示，修复并重载后恢复。
4. 另一个客户端模组调用 API，验证强制覆盖、解除恢复、非客户端线程调用、断开连接清除覆盖。
5. 无 Oculus、Oculus 安装但关闭光影、Oculus 开启光影分别检查画面并做 1080p 性能采样。
6. 专用服务器启动无客户端类加载错误；客户端连接未安装 Prismod 的服务器。

移除 Prismod jar 即停止功能；保留配置便于以后重装。不会写世界滤镜数据或发送网络包。
