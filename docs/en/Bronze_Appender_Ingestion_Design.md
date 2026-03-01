# Design Doc: High-Performance Bronze Ingestion via DuckDB Appender

## 1. Overview
The current Bronze ingestion uses `jdbcTemplate.batchUpdate` into temporary tables, which involves high overhead due to JDBC row-by-row processing and SQL parsing. This design proposes using the **DuckDB Native Appender**, the fastest ingestion path in DuckDB, while maintaining resource stability and progress visibility.

---

## 2. Technical Implementation: Native Appender
Instead of generating `INSERT` statements, we will use the `DuckDBAppender` API provided by the native driver.

### 2.1 Implementation Logic
1.  **Direct Streaming**: Read event logs line-by-line using a buffered reader.
2.  **Schema-Based Appenders**: Create a pool of appenders for each Bronze event table (e.g., `bronze_event_task_end`).
3.  **JSON Pre-validation**: Perform lightweight JSON validation and extraction of the "Event" type in Java to route data to the correct table's appender.
4.  **Transaction Batching**: Wrap appends in periodic checkpoints/transactions to ensure ACID properties without sacrificing speed.

---

## 3. Stability & Resource Controls
To prevent DuckDB from exhausting system memory or saturating CPU:

### 3.1 Memory Management (Backpressure)
- **Batch Flush**: Data will be flushed from the appender to the persistent storage every **10,000 rows** or **5MB** of buffered data.
- **Single-Threaded Append per App**: To prevent CPU contention, only one thread will perform appending per application ingestion, ensuring predictable resource usage.

### 3.2 CPU Throttling
- Limit the total number of concurrent active appenders across the entire system via a global semaphore or the existing `transformationExecutor`.

---

## 4. Progress Control
Accurate progress reporting is critical for long-running imports.

### 4.1 Byte-Offset Calculation
- Progress will be calculated based on the **File Descriptor's byte offset** rather than row counts.
- `Progress = current_file_position / total_file_size`.
- This ensures a monotonic and accurate percentage even if event logs have varying line lengths.

---

## 5. Key Benefits
- **Speed**: Expected 5x - 10x throughput improvement over JDBC batch inserts.
- **Stability**: Constant memory footprint regardless of the log file size.
- **Control**: Real-time progress updates precisely matched to disk I/O.
