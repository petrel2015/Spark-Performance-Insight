# Changelog

English | [中文](./CHANGELOG.zh.md)

All notable changes to this project will be documented in this file.

## 0.5.0 - 2026-02-01

### Features
- **Job Detail View**: Dedicated view for Job details with job-level executor metrics and filtered Stage list
- **Environment Overhaul**: categorized sections (Runtime, Spark, Hadoop, System, Metrics, Classpath) with search and independent collapsible cards
- **Stage List V2**: Added Submitted, Duration, Input/Output, and Shuffle columns with capitalized headers
- **Progress Visualization**: Succeeded/Total task progress bar in Stage and Job lists
- **Call Site Info**: Job descriptions now include call site details (e.g., `csv at File.scala:10`) via `spark.job.callSite`
- **Spark Version Badge**: Show Spark version in navigation bar and application list
- **Enhanced Data Handling**: Improved Task success identification and zero-value masking (`-` instead of `0`)
- **UI Consistency**: Uniform multi-column sorting and hover highlights across all data tables

## 0.4.0 - 2026-02-01

### Features
- Add **Aggregated Metrics by Executor** table for per-executor analysis
- Add **Metric Visibility Selector** to dynamically toggle columns/rows across components
- Implement **Collapsible Cards** for all modules in Stage detail view
- Link Stage to its parent Job with clickable breadcrumb in header
- Support exact and readable record counts (e.g., `1,234 (1.2 K)`)
- Refactor navigation layout and styling to align with Spark Web UI parity
- Fix task duration and scheduler delay calculation for better accuracy

## 0.1.0 - 2026-02-01

### Features
- Enable multi-column sorting in task table
- Enhance job/stage analysis and UI parity with Spark History Server
- Implement automatic frontend packaging into the JAR

### Refactor
- Modularize stage details and enhance environment tab