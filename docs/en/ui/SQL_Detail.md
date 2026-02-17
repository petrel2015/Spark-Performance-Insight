# UI: SQL / DataFrame Detail

[English](./SQL_Detail.md) | [中文](../../zh/ui/SQL_Detail.md)

This page provides a deep dive into Spark SQL executions, including physical plans and the relationship between SQL operations and Spark Jobs.

## 📊 Information Visible
- **Execution Metadata**: Description, Duration, and Submission time.
- **Logical Mapping**: A list of all Spark Jobs triggered by this SQL execution.
- **Plan Analysis**: Full Physical Plan display with syntax highlighting.
- **DAG Visualization**: (Coming Soon) Graphical representation of the SQL plan.

## 🔍 Data Source
- **Primary Table**: `gold_sql_executions` (DuckDB).
- **Mapping**: Correlated with `gold_jobs` via the `sql_execution_id` extracted from the EventLog.

## 🖼 UI Preview
![SQL Detail](../../img/ui_sql_detail.png)
