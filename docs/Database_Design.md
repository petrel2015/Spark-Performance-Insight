# 数据库存储设计规格

## 1. 存储引擎：DuckDB
项目使用 DuckDB 1.1.x 作为核心 OLAP 引擎，所有数据持久化在 `out/` 目录下的 `.duckdb.db` 文件中。

## 2. 表命名规范
| 前缀 | 说明 | 示例 |
| :--- | :--- | :--- |
| **`bronze_`** | 原始事件表，存储解析前的原始 JSON 行 | `bronze_event_task_end` |
| **`silver_`** | 结构化事实表，按实体建模 | `silver_tasks`, `silver_stages` |
| **`gold_`** | 汇聚分析表，UI 直接读取的视图或物理表 | `gold_applications`, `gold_jobs` |
| **`sys_`** | 系统内部管理表 | `sys_parsing_queue` |

## 3. 内存保护与重试 (OOM Protection)
由于 DuckDB 运行在 JVM 进程内，当处理大规模数据（百万级 Task）时，可能会触碰内存阈值。

### 3.1 自动恢复算法
1.  **检测**: 捕获 `java.sql.SQLException`，检查 Message 是否包含 `Out of Memory` 或 `could not allocate block`。
2.  **强制释放**: 调用 `CHECKPOINT` 命令强制 DuckDB 将 Buffer Manager 中的脏页刷入磁盘。
3.  **重配置**: 重新执行 `SET memory_limit = '...'` 以对齐当前配置。
4.  **重试**: 自动重新执行导致 OOM 的上一条 SQL 任务。

## 4. 关键索引优化
- 全面采用 **AppId** 作为分区/过滤键。
- 在 `gold_tasks` 等超大表上，利用 DuckDB 的 Min/Max Zone Map 进行区间剪枝。
