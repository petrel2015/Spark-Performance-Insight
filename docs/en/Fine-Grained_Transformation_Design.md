# Design Doc: Fine-Grained Data Transformation and Aggregation

## 1. Overview
The current monolithic SQL approach for data processing risks high memory consumption in DuckDB and lacks granular progress visibility. This design proposes a "Divide and Conquer" strategy, breaking down App-level SQL into Stage-level and Job-level tasks.

## 2. Infrastructure: Unified Thread Pool
- **Transformation Executor**: Both Silver and Gold phases submit tasks to the same `transformationExecutor`.
- **Configuration**:
    - **Default Threads**: 1 (effectively sequential by default to ensure maximum stability on low-resource environments).
    - **Configurable**: Adjustable via `insight.transformation.threads` in `application.yml`.
- **Execution Principle**:
    - Tasks with strict dependencies (e.g., Tasks -> Stages) **must** run sequentially within the same thread task or via ordered futures.
    - Only truly independent entities (e.g., Executors, Environment) are dispatched for parallel execution.

---

## 3. Part I: Refactoring Silver Transformation
The goal is to move data from `bronze` (JSON) to `silver` (Structured) in manageable chunks.

### Implementation Steps:
1.  **Metadata & Discovery Phase**: 
    - Extract basic App metadata (Sequential).
    - Collect unique `(stageId, stageAttemptId)` list from `bronze_event_task_end`.
2.  **Task Transformation Task (Core sequential block)**:
    - Submit a task to the executor that iterates through Stages.
    - For each Stage, transform its Tasks first, then finalize the Stage metadata (Strict ordering).
3.  **Parallel Entity Transformation**:
    - Concurrently submit tasks for smaller entities (Jobs, Executors, SQL, Environment) to the same executor.

---

## 4. Part II: Refactoring Gold Aggregation
The goal is to aggregate `silver` data into `gold` tables using a Job-Stage topology.

### Implementation Steps:
1.  **Job-Centric Task Mapping**:
    - Query `silver_jobs` to identify IDs.
    - Initialize a `TransformationContext` to track Job-Stage dependencies.
2.  **Stage-Level Aggregation (Parallel Units)**:
    - Dispatch each Stage's heavy analytical task to the executor.
3.  **Job-Level Trigger (Ordered)**:
    - A Job's aggregation task is triggered **only** after all its associated Stage tasks are completed.
4.  **App Finalization**:
    - Performed sequentially after all Job-level tasks are finished.

## 5. State Persistence & Incremental Retry (Checkpointing)
To support resuming after a crash or power failure, a progress tracking table will be introduced in DuckDB.

### 5.1 Schema: `pipeline_progress`
| Column | Type | Description |
|--------|------|-------------|
| app_id | VARCHAR | Unique Application ID |
| phase | VARCHAR | SILVER_TRANSFORM or GOLD_AGGR |
| task_type | VARCHAR | STAGE or JOB |
| task_id | VARCHAR | identifier (e.g., stage_0_0) |
| status | VARCHAR | COMPLETED or FAILED |
| updated_at | TIMESTAMP | Last update time |

### 5.2 Resumption Logic
- Before starting a Stage/Job task, the engine checks `pipeline_progress`.
- If a task is marked as `COMPLETED`, it is skipped.
- **Incremental Retry (Beta)**: A new UI option allows users to trigger this "Resume" mode. A warning is displayed: *"Incremental retry is more efficient but may risk stale data consistency (Beta)."*
- **Full Retry**: If a user triggers a full retry, the system must:
    1. Delete all records for the corresponding `app_id` and `phase` from the `pipeline_progress` table.
    2. Purge all partially transformed/aggregated data from the `silver` or `gold` tables for that `app_id` before starting.

---

## 6. Key Benefits
-   **Memory Safety**: DuckDB buffer manager only handles data for one Stage at a time.
-   **Configurable Parallelism**: Ability to scale from 1 thread to many based on hardware.
- **Granular Progress**: Real-time reporting of "Processed X/Y Stages (Percentage%)". Percentages should be calculated and displayed with up to two decimal places (e.g., "45/100 (45.00%)").

