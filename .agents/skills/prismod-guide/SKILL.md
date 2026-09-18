---
name: prismod-项目引导
description: 在 Prismod 仓库中进行开发时使用，尤其适用于修改过滤器、自定义资源包、客户端界面、渲染、配置、客户端 API、模板代码或项目文档的场景。
---

# Prismod 项目引导

这个 Skill 用于确保处理 Prismod 任务时遵守项目现有契约。它不替代引导文档；开始分析或修改代码前，必须先读取项目根目录的 `PRISMOD_GUIDE_PROMPT.md`。

## 必须执行

1. 先读取 `PRISMOD_GUIDE_PROMPT.md`，再读取 `README.md` 和与任务相关的文档、测试及源码。
2. 把引导文档中的配置格式、客户端/服务端边界、`FilterKey`/`FilterSelection` 身份、资源包格式、渲染时序和 UI 页面职责视为项目契约。若实际代码已经发生变化，先核对事实，再同步更新引导文档。
3. 修改前追踪实际调用链和数据流；不要仅凭类名或模板代码猜测行为。保留用户已有的未提交改动。
4. 重要功能完成后，在 `PRISMOD_GUIDE_PROMPT.md` 中补充用户用法、配置字段、API 调用、资源目录或界面操作步骤，并至少给出一个具体示例。
5. 如果从源码移除暂时不用的示范代码，可以删除源码中的代码，但必须在 `PRISMOD_GUIDE_PROMPT.md` 保留用途、恢复位置、启用步骤和完整示例。不要为了保留示例而把无用注册重新加入运行代码。
6. 完成后运行覆盖变更的测试和 `git diff --check`，报告实际运行的命令、结果以及没有验证的游戏内行为。

## Prismod 关键边界

- 这是 Minecraft 1.20.1、Forge 47.3.32、Java 17 的客户端滤镜模组；专用服务器路径不能加载 `net.minecraft.client` 或 `api.client`。
- 自定义资源包是 Prismod 自己的模组资源包，位于 `config/prismod/resourcepacks/`，不是 Minecraft 原版资源包；不要引入 `pack.mcmeta` 或原版资源包管理流程。
- 自定义滤镜必须保留 `namespace:path` 身份。UI 和 F8 提示使用 `FilterDefinition.displayName()`，不能用旧版 `FilterState.id()` 替代。
- Oculus 只能作为可选共存模组，不能调用其私有 API 或变成发布依赖。
- 滤镜后处理只作用于世界画面，不能把 HUD、聊天、菜单或容器绘制放入滤镜链。
- 资源包管理和滤镜展示管理是两个独立的二级页面；当前生效滤镜来自某资源包时，该资源包不能禁用。

## 读取顺序

根据任务选择补充阅读内容：

- 配置、启动和资源包：`src/main/java/com/xkmxz/prismod/client/PrismodClientConfig.java`、`PrismodPackLoader.java`、`README.md`、`docs/自定义滤镜资源包制作说明.md`
- 状态、F8 和 API：`FilterController.java`、`FilterManager.java`、`FilterApi.java`、`docs/Java_API_调用说明.md`
- 配置界面：`FilterConfigScreen.java`、`ResourcePackManagerScreen.java`、`FilterVisibilityManagerScreen.java`
- 渲染和 Mixin：`WorldFilterRenderer.java`、`mixin/client/`、相关 shader 资源
- 行为回归：`src/test/java/com/xkmxz/prismod/client/`

如果 `PRISMOD_GUIDE_PROMPT.md` 缺失或与当前实现冲突，先报告这个问题并以实际源码和测试结果为依据，不要无声地编造契约。

