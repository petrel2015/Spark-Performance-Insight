# Database Storage Design Specifications

[English](./Database_Design.md) | [中文](../zh/Database_Design.md)

## 1. Storage Engine: DuckDB
The project uses DuckDB 1.1.x as the core OLAP engine. All data is persisted in the `.duckdb.db` file within the `out/` directory.

## 2. Table Naming Conventions
| Prefix | Description | Example |
| :--- | :--- | :--- |
| **`bronze_`** | Raw event tables, stores raw JSON lines before parsing | `bronze_event_task_end` |
| **`silver_`** | Structured fact tables, modeled by entity | `silver_tasks`, `silver_stages` |
| **`gold_`** | Aggregated analytical tables, read directly by UI | `gold_applications`, `gold_jobs` |
| **`sys_`** | Internal system management tables | `sys_parsing_queue` |

## 3. Core Entity Relationship Diagram (ER Diagram)
The system uses a typical hierarchical monitoring model, with all entities isolated at the top level by `app_id`.

```mermaid
erDiagram
    GOLD_APPLICATIONS ||--o{ GOLD_SQL_EXECUTIONS : executes
    GOLD_APPLICATIONS ||--o{ GOLD_ENVIRONMENT_CONFIGS : configures
    GOLD_APPLICATIONS ||--o{ GOLD_EXECUTORS : manages
    GOLD_APPLICATIONS ||--o{ GOLD_STORAGE_RDDS : persists
    
    GOLD_SQL_EXECUTIONS ||--o{ GOLD_JOBS : triggers
    GOLD_JOBS ||--o{ GOLD_STAGES : contains
    GOLD_STAGES ||--o{ GOLD_TASKS : executes
    
    GOLD_STORAGE_RDDS ||--o{ GOLD_STORAGE_BLOCKS : stores
    GOLD_EXECUTORS ||--o{ GOLD_STORAGE_BLOCKS : hosts
    GOLD_EXECUTORS ||--o{ GOLD_TASKS : runs

    GOLD_APPLICATIONS {
        string app_id PK
        string app_name
        timestamp start_time
        string status
        int performance_score
    }

    GOLD_SQL_EXECUTIONS {
        string id PK
        bigint execution_id
        string description
        string physical_plan
    }

    GOLD_JOBS {
        string id PK
        int job_id
        bigint sql_execution_id FK
        string status
    }

    GOLD_STAGES {
        string id PK
        int stage_id
        int job_id FK
        boolean is_skewed
    }

    GOLD_TASKS {
        bigint task_id PK
        int stage_id FK
        string executor_id FK
        bigint duration
    }

    GOLD_EXECUTORS {
        string executor_id PK
        string host
        boolean is_active
    }
```

## 4. Memory Protection & Retry (OOM Protection)
Since DuckDB runs within the JVM process, it may hit memory thresholds when processing large-scale data (millions of Tasks).

### 4.1 Auto-Recovery Algorithm
1.  **Detection**: Catches `java.sql.SQLException`, checks if the message contains `Out of Memory` or `could not allocate block`.
2.  **Forced Release**: Calls `CHECKPOINT` to force DuckDB to flush dirty pages from the Buffer Manager to disk.
3.  **Reconfiguration**: Re-executes `SET memory_limit = '...'` to align with the current config.
4.  **Retry**: Automatically re-executes the last SQL task that caused the OOM.

## 5. Key Index Optimizations
- Uses **AppId** extensively as a partition/filtering key.
- Utilizes DuckDB's Min/Max Zone Maps for range pruning on ultra-large tables like `gold_tasks`.
