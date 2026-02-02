# Changelog

English | [中文](./CHANGELOG.zh.md)

All notable changes to this project will be documented in this file.

## 0.7.0 - 2026-02-02

### Features
- **RDD DAG Visualization**: Implemented a transformation graph for stages using AntV X6 and Dagre. Supports RDD lineage, operation scope grouping (e.g., WholeStageCodegen), and call site display.
- **Enhanced Stage Summary**: Integrated Stage Overview into the Summary Metrics matrix. Added a `Total` column showing aggregated resource consumption (sums/max) for all metrics.
- **Improved Metric Selection**: Refactored the metric selector with a responsive grid layout. Columns are now equal-width and adaptively adjust their count based on screen resolution.
- **Full Metric Coverage**: Supplemented missing metrics (Output, Shuffle Read) and unified naming conventions across all views.
- **Backend Analytics Upgrade**: Enhanced the Stage analytics engine to calculate 10+ additional aggregated metrics during log parsing.

### Bug Fixes
- **UI Resilience**: Fixed a critical `TypeError` when navigating to details before data is loaded.
- **Layout Consistency**: Restored missing CSS styles and ensured proper text wrapping for long metric labels.

## 0.6.0 - 2026-02-01

### Features
- **Job Detail View**: Implemented a dedicated view for Jobs with aggregated executor metrics and strictly filtered Stage lists.
- **Environment Overhaul**: Comprehensive coverage of Spark, Hadoop, JVM, System properties, and Classpath with independent search and collapsible cards.
- **Executor View V2**: Added 15+ metrics (Peak Memory, Storage details, Disk used) and lifecycle tracking (Add/Remove Time).
- **Stage List Enhancements**: Added Submitted, Duration, and I/O metrics; introduced task progress visualization (Succeeded/Total progress bar).
- **Advanced Sorting**: Uniform multi-column sorting UI with active-sorts-bar across all data tables (Jobs, Stages, Executors, Tasks, Summary Metrics).
- **Deep Linking**: URL state persistence for all tabs (Jobs, Stages, Executors, etc.) via dedicated sub-paths.
- **Improved Data Accuracy**: Corrected Task success identification and enhanced Job descriptions using call site info.
- **UI Consistency**: Standardized link styles (blue, no underline), unified header names, and zero-value masking (`-`).

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