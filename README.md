# Spark-Performance-Insight

English | [中文](./README.zh.md)

[![AI Powered](https://img.shields.io/badge/Powered%20by-Gemini%20AI-blue.svg)](https://deepmind.google/technologies/gemini/)

---

An advanced Spark performance analysis system designed to address the core pain points of the native Spark Web UI/History Server. It aims to completely solve the "slow replay" and "no comparison" issues in Spark performance diagnosis through structured storage and multi-dimensional comparison technologies.

## Why do we need it? (The Pain Points)

While the native Spark Web UI provides basic monitoring, it suffers from the following core pain points during deep performance analysis and production operations:

### 1. History Server Architectural Bottlenecks
- **Event Replay Overhead:** The native History Server relies on replaying raw EventLogs to reconstruct job state. For massive jobs, replaying TB-sized logs results in extremely high CPU and memory overhead, leading to very slow response times.
- **Memory Starvation & Scalability:** Lacking pre-aggregated structured storage, all metrics must be cached in memory. This easily triggers SHS process OOM or UI crashes when handling ultra-large jobs with millions of tasks.
- **No Query Indexing:** The linear storage format does not support efficient indexing, searching, or pagination. Browsing, filtering, or sorting through tens of thousands of Stages or millions of Tasks provides an extremely poor user experience.

### 2. Lack of Deep Horizontal Comparison (No Job/Stage Comparison)
- **Unquantifiable Differences:** When job performance degrades, the native UI cannot directly compare metrics across Jobs or Stages. Users find it difficult to intuitively see which specific Stage has slowed down and how the Task distribution has changed.
- **Environment Blind Spots:** It is hard for users to quickly identify if performance degradation is due to changes in `spark.conf` parameters, Executor resource configuration differences, or underlying hardware environment issues.

## Core Features

### 1. Classic UI Parity & Beyond
- **Jobs & Stages Explorer:** Deep replication of the native lists, supporting millisecond-level sorting and filtering powered by DuckDB.
- **Executor Monitor:** Comprehensive monitoring of compute node resources (Cores/Memory) and runtime status.
- **High-Performance Task List:** **Core Competency**. Supports database-level pagination loading of millions of Tasks, with built-in alerts for abnormal GC ratios and disk spills.

### 2. Smart Diagnosis Engine
- **Markdown Report Generation:** Automatically analyzes data skew, GC pressure, disk spills, etc., generating LLM-friendly diagnostic reports.
- **Visual Risk Indicators:** Automatically marks high-risk stages with colors in the Stages list.

### 3. Multi-dimensional Deep Comparison (Job/Stage Comparison)
- **Cross-App Job Comparison:** Supports selecting two Jobs (can belong to different Application instances). The system automatically identifies and correlates the performance metrics of corresponding Stages.
- **Stage Specific Analysis:** Supports direct selection of two Stages for deep benchmarking. Includes:
    - **Statistical Distribution Comparison:** Differences in Min, Median, Max, P95 distributions for metrics like Duration, GC Time, Shuffle Read/Write, etc.
    - **Task Detail Comparison:** Automatically identifies long-tail Tasks and compares subtle differences in Task execution traces between two Stages.

### 4. Broad Log Compatibility
- **Zstd Compression Support:** Native support for reading `.zstd` and `.zst` compressed logs without manual decompression.
- **Spark V2 Log Structure:** Supports recursive directory scanning, perfectly compatible with Spark V2's directory-based EventLog storage structure.
- **Robust Parsing:** Automatically handles missing fields for different log versions to ensure a stable, crash-free parsing process.

## Technical Stack

- **Frontend:** Vue 3 + Vite + ECharts (for full-link latency and resource distribution visualization).
- **Backend:** Java 21 + Spring Boot 3.x (utilizing Virtual Threads to improve log parsing efficiency).
- **Database:** DuckDB (In-process embedded OLAP database for high-performance analytical queries).
- **Persistence (ORM):** MyBatis Plus (Using XML for SQL to facilitate future database extensions and SQL optimization).
- **Run Mode:** Standalone JAR execution, supporting offline analysis.

## Workflow

1.  **Scan:** Scans the `eventlog` path specified in the configuration file upon startup.
2.  **Import:** Adopts a **"Dual-Track" Parsing Strategy**:
    -   **Current: FastEventParser** - Based on Jackson/Fastjson for asynchronous parsing of raw JSON, extracting core metrics into the database. Lightweight, no Spark dependency.
    -   **Reserved: SparkNativeParser** - Defines a unified interface, reserving the ability to integrate the `spark-core` ReplayListenerBus in the future to support native parsing for different versions.
3.  **Pre-Calculate:**
    -   Automatically calculates key metrics at the Stage/Task level during the ingestion phase.
    -   Pre-aggregates: GC time ratio, Average, Median (P50), P90/P95/P99 duration, etc.
4.  **Analyze:** Provides single-task deep analysis and multi-task horizontal comparison via the Web UI.

## Roadmap

- [ ] **Structured Storage Engine:** Implement streaming parsing and structured ingestion of EventLog, completely saying goodbye to raw log replay.
- [ ] **Multi-Version Event Parser:**
    - [x] Fast parser implementation based on Jackson.
    - [ ] Plugin-based parsing interface design (supporting custom Event mapping).
    - [ ] Reserve native Spark Jar replay adapter.
- [ ] **Stage Summary Metrics:**
    - [ ] Implement Task statistics panel similar to Spark UI.
    - [ ] Provide Min, 25%, Median, 75%, 95%, Max statistical distribution for metrics like Duration, GC Time, Shuffle Read/Write, Input Bytes.
- [ ] **Deep Comparator:** Support full comparison at Job and Stage dimensions.
    -   **Cross-App Benchmarking:** Automatically match Job logic relationships between different run instances.
    -   **Metric Drill-down:** One-click drill-down from Stage overview to specific Task difference comparison.
    -   **Parameter Deviation:** Automatically highlight differences in Environment and SparkConf.

## Quick Start

(Waiting for future development...)
