# Repository Guidelines

## 项目结构

Prismod 是面向 Minecraft 1.20.1、Forge 47.3.32、Java 17 的客户端滤镜模组。主要代码位于 `src/main/java/com/xkmxz/prismod/`：`client/` 包含状态、配置、资源包、UI 和渲染逻辑，`api/client/` 是对外客户端 API，`mixin/client/` 存放渲染访问器与 Mixin。单元测试位于 `src/test/java/com/xkmxz/prismod/client/`；内置 shader 和语言文件位于 `src/main/resources/assets/prismod/`。`docs/` 保存资源包与 API 文档，`config/prismod/resourcepacks/` 是运行时自定义资源包目录。

开始修改前先阅读 `PRISMOD_GUIDE_PROMPT.md`、`README.md` 及相关文档。项目只处理世界画面（包括手持物品），不要把 HUD、聊天、菜单或容器绘制接入滤镜链；服务端路径不得加载 `net.minecraft.client` 或 `api.client`。

## 构建、测试与开发命令

Windows 使用 `gradlew.bat`，Linux/macOS 使用 `./gradlew`：

```powershell
.\gradlew.bat build                         # 编译、测试并生成 build/libs/prismod-1.0.jar
.\gradlew.bat test --console=plain          # 运行 JUnit 5 单元测试
.\gradlew.bat test -PprismodRenderTests     # 额外运行 Windows OpenGL shader 测试
.\gradlew.bat runClient                     # 启动开发客户端
.\gradlew.bat runClient -PprismodProfileGpu=true # 采集 GPU 性能日志
```

## 编码与命名约定

Java 与资源文件使用 UTF-8、4 空格缩进；类和方法遵循 Java 常规的 PascalCase/camelCase，常量使用 `UPPER_SNAKE_CASE`。自定义滤镜必须保留 `namespace:path` 身份，UI/F8 名称使用 `FilterDefinition.displayName()`。资源包必须包含 `prismod.meta.json`，资源放在 `assets/<namespace>/` 下，不要引入 `pack.mcmeta` 或原版资源包管理流程。仓库未配置独立格式化或 lint 任务，提交前至少运行编译、相关测试和 `git diff --check`。

## 测试指南

测试使用 JUnit 5，测试类以 `Test` 结尾，覆盖状态优先级、滤镜顺序、注册表、资源包扫描和 shader 资源。修改渲染或 GLSL 时运行 `-PprismodRenderTests`；它不替代游戏内回归。涉及 F8、GUI、F3+T、窗口缩放、世界重进或 Oculus 兼容性的改动，应在 PR 中记录实际验证步骤、环境和未验证项目。

## 提交与 Pull Request

提交信息保持简短、命令式，并沿用历史中的 `feat:`、`fix:`、`docs:` 等前缀，例如 `fix: restore blend state after filter pass`。每次提交聚焦一个逻辑变更。PR 应说明行为变化和受影响路径，列出实际运行的命令及结果；GUI、渲染或资源包改动附截图/录屏，API、配置格式或资源结构改动同步更新文档，并说明 Oculus 等可选依赖的测试情况。
