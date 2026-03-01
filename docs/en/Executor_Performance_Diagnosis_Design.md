# Design Doc: Cross-Executor Performance Diagnosis and Timeline Analysis

## 1. Overview
The goal of this feature is to identify performance anomalies across different executors (e.g., hardware issues, network bottlenecks) and provide a time-series view of resource utilization and throughput.

---

## 2. Executor Diagnosis Card
This card will be located at the top of the **Executors** tab to highlight potential "Slow Nodes."

### 2.1 Core Metrics for Comparison
To determine if an executor is underperforming, we compare its efficiency against the cluster-wide median:
1.  **Task Throughput Efficiency**: `Tasks Completed / Total Executor Run Time`.
    - Detects executors that process fewer tasks than others despite similar run times.
2.  **HDFS I/O Velocity**: `HDFS Bytes Read / Total Executor Run Time`.
    - Compares HDFS access speed. If one executor reads significantly fewer bytes per second of active time, it likely has a disk or local network issue.
3.  **Shuffle I/O Velocity**: `Shuffle Read/Write Bytes / Total Executor Run Time`.

### 2.2 Anomaly Detection Logic
- **Threshold**: An executor is flagged if its efficiency is `< 60%` of the cluster median (configurable).
- **Diagnosis Output**: 
    - "Executor X shows 40% lower HDFS I/O efficiency than average, indicating potential disk/network bottlenecks."
    - "Executor Y has significantly lower task throughput, check for CPU throttling or high Load Average on the host."

---

## 3. Executor Performance Timeline Chart
A multi-metric time-series chart to visualize activity distribution.

### 3.1 Chart Configuration (ECharts)
- **X-Axis**: Event Time (Windowed by minute or custom interval).
- **Y-Axis**: 
    - **Tasks**: Count of tasks processed by the executor in the window.
    - **HDFS I/O**: Bytes read/written from/to HDFS.
    - **Shuffle Metrics**: Bytes and records for Shuffle Read/Write.
- **Series**: Each executor will be a selectable line, or we provide a "Stacked Area" view for cluster-wide distribution.

### 3.2 Data Aggregation Query
We need a windowed aggregation over `gold_tasks`:
```sql
SELECT 
    floor(launch_time / 60000) * 60000 as time_window,
    executor_id,
    count(*) as task_count,
    sum(input_bytes) as hdfs_read,
    sum(shuffle_read_bytes) as shuffle_read,
    ...
FROM gold_tasks
WHERE app_id = ?
GROUP BY 1, 2
ORDER BY 1 ASC
```

---

## 4. UI/UX Enhancements
1.  **Diagnosis Alert**: A prominent alert box if anomalies are detected.
2.  **Metric Switcher**: Toggle between Task Count, HDFS Bytes, and Shuffle data on the timeline.
3.  **Cross-Highlighting**: Clicking an executor in the list highlights its corresponding line in the timeline.
