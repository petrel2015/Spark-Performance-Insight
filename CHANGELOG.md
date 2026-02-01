# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added
- **Multi-column Sorting**: Enabled multi-column sorting in the Task List view. Users can now hold `Shift` to sort by multiple fields (e.g., Duration then GC Time). Added visual indicators for sort priority and controls to manage active sorts.
- **Integrated Frontend Build**: Added `frontend-maven-plugin` and `maven-resources-plugin` to automate the frontend build process during `mvn package`. The UI is now automatically bundled into the JAR and served at `http://localhost:8081`.
- **Job/Stage Comparison**: Enhanced documentation and planning for a deeper comparison engine that supports cross-app Job alignment and Stage/Task-level statistical benchmarking.
- **Zstd Support**: Added native support for reading `.zstd` and `.zst` compressed Spark event logs using `zstd-jni`.
... (existing entries) ...

### Changed
- **Build Workflow**: Simplified the build process to a single `mvn clean install` command.
- **Project Structure**: Added a `.gitignore` specifically for the `frontend` directory to maintain a clean repository state.

### Fixed
- **Build Stability**: Bypassed a `vue-tsc` compatibility issue during the frontend build to ensure reliable JAR creation in diverse environments.
- **Database Lock Issue**: Resolved startup failures caused by stale database locks when previous instances weren't shut down cleanly.

