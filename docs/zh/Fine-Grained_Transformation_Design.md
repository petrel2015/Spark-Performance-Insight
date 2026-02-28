# 技术设计：细粒度数据转换与汇聚

## 1. 概述
当前基于全量应用（App-level）的单体 SQL 处理模式在 DuckDB 中存在内存占用过高的风险，且缺乏细粒度的进度可见性。本设计提出一种“分而治之”的策略，将应用级的 SQL 拆分为 Stage 级和 Job 级的子任务。

## 2. 基础设施：统一线程池
- **转换执行器 (Transformation Executor)**：Silver 转换和 Gold 汇聚阶段均向同一个 `transformationExecutor` 提交任务。
- **配置信息**：
    - **默认线程数**：1（默认情况下表现为严格串行，以确保在低资源环境下的最大稳定性）。
    - **可配置性**：可通过 `application.yml` 中的 `insight.transformation.threads` 进行调整。
- **执行原则**：
    - **依赖优先**：存在严格依赖关系的任务（如：Task 转换 -> Stage 统计）**必须**在同一个线程任务中按顺序执行，或通过有序的 Future 链确保顺序。
    - **并发条件**：仅在资源充足（配置线程 > 1）且任务间无依赖（如：Executors 与 Environment）时才真正实现并行。

---

## 3. 第一部分：Silver 转换改造步骤
目标是将数据从 `bronze` (JSON) 分块迁移到 `silver` (结构化) 表中。

### 改造步骤：
1.  **元数据与发现阶段 (串行)**：
    - 提取 App 基础信息。
    - 从 `bronze_event_task_end` 收集唯一的 `(stageId, stageAttemptId)` 列表。
2.  **核心顺序块 (Task -> Stage)**：
    - 向执行器提交一个顺序任务：遍历 Stage 列表，针对每个 Stage 先转换 Tasks，紧接着更新该 Stage 的 Silver 层元数据。
3.  **并行实体转换**：
    - 将 Jobs, Executors, SQL, Environment 等独立实体的转换任务提交至线程池。

---

## 4. 第二部分：Gold 汇聚改造步骤
目标是按照 Job-Stage 拓扑结构将 `silver` 数据汇总到 `gold` 表中。

### 改造步骤：
1.  **Job 核心映射**：识别 Job ID 及其关联的 Stage ID，并建立状态追踪上下文。
2.  **Stage 级汇聚 (执行单元)**：将每个 Stage 的复杂分析计算作为独立任务提交。
3.  **Job 级触发 (有序)**：只有当某个 Job 关联的所有 Stage 任务全部完成后，才触发该 Job 的最终汇聚。
4.  **App 最终化**：在所有 Job 任务完成后顺序执行。

## 5. 状态持久化与增量重试 (断点续传)
为支持掉电或进程崩溃后的断点续传，将在 DuckDB 中引入进度追踪表。

### 5.1 表结构：`pipeline_progress`
| 字段 | 类型 | 描述 |
|------|------|------|
| app_id | VARCHAR | 唯一应用 ID |
| phase | VARCHAR | SILVER_TRANSFORM 或 GOLD_AGGR |
| task_type | VARCHAR | STAGE 或 JOB |
| task_id | VARCHAR | 任务标识 (如：stage_0_0) |
| status | VARCHAR | COMPLETED 或 FAILED |
| updated_at | TIMESTAMP | 最后更新时间 |

### 5.2 续传逻辑
- 在启动 Stage/Job 级任务前，引擎会查询 `pipeline_progress`。
- 若该任务已标记为 `COMPLETED`，则直接跳过。
- **增量重试 (Beta)**：在前端 App 列表页新增重试选项。点击时提示用户：*“增量重试效率更高，但可能存在数据一致性风险（Beta版）。”*
- **全量重试**：若用户触发全量重试，系统必须执行以下清理：
    1. 从 `pipeline_progress` 表中删除对应 `app_id` 和 `phase` 的所有记录。
    2. 在开始转换前，清空该 `app_id` 已在 `silver` 或 `gold` 表中产生的任何部分转换/汇聚的数据。

---

## 6. 核心优势
-   **内存安全**：DuckDB 每次仅处理一个 Stage 的数据，有效避免 OOM。
-   **可伸缩并发**：支持从 1 到 N 个线程的动态伸缩，平衡稳定性与性能。
- **粒度进度**：UI 可实时显示“已完成 45/100 个 Stage (45.00%)”。百分比计算需精确到小数点后两位。

