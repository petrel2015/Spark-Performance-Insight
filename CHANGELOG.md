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
- Add "PROCESSING" status tracking for event logs in the database

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
