# Documentation Index

[English](./index.md) | [中文](../zh/index.md)

Welcome to the Spark-Performance-Insight documentation. Below you will find detailed technical specifications and design documents.

## 📖 Technical Specifications

*   **[System Architecture](./Architecture.md)**: High-level design, Medallion architecture, and system data flow.
*   **[Database Design](./Database_Design.md)**: DuckDB schema, Medallion layers (Bronze/Silver/Gold), and memory protection.
*   **[Application Import State Machine](./Application_Import_State_Machine.md)**: Detailed lifecycle of an application from discovery to successful analysis.
*   **[EventLog Reference](./EventLog_Reference.md)**: Supported log formats, naming conventions, and compression types.

## 🛠 Project Structure
*   **Backend**: Java 21 + Spring Boot 3.x + DuckDB.
*   **Frontend**: Vue 3 + Vite + TypeScript + ECharts.
