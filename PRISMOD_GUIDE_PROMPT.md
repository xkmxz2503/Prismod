# Prismod 后续开发引导提示词

你正在维护 Prismod：一个面向 Minecraft 1.20.1、Forge 47.3.32、Java 17 的客户端世界画面滤镜模组。开始修改前先阅读本文件和 `docs/api/API_架构与文档索引.md`，再按任务读取一个专题文档：`docs/api/Prismod_客户端_API_规范.md`、`docs/api/Prismod_服务端_API_命令与协议.md` 或 `docs/Prismod_资源包格式与编辑指南.md`，并核对相关源码和测试。处理 API 问题时先读 `docs/api/bug/INDEX.md`，仅继续读取与当前任务相关的活动问题文件。仓库根目录 `README.md` 只面向人类读者，除非用户明确要求更新，否则不要读取、引用或修改它；资源包内部的 `README.md` 属于资源包功能内容。文档中的当前行为应以源码和测试为准。

## 当前项目状态

- 服务端在通用入口初始化，提供管理 API、命令和版本化策略下行通道。
- 内置滤镜包括原色、黑白、暖色、冷色、复古和夜视；F8 按配置顺序循环。
- 自定义滤镜通过 Prismod v1 资源包清单发现，并保留完整 `namespace:path` 身份。
- 客户端支持 PostChain、LUT、统一调试参数、资源包管理和编辑器。
- API 与命令的详细契约分别见客户端和服务端专题文档；跨端结构见 API 架构索引。

## 不可改变的功能契约

### 配置与滤镜

- 客户端配置位于 `config/prismod/config/prismod-client.toml`。循环顺序、强度、禁用包和隐藏滤镜继续使用现有字段与百分制单位。
- 内置滤镜允许短名称；自定义滤镜使用完整 `namespace:path`。非法或重复顺序项会忽略，解析结果为空时回退默认顺序。
- strength 在客户端 API、服务端 API、命令和协议中统一使用 `0-100`；非有限值按 `0` 处理。仅客户端渲染边界转换为 shader 底层单位。
- 渲染不可用时回退原色；有效强制状态优先于玩家总开关和普通选择。回退不得清除用户选择。

### 自定义资源包

- Prismod 使用自己的 v1 格式，只扫描 `config/prismod/resourcepacks/` 的直接子目录和 ZIP；不读取原版资源包，不要求 `pack.mcmeta`，不接入原版资源包管理器。
- 资源包根目录必须有 `prismod.pack.json`，声明 schema `prismod.resource_pack`、格式版本 `1` 和滤镜清单。滤镜资源位于所属 namespace 的 `assets/<namespace>/filters/`。
- 支持 `post_chain` 和 Adobe `.cube` `lut3d`。LUT 尺寸为 1 到 64，数据点数必须等于尺寸的三次方；支持调试的滤镜使用固定的九个 float uniform。
- 滤镜名称从资源包自己的 `assets/<namespace>/lang/` 读取；不得注册到 Minecraft 全局语言管理器。详细结构、编辑器行为、备份和示例以[资源包格式与编辑指南](docs/Prismod_资源包格式与编辑指南.md)为准。
- 同一 namespace 只接受按文件名升序扫描到的第一个有效资源包；导入不得覆盖已有文件或重复 namespace。

### API 与网络边界

- 客户端公开入口是 `api.client.FilterClientApi`，服务端公开入口是 `api.server.FilterServerApi`；两者均采用 `submit` 写入、`query` 读取。完整契约、示例和生命周期规则分别见[客户端 API](docs/api/Prismod_客户端_API_规范.md)与[服务端 API、命令与协议](docs/api/Prismod_服务端_API_命令与协议.md)。
- 外部模组不得依赖应用层、dispatcher、状态管理器、注册表、渲染器或网络实现。客户端和服务端 API 数据保持不可变。
- `Application` 负责组合运行依赖并提供用例入口；内部 `Dispatcher` 执行请求调度、业务用例和结果交付。命令适配器经公开 API 调用用例，不直接操作策略状态或传输实现。
- 服务端通道为 `prismod:policy`，现行协议为 `3`；协议 `2` 及更早版本不兼容。消息字段与登录补发行为以服务端 API 文档和源码为准。
- 服务端回执只说明服务端接受并保存策略，不表示发送、客户端接收或渲染完成。协议仅下行；不从网络路径加载 `net.minecraft.client` 或 `api.client`。
- 客户端写请求遵守现有线程调度，不在客户端线程阻塞等待异步结果。客户端状态、资源、回执和事件的详细时序以 API 专题文档为准。

## 架构和模块职责

资源包编辑器的完整版本备份位于 `config/prismod/backup/resourcepacks/.prismod-backup/<资源包目录>/`；删除滤镜的文件按时间批次位于 `config/prismod/backup/resourcepacks/.prismod-recycle/<资源包>/<时间戳>/`，均与资源包本体分离。

