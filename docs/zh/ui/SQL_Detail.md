# UI: SQL / DataFrame 详情

[English](../../en/ui/SQL_Detail.md) | [中文](./SQL_Detail.md)

此页面深入展示了 Spark SQL 的执行情况，包括物理计划以及 SQL 操作与 Spark Job 之间的关系。

## 📊 可见信息
- **执行元数据**：描述、时长和提交时间。
- **逻辑映射**：由此 SQL 执行触发的所有 Spark Job 列表。
- **计划分析**：带有语法高亮的完整物理计划（Physical Plan）展示。
- **DAG 可视化**：(即将推出) SQL 计划的图形化表示。

## 🔍 数据来源
- **核心表**：`gold_sql_executions` (DuckDB)。
- **映射关系**：通过从 EventLog 中提取的 `sql_execution_id` 与 `gold_jobs` 相关联。

## 🖼 截图预留
![SQL 详情](../../img/ui_sql_detail.jpg)
