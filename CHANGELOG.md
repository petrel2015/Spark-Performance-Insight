# Changelog

English | [中文](./CHANGELOG.zh.md)

All notable changes to this project will be documented in this file.

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