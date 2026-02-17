## 1.0.0 - 2026-02-17

### Features
- **Medallion Architecture**: Fully transitioned to Medallion Architecture (Bronze/Silver/Gold) for high-speed log ingestion and analysis.
- **Storage Analysis**: Complete overhaul of the Storage tab with support for RDD/DataFrame persistence analysis, structural storage level display, and deep-link support for individual RDDs.
- **Robust Ingestion**: Improved handling of large logs with chunked ingestion, real-time progress tracking based on file stream position, and automated OOM recovery using DuckDB `CHECKPOINT`.
- **UI Enhancements**: Added support for column filtering, multi-column sorting, inline notes editing, and persistent list settings in the Application List.
- **Log Support**: Robust AppId extraction and support for ZSTD compression and V2 log directories.

### Fixes
- **Timing Accuracy**: Resolved timezone-related timing errors and inaccurate remaining time estimation using synchronized epoch milliseconds and monotonic progress reporting.
- **Stability**: Fixed DuckDB primary key constraint errors and implemented automatic memory release on OOM.
- **Layout**: Fixed layout distortions caused by long RDD names and overlapping tooltips in performance diagnosis cards.

## 0.35.0 - 2026-02-13

### Features
- **Lifecycle Logging**: Introduced a dedicated `application_logs` table to track every stage of an application's lifecycle (Scan, Import, Parse, Finalize, Success/Fail).
- **Performance Tracing**: Added detailed timing logs for each step of the metric aggregation process.

### Fixes
- **Visualizations**: Restored missing Stage DAG and SQL Physical Plan metadata that was lost in a recent refactor.
- **Incremental Parsing**: Implemented MD5-based change detection to avoid redundant re-import prompts when no changes are detected.
- **UI Alignment**: Fixed misalignment in the Application List table when Compare Mode is enabled.

## 0.34.1 - 2026-02-13

### Fixes
- Resolve data loss in tasks table and incorrect performance scores

## 0.34.0 - 2026-02-12

### Features
- **Real-time Status**: Implemented WebSocket support for real-time application status and parsing progress updates
- **Overwrite Confirmation**: Added manual confirmation flow for re-importing existing applications when new logs are detected
- **UI Overhaul**: Redesigned Application List with progress bars, status badges, and improved horizontal scrolling

### Refactor
- **Event Parsing**: Optimized JacksonEventParser to use total log size for precise 0-100% progress tracking
- **Service Layer**: Decoupled overwrite logic into ApplicationOverwriteService
- **Code Style**: Cleaned up imports and simplified MyBatis Plus query wrappers across the project

## 0.33.0 - 2026-02-12

### Refactor
- **Event Scanning**: Enhanced EventLogWatcherService with MD5-based change detection and App ID grouping for more efficient incremental parsing
- **Parsing Progress**: Implemented dynamic progress tracking in JacksonEventParser by querying DuckDB for real-time status updates
- **Interface Cleanup**: Simplified EventParser interface by removing redundant file counters and incorporating pre-inferred App IDs
- **User Experience**: Improved log readability with human-readable file sizes and percentage-based progress tracking

### Database
- **Schema Update**: Added `app_id` column to `parsed_event_logs` to better track the relationship between log files and Spark applications

## 0.32.1 - 2026-02-12

### Refactor
- simplify lambda expressions and optimize app ID inference in EventLogWatcherService
- optimize configuration comparison with configurable filters and Optional-based refactoring

## 0.32.0 - 2026-02-12

### Features
- **OpenAI Support**: Add OpenAI support and refactor LLM client integration

## 0.31.0 - 2026-02-11

### Features
- **Log4j2 Migration**: Migrate to Log4j2 and improve parsing error logging
- **Import Status**: Implement IMPORTING status when app_id is detected and update EventLogStatus enum
- **Schema Refactor**: Refactor ParsedEventLog table and model to use fileName as PK and EventLogStatus enum
- **Parsing Progress**: Include byte counts and percentage in parsing progress message

### Fixes
- **Stability**: Resolve Log4j2 stack trace and DuckDB type mapping issues

### Refactor
- **Consistency**: Rename columns in parsed_event_logs for consistency

## 0.30.0 - 2026-02-10

### Features
- **Parser Progress**: Implemented byte-based percentage tracking and post-calculation status updates for better log ingestion visibility

### Refactor
- **UI Search Validation**: Converted numeric ID search inputs to text type with strict non-negative integer regex validation across Jobs, Stages, and SQL views

### Maintenance
- **Docker**: Modernized Maven build profiles to use `docker compose` instead of the deprecated `docker-compose`

