# 资源包编辑器分层 Implementation Plan

**目标：** 将资源包编辑器主页面改为抽象滤镜与说明编辑，并把原始文件编辑移动到高级子页面。

**架构：** 主页面持有资源包编辑草稿；滤镜、语言、README 和高级文件页面通过同一草稿返回主页面，不直接落盘。主页面保存时继续调用现有校验、备份、原子替换和资源重载流程。

**技术栈：** Minecraft 1.20.1、Forge 47.3.32、Java 17、现有 Screen/Button/EditBox/MultiLineEditBox、Gson。

## 全局约束

- 资源包仍使用 Prismod 私有 v1 格式，不引入 pack.mcmeta。
- 取消、关闭和子页面返回不写入磁盘；只有主页面保存写入。
- 语言编辑只修改 `assets/<namespace>/lang/<language>.json` 中的滤镜显示名称键。
- 高级文件编辑保留完整路径作为内部 key，但界面显示文件名。

## 任务

### 任务 1：共享草稿与滤镜抽象页面

- 新增 `ResourcePackEditorDraft`，封装文件映射、删除列表、名称、namespace、选中滤镜和修改方法。
- 修改 `ResourcePackEditorScreen`，只显示资源包信息、滤镜摘要、README、语言编辑和高级编辑入口。
- 新增 `ResourcePackFilterScreen`，编辑清单条目和对应 `filter.json` 抽象字段。

### 任务 2：说明、语言和高级页面

- 新增 README 编辑子页面。
- 新增语言选择及语言名称编辑子页面，按语言显示滤镜名称输入框。
- 新增高级文件编辑子页面，迁移现有文件树与文本框逻辑。

### 任务 3：保存、文案和验证

- 所有子页面返回共享草稿，主页面保存路径保持不变。
- 更新中英文语言键和项目引导文档。
- 运行 `test` 与 `git diff --check`。
