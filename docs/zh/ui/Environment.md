# UI: 环境配置

[English](../../en/ui/Environment.md) | [中文](./Environment.md)

环境页面列出了所有 Spark 配置属性、系统属性和类路径（Classpath）信息，用于排查配置诱发的性能问题。

## 📊 可见信息
- **Spark 属性**：`spark.executor.memory`, `spark.sql.shuffle.partitions` 等。
- **系统属性**：JVM 版本、操作系统详情和环境变量。
- **类路径**：所有已加载的 JAR 包和库列表。
- **搜索与过滤**：能够快速查找特定键（如 "memory" 或 "dynamicAllocation"）。

## 🔍 数据来源
- **核心表**：`gold_environment_configs` (DuckDB)。
- **来源事件**：解析自 `SparkListenerEnvironmentUpdate` 事件。

## 🖼 截图预留
![环境配置](../../img/ui_environment.jpg)
