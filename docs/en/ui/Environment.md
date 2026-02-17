# UI: Environment Configuration

[English](./Environment.md) | [中文](../../zh/ui/Environment.md)

The Environment page lists all Spark configuration properties, system properties, and classpath information for troubleshooting configuration-induced performance issues.

## 📊 Information Visible
- **Spark Properties**: `spark.executor.memory`, `spark.sql.shuffle.partitions`, etc.
- **System Properties**: JVM version, OS details, and environmental variables.
- **Classpath**: List of all loaded JARs and libraries.
- **Search & Filter**: Ability to quickly find specific keys (e.g., "memory" or "dynamicAllocation").

## 🔍 Data Source
- **Primary Table**: `gold_environment_configs` (DuckDB).
- **Source Event**: Parsed from `SparkListenerEnvironmentUpdate`.

## 🖼 UI Preview
![Environment](../../img/ui_environment.png)
