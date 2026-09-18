# Prismod

Minecraft **1.20.1 / Forge 47.3.32 / Java 17** 的客户端世界画面滤镜初版。

## 使用

- 安装构建产物 `build/libs/prismod-1.0.jar`，不需要额外渲染前置。
- 进入世界后按 **F8**：原色 → 黑白 → 暖色 → 冷色 → 复古 → 夜视。可在游戏的“控制 → 按键绑定 → Prismod”中改键。
- 在“模组 → Prismod → 配置”中选择当前滤镜、调整各预设的强度和循环顺序。拖动顺序项可以排序；保存后生效，取消/ESC 放弃本次编辑。
- 客户端配置存入 `config/prismod/config/prismod-client.toml`；每次启动从原色开始。原色或强度 0 跳过后处理。
- 自定义滤镜目录为 `config/prismod/resourcepacks/`，支持直接子目录和 `.zip`；资源包根目录必须包含 `prismod.meta.json`。
- 只处理世界（包括手持物品），HUD、聊天、容器、菜单保持原色。夜视只是画面调色，不赋予药水效果，也不能恢复全黑像素中不存在的细节。
- F8 绑定冲突只提示，不擅自改动其他按键。打开界面时 F8 不切换。

配置字段为顶层 `enabled`、`cycle_order`、`strength_original`、`strength_grayscale`、`strength_warm`、`strength_cool`、`strength_vintage`、`strength_night_vision`。顺序必须包含六个唯一 ID；非法列表整体回退默认顺序并记录警告。所有强度在 0.0–1.0 内。

## 客户端 API

```java
import com.xkmxz.prismod.api.client.FilterApi;
import com.xkmxz.prismod.client.FilterId;
import com.xkmxz.prismod.client.FilterState;

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