## 0.29.0 - 2026-02-08

### Features
- **Multi-Dimensional Comparison**: Implemented cross-application, job, and stage performance comparison
- **Environment Analysis**: Added categorical configuration diff (Spark, JVM, Hadoop) with identical item filtering
- **Unified Comparison UI**: Redesigned comparison detail cards with left-aligned layout and rich metadata (Duration, Stages, Tasks)
- **Application Comparison**: Enabled direct selection and comparison of two different Spark applications from the workspace

### Improvements
- **Data Parsing**: Enhanced App ID detection and implemented batch processing for environment configurations
- **Robustness**: Added `spring-boot-starter-jdbc` for more reliable datasource auto-configuration
- **UI Consistency**: Standardized health score badges and refined card styles across the comparison report
## 0.28.1 - 2026-02-08

### Fixes
- **Docker Compose**: Fixed environment variable mapping for API keys (using mapping format)
- **UI Tweaks**: Refined loading text in AI Diagnosis card
- **Configuration**: Updated default placeholders for sensitive keys

## 0.28.0 - 2026-02-08

### Features
- **Compare Workspace**: New staging area for performance benchmarking
- **Persistence**: Comparison candidates are now saved in browser localStorage
- **Modern Pagination**: Unified and polished pagination UI across all tables
- **Storage Fixes**: Improved RDD metadata parsing and added empty state UI
- **UI Enhancements**: Dynamic skew highlighting in Stage Summary metrics
- **Version Tracking**: Display system version in the navigation bar

## 0.27.0 - 2026-02-07

### Features
- Integrate **Zhipu AI (GLM-4.7)** for deep performance diagnosis
- Implement persistence for LLM reports with forced regeneration support
- Real-time generation timer and history tracking in UI
- Refactor Diagnosis tab with folder-style layout (Rule-Based & LLM)
- Optimize prompts for professional Chinese reports with preserved English technical terms

## 0.26.0 - 2026-02-07

### Features
- Implement Storage tab to track RDD/DataFrame persistence
- Standardize all scores to Health Score (0-100, higher is better)
- Professional Rule-Based Diagnostic Report with Material Icons
- Fix unit mismatch issues in I/O Wait calculations

## 0.25.0 - 2026-02-07

### Features
- Enhance UI for job/stage diagnosis and DAG visualization

## 0.24.0 - 2026-02-07

### Features
- Implement SQL performance scoring and enhanced diagnosis views

## 0.23.0 - 2026-02-07

### Features
- Streamline containerized deployment with Spark 4.0 and Java 21

### Fixes
- Resolve database initialization and SQL binder errors

## 0.21.0 - 2026-02-05

### Features
- Implement SQL / DataFrame tab mimicking Spark UI
- Add SQL execution list with associated Job IDs
- Add SQL detail view with physical plan and linked Jobs
- Parse SparkListenerSQLExecution events from logs

### Improvements
- Links now support opening in new browser tabs (using router-link)
- Updated TODO.md with latest task progress

## 0.20.1 - 2026-02-05

### Configuration
- Change event log scan interval to one hour (3600 seconds)

## 0.20.0 - 2026-02-05

### Features
- Ensure sequential parsing of rolling event logs for the same Spark application
- Implement concurrency control in EventLogWatcherService to prevent duplicate parsing tasks
- 在数据库中添加事件日志的 "PROCESSING" 状态追踪

### Improvements
- Refined waterfall timeline with improved lane packing logic
- Corrected RDD positioning in Job DAG visualization

## 0.19.0 - 2026-02-05

### Features
- Implement traditional event timeline with vertical flags for executor lifecycle
- Add Locality Level Summary to Stage Details
- Improve Job DAG visualization with correct nesting and styling
- Add zoom lock controls to timeline charts
- Use Material Design Icons throughout the UI
- Display percentage for time-based metrics in summary tables
- Track and log event log parsing duration

### Improvements
- Optimize /report API to use pre-calculated metrics
- Enable opening list items and tabs in new browser tabs (real links)
- Collapse Job DAG and Event Timeline cards by default
- Unified styling for Stage and Job DAGs

## 0.18.0 - 2026-02-05

### Features
- Infer App ID from event log filenames
- Add search functionality to Job and Stage lists
- Update task tracking in TODO.md

## 0.17.1 - 2026-02-04

### Performance
- Optimize backend parsing and frontend lazy loading

### Refactor
- Major overhaul of frontend components and minor backend improvements

### Documentation
- Translate TODO.md to Chinese and update environment/dependency configurations

## 0.17.0 - 2026-02-04

