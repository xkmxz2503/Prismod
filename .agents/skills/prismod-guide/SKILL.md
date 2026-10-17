---
name: prismod-项目引导
description: 在 Prismod 仓库中进行开发时使用，负责项目契约的读取顺序、任务路由和验证要求。
---

# Prismod 项目引导

这个 Skill 负责 Prismod 任务的读取顺序和验证流程。项目架构、渲染边界、资源包契约和稳定开发约束以根目录 `PRISMOD_GUIDE_PROMPT.md` 为准；本 Skill 不复制完整业务规则。

## 必须执行

1. 先读取 `PRISMOD_GUIDE_PROMPT.md`。
2. 再读取 `../../../docs/api/API_架构与文档索引.md`，确认跨端边界和当前规范来源。
3. 按任务读取一个专题文档：客户端 API、服务端 API/命令/协议或资源包格式/编辑。
4. 处理 API 问题时先读 `../../../docs/api/bug/INDEX.md`，再按需读取相关活动问题记录；不要默认扫描归档。
5. 修改前追踪实际调用链和数据流；不要仅凭类名、历史设计或模板猜测行为。
6. 修改完成后运行覆盖变更的测试和 `git diff --check`，报告实际结果与未验证项。

## 根 README 边界

仓库根目录的 `README.md` 只面向人类读者，AI 默认不得读取、引用或修改；只有用户明确要求更新 README 时才允许处理。资源包内部的 `README.md` 和 `docs/example-resource-pack/README.md` 是产品资料，按相关资源包任务读取。

## 按任务读取

- 客户端 API：`src/main/java/com/xkmxz/prismod/api/client/`、相关测试、`../../../docs/api/Prismod_客户端_API_规范.md`
- 服务端 API、命令或协议：`src/main/java/com/xkmxz/prismod/api/server/`、`src/main/java/com/xkmxz/prismod/network/`、相关测试、`../../../docs/api/Prismod_服务端_API_命令与协议.md`
- 资源包、编辑器或 shader：`src/main/java/com/xkmxz/prismod/client/pack/`、`src/main/java/com/xkmxz/prismod/client/ui/resourcepack/`、资源包测试、`../../../docs/Prismod_资源包格式与编辑指南.md`
- 架构或跨端边界：`../../../docs/api/API_架构与文档索引.md`、`PRISMOD_GUIDE_PROMPT.md`、相关源码和测试

## 文档同步规则

- 客户端 API 的参数、线程、回执和生命周期写入客户端专题文档。
- 服务端 API、命令和网络协议写入服务端专题文档。
- 资源包格式、目录、编辑器和重载流程写入资源包专题文档。
- `PRISMOD_GUIDE_PROMPT.md` 只记录稳定项目契约、边界和维护规则，不复制完整 API 手册，也不要求在其中保留专题示例。
- 新增或修改用户可见功能时，先以源码和测试为事实来源，再同步唯一对应的规范文档。
- 每次发现 API 问题都在 `../../../docs/api/bug/list/` 新建独立记录，不覆盖旧问题；修复并验证后移至 `../../../docs/api/bug/archive/` 并更新问题索引。同类问题再次发生时新建记录并关联历史记录。

## 验证边界

普通文档或 AI 入口变更执行静态链接审计和 `git diff --check`。修改 Java、GLSL、渲染、GUI、网络或资源加载行为时，才运行对应 Gradle 测试；未运行的游戏内、专用服务器或可选模组验证必须明确报告。
