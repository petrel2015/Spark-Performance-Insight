# Changelog

English | [中文](./CHANGELOG.zh.md)

All notable changes to this project will be documented in this file.

## 0.9.0 - 2026-02-02

### Features
- **RDD Lineage V3**: Complete visual overhaul of the DAG chart.
    - **Spark UI Parity**: Matched color scheme (Scopes `#A0DFFF`, RDDs `#C3EBFF`, Edges `#444`) and style (solid borders, solid fills).
    - **Robust Rendering**: Implemented a "Flattened X6 + Dagre" engine to solve coordinate drift and missing nodes issues.
    - **Smart Loading**: Added an intelligent loading spinner with debounce logic to handle card expansion/collapse smoothly.
- **Event Timeline V2**: 
    - **Dynamic Sizing**: Row height increased to 50px for readability; container height now auto-scales with executor count.
    - **Compact Layout**: Reduced vertical padding and removed redundant bottom status bars.
    - **Integrated Legend**: Legends are now embedded within the card headers, maintaining a cleaner UI.

### Bug Fixes
- **Graph Visibility**: Fixed a critical bug where graphs would disappear or misalign when collapsing/expanding cards.
- **DataZoom Sync**: Resolved performance issues by decoupling the zoom linkage between trend and gantt charts.

## 0.8.0 - 2026-02-02

### Features
- **Advanced Event Timeline**: Completely refactored the timeline into a dual-chart view.
    - **Active Tasks Trend**: A stacked area chart showing concurrency per executor over time.
    - **Enhanced Gantt Chart**: Grouped by executor with 8-color metric breakdown (Scheduler Delay, GC, Computing, etc.).
    - **Smart Interaction**: Unified crosshair pointers and floating tooltips with shared styles.
- **RDD Lineage V2**: Enhanced the DAG visualization with Scope grouping (nesting RDDs within operators like WholeStageCodegen) and professional layout via Dagre.
- **Interactivity Lock**: Introduced a global zoom lock mechanism for all complex charts (Timeline, DAG) to prevent accidental zooming during page scrolling.
- **Universal Duration Sorting**: Fixed sorting by duration in both Job and Stage lists by persisting calculated duration in the database and optimizing SQL generation.
- **Unified UI Componentry**: Moved action buttons (like Zoom Lock) into standard `CollapsibleCard` header slots for a cleaner look.

### Improvements
- **Layout Precision**: Standardized all grid margins and synchronized axis alignment across sub-charts.
- **Dynamic Chart Sizing**: Improved row height calculation (50px/executor) and responsive width handling using `ResizeObserver`.

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