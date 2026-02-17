# UI: 对比工作区

[English](../../en/ui/Compare_Workspace.md) | [中文](./Compare_Workspace.md)

对比工作区充当性能分析的“购物车”，允许用户挑选候选对象进行 side-by-side 的深度对标。

## 📊 可见信息
- **候选网格**：已选择的待对比 Application、Job 和 Stage。
- **有效性状态**：实时检查选择的项目是否仍然存在或是否已准备好分析。
- **摘要卡片**：快速查看候选对象的指标（时长、任务数、状态）。
- **对比启动器**：当选择了两个同类型的项目时，触发深度对标引擎。

## 🔍 数据来源
- **持久化**：通过浏览器 `localStorage` 管理，实现跨会话持久化。
- **校验 API**：`/api/compare/validate` 接口根据当前数据库状态检查候选对象。

## 🖼 界面展示
![对比工作区](../../img/ui_compare_workspace.png)