- `server/PrismodServer.java`：服务端生命周期、事件注册和适配器组装。
- `server/application/ServerFilterApplication.java`：服务端 API 与命令共用的组合根和用例入口。
- `server/application/ServerFilterDispatcher.java`：内部请求执行核心，维护用例执行流程。
- `network/contract/PolicyTransport.java`：服务端应用可见的冻结传输端口及频道/协议常量。
- `network/transport/PolicyNetwork.java`：封装 Forge 通道和消息传输实现。
- `network/transport/PolicyTransportErrorHandler.java`：统一记录发送异常诊断；不改变策略状态、回执或协议语义。
- `api/server/FilterServerApi.java`：服务端公开 API 门面和查询契约。
- `client/application/ClientFilterApplication.java`、`ClientFilterDispatcher.java`：客户端公开入口后的内部组装与请求执行。
- `client/FilterController.java`、`FilterManager.java`：客户端滤镜状态机、配置刷新及失败回退。
- `client/PrismodPackLoader.java`、`FilterRegistry.java`：资源包扫描、资源加载和滤镜发现校验。
- `client/WorldFilterRenderer.java`：渲染线程上的 PostChain、LUT、临时 framebuffer、resize 和失败降级。
- `mixin/client/GameRendererMixin.java`：在世界和手持物品及原版后处理之后、HUD 开始之前注入滤镜。

资源包编辑器以草稿工作，保存前校验，并通过原子替换保护目录包；ZIP 编辑使用 `.editable` 工作区并保留原 ZIP。namespace 变更另存为新包，冲突时阻止保存。删除滤镜需同步清单、滤镜文件和资源包语言键。

## 渲染约束

滤镜只处理世界画面与手持物品，注入必须位于 HUD 前。固定渲染链为：

```text
minecraft:main --(一个滤镜 pass)--> swap --(颜色 blit)--> minecraft:main
```

成功处理前不得清空主目标。原色或强度为零时跳过后处理；失败时保留原画面并回退原色。必须恢复 blend、depth、cull、depth mask、blend factors、blend equations 和 `BlendMode.lastApplied`。资源重载、页面退出、世界切换或窗口缩放时释放失效的 GPU 资源。不得调用 Oculus 私有 API，也不得把 HUD、聊天、菜单或容器绘制加入滤镜链。

## 测试和验证

测试位于 `src/test/java/com/xkmxz/prismod/`，覆盖状态机、滤镜身份与顺序、线程安全快照、资源包扫描、注册表和 shader 资源关系。普通测试：

```powershell
.\gradlew.bat test --console=plain
```

修改 GLSL 或渲染代码时运行 `-PprismodRenderTests`；该测试不代替游戏内 Mixin、HUD、专用服务器或 Oculus 验证。报告实际运行的命令、结果和未验证项，不把未运行的回归写成已通过。

## 后续修改规则

1. 先阅读实际调用链和资源加载顺序，再修改 Mixin、资源包来源或渲染时机。
2. 保留自定义 `FilterKey` 身份；不要用旧版 `FilterState.id()` 替代完整滤镜 key。
3. 不把 Oculus、OptiFine 或其他渲染前置变成硬依赖，也不调用其私有 API。
4. 专用服务器路径不得引用 `net.minecraft.client`、`Minecraft` 或任何 `client` 包。
5. 修改配置或 API 时保留现有线程调度、不可变快照、资源重载及启动阶段配置未加载兼容行为。
6. 使用 `apply_patch` 编辑，保留用户已有改动；不得删除 IDE 文件或运行产物目录中的用户数据。
7. 修改后运行覆盖变更的测试，并报告命令、结果、未验证项和产物路径。
8. 新增或改变用户可见功能时，同步更新对应专题文档；本文件记录稳定开发约束，不复制完整 API 手册。

## 当前停用的物品示范

`src/main/java/com/xkmxz/prismod/Prismod.java` 中的示例方块、示例物品和创造模式标签注册目前使用块注释保留。只有当物品成为 Prismod 功能的一部分时才恢复：启用对应导入和注册代码，在构造函数调用 `ITEMS.register(modEventBus)`，并补充创造模式标签、模型、语言键和纹理；不得只为验证模板而注册示例物品。

## 命令与文档规则

Prismod 当前注册 `/prismod` 管理命令和独立 API 测试命令。管理命令语法、权限和反馈以[服务端 API、命令与协议](docs/api/Prismod_服务端_API_命令与协议.md)为准；不要在本文件复制完整命令树。新增命令时明确它运行于客户端还是服务端，检查事件总线及专用服务器类加载边界。

新增 API 或用户功能时，应在对应专题文档说明调用环境、参数、线程、失败行为和可复现的使用路径。资源包功能还要说明目录、清单、导入方式和资源重载时机。跨端架构只更新[API 架构与文档索引](docs/api/API_架构与文档索引.md)。
