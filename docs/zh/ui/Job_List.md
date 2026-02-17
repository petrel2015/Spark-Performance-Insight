# UI: Job 列表

[English](../../en/ui/Job_List.md) | [中文](./Job_List.md)

Job 列表提供了特定应用中所有 Spark Job 的结构化概览，提供比原生 Spark UI 更深度的洞察。

## 📊 可见信息
- **Job 标识**：Job ID 和 Job Group（支持多列排序）。
- **性能评分**：基于所属 Stage 计算的聚合健康评分。
- **逻辑链路**：关联的 SQL 执行 ID 和所有 Stage ID 列表。
- **进度追踪**：显示成功、失败和活动任务的实时进度条。
- **资源指标**：总时长和 Stage 数量。

## 🔍 数据来源
- **核心表**：`gold_jobs` (DuckDB)。
- **关联关系**：通过 `job_id` 和 `app_id` 动态关联到 `gold_stages`。
- **预聚合**：评分和摘要在“黄金层 (Gold)”处理阶段预先计算。

## 🖼 界面展示
![Job 列表](../../img/ui_job_list.png)
