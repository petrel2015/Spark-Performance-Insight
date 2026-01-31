# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- **Zstd Support**: Added native support for reading `.zstd` and `.zst` compressed Spark event logs using `zstd-jni`.
- **Recursive Log Scanning**: The `LogScannerRunner` now recursively scans subdirectories in the configured event log path, enabling support for Spark's V2 event log directory structure.
- **Spark V2 Log Compatibility**: 
    - Enhanced `JacksonEventParser` to robustly extract `App ID` from `SparkListenerEnvironmentUpdate` events when `SparkListenerApplicationStart` is missing or delayed.
    - Added comprehensive null-safety checks for optional fields in Task Metrics and Stage Info to prevent crashes during parsing.
- **Robustness**: Improved error handling during the log parsing process to prevent the entire application from crashing due to a single malformed file.

### Fixed
- **Database Lock Issue**: Resolved startup failures caused by stale database locks when previous instances weren't shut down cleanly.
- **Duplicate Key Error**: Fixed a bug where a missing App ID led to `null:driver` primary key violations in the `executors` table.
