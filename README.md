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
- **Event Replay Overhead:** TB-sized logs result in extreme CPU/Memory overhead and minute-long wait times due to replaying raw JSON.
- **Scalability Issues:** Without structured storage, all metrics must be cached in memory, often triggering OOM when handling jobs with millions of tasks.
- **No Query Indexing:** Linear storage lacks efficient indexing. Browsing through tens of thousands of stages provides a poor user experience.

### 2. Lack of Deep Comparison
- **Unquantifiable Differences:** Impossible to compare metrics directly across different runs to identify root causes.
- **Environment Blind Spots:** Hard to identify if performance changes are due to configuration tweaks or hardware discrepancies.

## Core Features

### 1. Classic UI Parity & Beyond
- **Familiar Interface:** Deeply replicates the native Spark UI lists (Jobs, Stages, Tasks), progress bars, and descriptions to ensure a seamless transition for developers.
- **Enhanced Summary:** Provides statistical distributions (Min, 25%, Median, 75%, 95%, Max) for Duration, GC Time, Spill, and Shuffle metrics.
- **Advanced Task View:** Supports server-side pagination for millions of tasks with multi-column sorting (Shift+Click).

### 2. Medallion Data Pipeline
- **Bronze (Raw Ingestion):** High-speed streaming ingestion using Jackson.
- **Silver (Transformation):** Structured parsing, recovering logical relationships and identifying long-tail tasks.
- **Gold (Aggregation):** Pre-calculated analytical tables for instant UI response times.

### 3. Advanced Storage Analysis
- **Persistence Tracking:** Comprehensive view of cached RDDs and DataFrames.
- **Structural UI:** Detailed storage levels (Memory/Disk/Deserialized) with status indicators.
- **Deep Linking:** Every RDD has its own unique URL for easy sharing.

### 4. Smart Diagnosis Engine
- **AI-Powered Analysis:** Integrates **Zhipu AI (GLM-4.7)** and **OpenAI** for expert-level diagnostic reports.
- **Rule-Based Insights:** Automatically identifies data skew, GC pressure, disk spills, and scheduler delays.

### 5. Multi-dimensional Benchmarking
- **Cross-App Comparison:** Side-by-side comparison of different application instances.
- **Stage Benchmarking:** Deep dive into two stages to compare distributions and task traces.

### 6. Enterprise-Grade Robustness
- **OOM Recovery:** Automatic DuckDB memory management with `CHECKPOINT` and retry logic.
- **Timing Accuracy:** Synchronized epoch milliseconds and monotonic progress tracking.
- **Broad Compatibility:** Native support for **ZSTD** and Spark **V2 log directories**.

## Technical Stack

- **Frontend:** Vue 3 + Vite + ECharts + Material Design.
- **Backend:** Java 21 (Virtual Threads) + Spring Boot 3.x.
- **OLAP Engine:** [DuckDB](https://duckdb.org/) (Embedded analytical database).
- **ORM:** MyBatis Plus (XML-based for optimized SQL).

## Quick Start

### Build and Run (Native)

1.  **Build the Project:**
    Includes frontend build and runs the application via Docker Compose.
    ```bash
    mvn clean install -Pbuild-frontend -Prun
    ```

2.  **Access the UI:**
    Visit `http://localhost:18081` in your browser.

### Build and Run (Docker Compose)

This method starts both **Spark Performance Insight** and a native **Spark History Server** using the same log directory, allowing for side-by-side comparison.

1.  **Start Services:**
    ```bash
    docker compose up -d
    ```

2.  **Access Points:**
    -   **Insight UI:** `http://localhost:18081`
    -   **Spark History Server:** `http://localhost:18080`

## Acknowledgments

- Special thanks to the authors of the **smart-commit** and **release-skills** tools for streamlining our development and release workflows.
