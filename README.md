# Spark-Performance-Insight

English | [中文](./README.zh.md)

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](./CHANGELOG.md)
[![AI Powered](https://img.shields.io/badge/Powered%20by-Gemini%20AI-blue.svg)](https://deepmind.google/technologies/gemini/)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

---

An advanced Spark performance analysis system designed to address the core pain points of the native Spark Web UI/History Server. It eliminates "slow replay" and "lack of comparison" issues through **Medallion Architecture**, **Structured OLAP Storage**, and **Multi-dimensional Benchmarking**.

## Why Spark-Performance-Insight?

While the native Spark Web UI provides basic monitoring, it suffers from critical limitations during deep performance analysis and production operations:

### 1. History Server Architectural Bottlenecks
- **Event Replay Overhead:** Relies on replaying raw JSON EventLogs to reconstruct state. For massive jobs, TB-sized logs result in extreme CPU/Memory overhead and minute-long wait times.
- **Scalability Issues:** Without structured storage, all metrics must be cached in memory, often triggering OOM or UI crashes when handling jobs with millions of tasks.
- **No Query Indexing:** Linear storage lacks efficient indexing. Browsing through tens of thousands of stages or millions of tasks provides a poor user experience.

### 2. Lack of Deep Comparison
- **Unquantifiable Differences:** When a job slows down, it is nearly impossible to compare metrics directly across different runs or stages to identify the root cause.
- **Environment Blind Spots:** Hard to quickly identify if performance changes are due to `spark.conf` tweaks, resource allocation differences, or hardware discrepancies.

## Core Features

### 1. Medallion Data Pipeline
- **Bronze (Raw Ingestion):** High-speed streaming ingestion using Jackson, handling TB-sized logs with ease.
- **Silver (Transformation):** Structured parsing and normalization, recovering logical relationships and identifying long-tail tasks.
- **Gold (Aggregation):** Pre-calculated analytical tables for instant UI response times, even for massive datasets.

### 2. Advanced Storage Analysis
- **Persistence Tracking:** Comprehensive view of cached RDDs and DataFrames.
- **Structural UI:** Detailed storage levels (Memory/Disk/Deserialized) with status indicators.
- **Deep Linking:** Every RDD has its own unique URL for easy sharing and direct access.

### 3. Smart Diagnosis Engine
- **AI-Powered Analysis:** Integrates **Zhipu AI (GLM-4.7)** and **OpenAI** to generate expert-level diagnostic reports.
- **Rule-Based Insights:** Automatically identifies data skew, GC pressure, disk spills, and scheduler delays with visual risk indicators.

### 4. Multi-dimensional Benchmarking
- **Cross-App Comparison:** Compare two different application instances side-by-side.
- **Stage Benchmarking:** Deep dive into two stages to compare statistical distributions (P95, Median) and task execution traces.

### 5. Enterprise-Grade Robustness
- **OOM Recovery:** Automatic DuckDB memory management with `CHECKPOINT` and retry logic.
- **Timing Accuracy:** Synchronized epoch milliseconds and monotonic progress tracking for reliable time-to-completion estimates.
- **Broad Compatibility:** Native support for **ZSTD** compression and Spark **V2 log directories**.

## Technical Stack

- **Frontend:** Vue 3 + Vite + ECharts + Material Design.
- **Backend:** Java 21 (Virtual Threads) + Spring Boot 3.x.
- **OLAP Engine:** [DuckDB](https://duckdb.org/) (Embedded analytical database for high-performance SQL queries).
- **ORM:** MyBatis Plus (XML-based for optimized analytical SQL).

## Quick Start

### Build and Run

1.  **Build the Project:**
    Builds both frontend and backend into a single executable JAR.
    ```bash
    mvn clean install
    ```

2.  **Run the Application:**
    ```bash
    java -jar target/spark-performance-insight-1.0.0.jar
    ```

3.  **Access the UI:**
    Visit `http://localhost:18081` in your browser.

## Roadmap

- [x] **Medallion Architecture:** Fully implemented Bronze/Silver/Gold storage engine.
- [x] **LLM Diagnosis:** Integrated deep analysis with AI.
- [x] **Storage Overhaul:** Structural tags and deep-link support.
- [ ] **DAG Visualization Enhancement:** Richer interactive graphs for Job/Stage relationships.
- [ ] **Streaming Ingestion:** Real-time processing of in-progress application logs.

## Acknowledgments

- Special thanks to the authors of the **smart-commit** and **release-skills** tools for streamlining our development and release workflows.
