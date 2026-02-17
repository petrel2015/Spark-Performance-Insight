# 文档索引

[English](../en/index.md) | [中文](./index.md)

欢迎查看 Spark-Performance-Insight 技术文档。以下是详细的技术规格和设计文档。

## 📖 技术规格

*   **[系统架构设计](./Architecture.md)**：高层设计、奖章架构 (Medallion) 及系统数据流。
*   **[数据库存储设计](./Database_Design.md)**：DuckDB 表结构、奖章架构各层定义及内存保护机制。
*   **[Application 导入状态机](./Application_Import_State_Machine.md)**：应用从发现到分析成功的详细生命周期。
*   **[EventLog 技术参考](./EventLog_Reference.md)**：支持的日志格式、命名规范及压缩类型。

## 🛠 项目结构
*   **后端**: Java 21 + Spring Boot 3.x + DuckDB。
*   **前端**: Vue 3 + Vite + TypeScript + ECharts。
