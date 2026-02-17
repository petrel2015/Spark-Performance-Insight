# UI: Job List

[English](./Job_List.md) | [中文](../../zh/ui/Job_List.md)

The Job List provides a structured overview of all Spark Jobs within a specific application, offering deeper insights than the native Spark UI.

## 📊 Information Visible
- **Job Identity**: Job ID and Job Group (with multi-column sorting).
- **Performance Score**: An aggregated health score calculated from constituent stages.
- **Logical Links**: Associated SQL Execution ID and a list of all Stage IDs.
- **Progress Tracking**: Real-time progress bar showing succeeded, failed, and active tasks.
- **Resource Metrics**: Total duration and stage count.

## 🔍 Data Source
- **Primary Table**: `gold_jobs` (DuckDB).
- **Relationships**: Dynamically linked to `gold_stages` via `job_id` and `app_id`.
- **Aggregation**: Scores and summaries are pre-calculated during the "Gold" processing phase.

## 🖼 Screenshot Placeholder
![Job List](../../img/ui_job_list.jpg)
