# Gemini Development Guide - Spark-Performance-Insight

This document provides architectural context and development guidelines for Gemini CLI and other AI agents to maintain and evolve the Spark-Performance-Insight project.

## 🚀 Project Vision
An advanced Spark performance analysis system that replaces the slow "event replay" of Spark History Server with a **structured OLAP storage (DuckDB)** and provides **multi-dimensional comparisons**.

## 🏗 System Architecture

### 1. Backend (Java 21 + Spring Boot 3.x)
- **Log Parsing**: `JacksonEventParser` uses a streaming JSON approach for high-speed ingestion of Spark EventLogs.
- **Concurrency**: Heavily utilizes **Java 21 Virtual Threads** for I/O-bound tasks (log reading and DB persistence).
- **Storage**: **DuckDB** is used as an embedded OLAP engine. It's excellent for analytical queries over millions of tasks.
- **ORM**: MyBatis Plus with XML-based SQL to leverage DuckDB-specific analytical functions.
- **Services**:
    - `EventLogWatcherService`: Monitors directories for new logs.
    - `DiagnosisService`: Generates Markdown-based performance reports.
    - `StageService`/`JobService`: Handle core metrics and aggregations.

### 2. Frontend (Vue 3 + Vite + TypeScript)
- **UI Framework**: Custom styled components with a focus on "Classic Spark UI" parity but with enhanced interactivity.
- **Visualization**: **ECharts** for timelines, distributions, and DAGs.
- **Navigation**: SPA with deep linking; uses `/api` proxying to the backend (Port 8081).
- **Lazy Loading**: Tabs in `AppDetail.vue` (Diagnosis, Executors, Environment) are loaded on-demand to ensure fast initial page response.

## 📂 Key File Map
- `src/main/java/com/spark/insight/parser/JacksonEventParser.java`: The heart of the log ingestion.
- `src/main/resources/db/schema.sql`: DuckDB table definitions.
- `src/main/resources/mapper/*.xml`: Analytical SQL queries.
- `frontend/src/views/AppDetail.vue`: Main dashboard layout and tab management.
- `frontend/src/components/stage/StageTable.vue`: Complex data table for Stage metrics.

## 🛠 Development Guidelines for Gemini

### Backend Rules
1. **Virtual Threads**: Always prefer virtual threads for background processing via `Executors.newVirtualThreadPerTaskExecutor()` or Spring's `@Async` (when enabled).
2. **DuckDB SQL**: Use standard SQL. Avoid complex transactions as DuckDB is optimized for analytical throughput rather than ACID compliance.
3. **Log Handling**: When adding new Event handlers in `JacksonEventParser`, ensure they are null-safe as Spark logs can be inconsistent across versions.

### Frontend Rules
1. **Composition API**: Use `<script setup>` with Vue 3 Composition API.
2. **Performance**: Avoid monolithic data fetching in `onMounted`. Use the implemented `fetchDataForTab` pattern for new tabs.
3. **Styling**: Maintain the clean, data-heavy "Monitoring" aesthetic. Use established CSS variables for colors.

## 📈 Performance Patterns
- **Batch Processing**: Logs are parsed in batches of 1000 tasks before being flushed to DuckDB.
- **Pre-calculation**: High-level metrics (P95, Median) are calculated post-parsing to ensure fast UI rendering.

## 🔗 Common Workflows
- **Running Dev**: `mvn clean install` (builds both FE and BE) -> `java -jar ...`
- **Frontend Only Dev**: `cd frontend && npm run dev` (Vite will proxy to `localhost:8081`).

## 🎯 Roadmap Highlights
- [ ] **Structured Storage Engine**: Moving towards a more robust streaming ingestion.
- [ ] **Deep Comparator**: Implementing full cross-app and cross-stage benchmarking logic.
- [ ] **DAG Visualization**: Adding X6 or similar for Job/Stage relationship graphs.