### Features
- Job Group Search, Graceful Parsing Handling, and Metrics Improvements (by @hongyu)
- Add Job Group search to Job List (by @hongyu)
- Add app parsing status check, progress tracking, and 503 handling with frontend notification (by @hongyu)
- Add app parsing status check and 503 response for incomplete data (by @hongyu)
- Metric Visibility Selector to Job List page (by @hongyu)
- Add metric selector to Job Detail page and support dynamic columns in StageTable (by @hongyu)
- Add sortable Job Id column to Stage List with navigation support (by @hongyu)

### Fixes
- Resolve compilation error in InsightController due to incorrect class reference (by @hongyu)
- Access .value of computed columns in JobsTab script to fix TypeErrors (by @hongyu)
- Import computed from Vue in JobsTab to fix ReferenceError (by @hongyu)
- Access .value of computed columns in StageTable script (by @hongyu)

## 0.15.0 - 2026-02-04

### Features
- Job Timeline, Rolling Logs, and Enhanced Job List (by @hongyu)
- Add Stage IDs and Stages Count columns to Job List (by @hongyu)
- Split Job ID and Job Group into separate sortable columns (by @hongyu)

### Styles
- Simplify stage group labels in Job DAG to only show Stage ID (by @hongyu)

## 0.14.0 - 2026-02-04

### Features
- Job DAG Visualization and SQL Ambiguity Fix (by @hongyu)

## 0.13.0 - 2026-02-04

### Features
- Scheduled Log Parsing, Diagnosis Thresholds, and Docker Support (by @hongyu)
- Add Dockerfile based on Bitnami Spark 3.5 and docker-compose with History Server integration (by @hongyu)

### Styles
- Widen Stage Id column to prevent wrapping with attempts/badges (by @hongyu)

## 0.12.1 - 2026-02-04

### Features
- UI Parity with Spark Web UI and Polish (by @hongyu)
- Handle long App IDs in App List by increasing column width and adding word-break styling (by @hongyu)
- Update Job ID column to 'Job ID (Job Group)' and refine badge styling (by @hongyu)
- Default job list sort by jobId DESC to align with Spark Web UI (by @hongyu)

### Fixes
- Stage details 'of Job' link now correctly navigates to Job Details instead of Job List (by @hongyu)

## 0.12.0 - 2026-02-04

### Features
- Full support for Stage Retries (Multi-attempt) (by @hongyu)

## 0.11.0 - 2026-02-04

### Features
- Proactive data quality governance and robust diagnosis reporting (by @hongyu)

## 0.10.1 - 2026-02-04

### Fixes
- Enable Jackson to parse large JSON event logs (>20MB) (by @hongyu)
- Robust null handling in DiagnosisService markdown report (by @hongyu)

## 0.10.0 - 2026-02-04

### Features
- Case-insensitive app search, new Executor Timeline, and UI normalization (by @hongyu)

## 0.9.0 - 2026-02-04

### Features
- Finalize RDD Lineage styling/stability and polish Timeline UX (by @hongyu)

## 0.8.0 - 2026-02-04

### Features
- Advanced Event Timeline with concurrency trend, RDD Lineage V2, and global interaction locks (by @hongyu)

## 0.7.0 - 2026-02-04

### Features
- Implement RDD DAG visualization, enhanced stage summary, and responsive UI grid (by @hongyu)

## 0.6.0 - 2026-02-04

### Features
- complete v0.6.0 with Job Details, advanced sorting, and full UI normalization (by @hongyu)
- enhance Job metadata with call site and complete Job Detail view (by @hongyu)

### Styles
- unify job list link style with stage list (by @hongyu)

### Fixes
- correctly handle job description with stage name fallback and fix compilation (by @hongyu)
- correctly link stages to jobs in job detail view (by @hongyu)

## 0.3.0 - 2026-02-04

### Features
- comprehensive upgrade of Job/Stage/Environment views and UI normalization (by @hongyu)
- enhance stage details with executor aggregation and collapsible cards (by @hongyu)

## 0.2.0 - 2026-02-04

### Features
- enhance stage summary metrics to match Spark UI parity (by @hongyu)

### Fixes
- resolve API_BASE reference error and missing components (by @hongyu)

## 0.1.0 - 2026-02-04

### Features
- enhance job/stage analysis and UI parity with Spark History Server (by @hongyu)
- enable multi-column sorting in task table (by @hongyu)
- Implemented automatic frontend packaging into the JAR using the frontend-maven-plugin. (by @hongyu)

### Refactor
- modularize stage details and enhance environment tab (by @hongyu)

### Documentation
- generate v0.1.0 changelogs and add acknowledgments (by @hongyu)
