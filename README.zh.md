# Spark-Performance-Insight

[English](./README.md) | 中文

[![AI Powered](https://img.shields.io/badge/Powered%20by-Gemini%20AI-blue.svg)](https://deepmind.google/technologies/gemini/)

--- 

一个专为解决原生 Spark Web UI/History Server 核心痛点而生的深度性能分析系统。它旨在通过结构化存储与多维对比技术，彻底解决 Spark 性能诊断中的“重放慢”与“无对比”问题。

## 为什么需要它？ (The Pain Points)

虽然 Spark 原生 Web UI 提供了基础监控，但在深度性能分析和生产运维中存在以下核心痛点：

### 1. History Server 的架构性能瓶颈
- **重放负担 (Event Replay Overhead)：** 原生 History Server 依赖回放原始 EventLog 来重建作业状态。对于大型作业，TB 级的日志回放会导致极高的 CPU 与内存开销，访问响应极慢。
- **内存饥饿与扩展性：** 缺乏预聚合的结构化存储，所有指标都需缓存在内存中，在处理海量 Task 的超大型作业时极易引发 SHS 进程 OOM 或 UI 崩溃。
- **查询无索引：** 线性存储格式不支持高效的索引、搜索和分页。在数万个 Stage 或百万级 Task 中进行翻页、过滤或排序时，体验极其糟糕。

### 2. 缺乏深度的横向对比能力 (No Job/Stage Comparison)
- **无法量化差异：** 当作业性能出现退化时，原生 UI 无法直接跨作业对比 Job 或 Stage 的指标差异。用户难以直观看到是哪个特定的 Stage 变慢了，以及 Task 分布发生了什么变化。
- **环境变动盲区：** 用户难以快速识别性能下降是因为 `spark.conf` 参数的变动、Executor 资源配置差异，还是由于底层硬件环境导致的。

## 核心功能模块 (Features)

### 1. 经典视图复刻与增强 (Classic UI Parity & Beyond)
- **Jobs & Stages Explorer:** 深度复刻原生列表，包含 **Job 描述**、进度条展示，支持毫秒级排序与过滤。
- **Summary Metrics:** 完美复刻 Spark UI 的 Stage 统计面板。提供 Duration, GC Time, Spill, Shuffle 等指标的五分位数（Min, 25%, Median, 75%, 95%, Max）统计分布。
- **高性能 Task 列表:** 支持百万级 Task 的后端分页、自定义每页条数、以及 **多列同时排序**（按住 Shift 多选）。
- **SPA 路由持久化:** 全面支持页面刷新与 URL 直接访问。可直接分享特定的 Stage 详情链接。

### 2. 智能化诊断引擎 (Smart Diagnosis)
- **Markdown 报告生成:** 自动分析数据倾斜、GC 压力、磁盘溢写等，生成 LLM 友好的诊断报告。
- **直观风险标识:** 在 Stages 列表中自动通过颜色标记高风险阶段。

### 3. 多维度深度对比 (Job/Stage Comparison)
- **跨应用 Job 对比：** 支持选择两个 Job（可属于不同的 Application 实例），系统会自动识别并关联对比对应的各 Stage 性能指标。
- **Stage 专项分析：** 支持直接选择两个 Stage 进行深度对标。包括：
    - **统计分布对比：** Duration, GC Time, Shuffle Read/Write 等指标的 Min, Median, Max, P95 分布差异。
    - **Task 细节对比：** 自动识别长尾 Task，对比两个 Stage 间 Task 执行轨迹的细微差别。

### 4. 广泛的日志兼容性 (Broad Log Compatibility)
- **Zstd 压缩支持:** 原生支持读取 `.zstd` 和 `.zst` 格式的压缩日志，无需手动解压。
- **Spark V2 日志结构:** 支持递归扫描目录，完美兼容 Spark V2 的目录式 EventLog 存储结构。
- **鲁棒性解析:** 针对不同版本的日志格式缺失字段进行自动兼容，确保解析过程稳定不崩溃。

## 技术栈 (Technical Stack)

## 技术栈 (Technical Stack)

- **前端 (Frontend):** Vue 3 + Vite + ECharts (用于全链路耗时与资源分布可视化)。
- **后端 (Backend):** Java 21 + Spring Boot 3.x (利用虚拟线程提高日志解析效率)。
- **数据库 (Database):** DuckDB (进程内嵌入式 OLAP 数据库，高性能处理分析型查询)。
- **持久层 (ORM):** MyBatis Plus (采用 XML 编写 SQL，便于后续数据库扩展与 SQL 优化)。
- **运行模式:** 独立 JAR 包运行，支持离线分析。

## 工作流程 (Workflow)

1.  **扫描 (Scan):** 启动时扫描配置文件指定的 `eventlog` 路径。
2.  **导入 (Import):** 采用**“双轨制”解析策略**：
    - **当前：FastEventParser** - 基于 Jackson/Fastjson 异步解析原始 JSON，提取核心指标入库。轻量级、无 Spark 依赖。
    - **预留：SparkNativeParser** - 定义统一接口，预留未来集成 `spark-core` 回放总线（ReplayListenerBus）的能力，以支持不同版本的原生解析。
3.  **初始化预计算 (Pre-Calculate):**
    - 在入库阶段自动计算 Stage/Task 级别的关键指标。
    - 提前聚合：GC 时间占比、平均数、中位数 (P50)、P90/P95/P99 耗时等。
4.  **展示与对比 (Analyze):** 通过 Web UI 提供单任务深度分析与多任务横向对比。

## 主要功能规划 (Roadmap)

- [ ] **Structured Storage Engine:** 实现 EventLog 的流式解析与结构化入库，彻底告别原始日志重放。
- [ ] **Multi-Version Event Parser:**
    - [x] 基于 Jackson 的快速解析器实现。
    - [ ] 插件化解析接口设计（支持自定义 Event 映射）。
    - [ ] 预留原生 Spark Jar 回放适配器。
- [ ] **Stage Summary Metrics:**
    - [ ] 实现类似 Spark UI 的 Task 统计面板。
    - [ ] 提供 Duration, GC Time, Shuffle Read/Write, Input Bytes 等指标的 Min, 25%, Median, 75%, 95%, Max 统计分布。
- [ ] **Deep Comparator:** 支持 Job 和 Stage 维度的全量对比。
    - **跨 App 对标：** 自动匹配不同运行实例间的 Job 逻辑关系。
    - **指标下钻：** 从 Stage 概览一键下钻至具体的 Task 差异对比。
    - **参数偏差：** 自动高亮显示 Environment 与 SparkConf 的差异。

## 快速开始

### 构建与运行

1.  **构建项目:**
    该命令会自动构建前端（在本地安装 Node.js/NPM）并将其打包到 JAR 文件中。
    ```bash
    mvn clean install
    ```

2.  **启动应用:**
    ```bash
    java -jar target/spark-performance-insight-0.0.1-SNAPSHOT.jar
    ```

3.  **访问界面:**
    打开浏览器访问:
    ```
        http://localhost:8081
        ```
    
    ## 致谢
    
    - 特别感谢 [JimLiu/baoyu-skills](https://github.com/JimLiu/baoyu-skills.git) 项目及作者，为本项目提供自动化的 **release-skills** 发布流程支持。
    