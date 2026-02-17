# Spark-Performance-Insight

[English](./README.md) | 中文

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](./CHANGELOG.zh.md)
[![AI Powered](https://img.shields.io/badge/Powered%20by-Gemini%20AI-blue.svg)](https://deepmind.google/technologies/gemini/)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

---

一个专为解决原生 Spark Web UI/History Server 核心痛点而生的深度性能分析系统。通过 **奖章架构 (Medallion Architecture)**、**结构化 OLAP 存储** 以及 **多维深度对标** 技术，彻底告别“重放慢”与“无对比”的性能诊断困境。

## 为什么需要它？

虽然 Spark 原生 Web UI 提供了基础监控，但在生产环境的深度分析中存在以下关键局限：

### 1. History Server 的架构瓶颈
- **重放开销大：** 依赖顺序回放原始 JSON EventLog。对于大型作业，TB 级日志会导致极高的 CPU/内存开销和长达数分钟的等待。
- **扩展性挑战：** 缺乏结构化存储，所有指标必须缓存于内存，处理海量 Task 时极易引发 OOM 或 UI 崩溃。
- **查询效率低：** 线性存储不支持索引。在数万个 Stage 或百万级 Task 中进行搜索、排序和分页时体验极差。

### 2. 缺乏量化的横向对比
- **差异难以量化：** 当作业变慢时，很难直接对比不同运行实例或 Stage 间的指标，难以精准定位性能退化的根源。
- **环境变动盲区：** 难以快速识别性能波动是由 `spark.conf` 参数微调、资源分配差异还是硬件环境变化引起的。

## 核心功能

### 1. 奖章架构数据管道 (Medallion Pipeline)
- **Bronze (原始入库)：** 基于 Jackson 的超高速流式解析，轻松应对 TB 级原始日志。
- **Silver (结构化转换)：** 数据规范化处理，自动恢复逻辑关联，精准识别长尾 Task。
- **Gold (指标聚合)：** 预计算分析宽表，即使面对海量数据也能实现秒级 UI 响应。

### 2. 深度存储分析 (Storage Overhaul)
- **持久化追踪：** 全方位展示缓存 RDD 和 DataFrame 的分布情况。
- **结构化 UI：** 通过状态指示灯直观展示存储级别（Memory/Disk/Deserialized）。
- **深层链接：** 每个 RDD 拥有独立 URL，支持直接访问和快速分享。

### 3. 智能化诊断引擎 (Smart Diagnosis)
- **AI 深度分析：** 集成 **智谱 AI (GLM-4.7)** 与 **OpenAI**，一键生成专家级诊断报告。
- **规则引擎：** 自动识别数据倾斜、GC 压力、磁盘溢写和调度延迟，并提供视觉风险预警。

### 4. 多维度深度对比 (Benchmarking)
- **跨应用对比：** 支持两个不同 Application 实例间的全指标对标。
- **Stage 专项对标：** 深入对比两个 Stage 的五分位数（P95, Median）分布及 Task 执行轨迹。

### 5. 企业级稳定性
- **OOM 自动恢复：** 引入 DuckDB `CHECKPOINT` 机制与自动重试逻辑，确保内存瓶颈下的任务可靠性。
- **精准时间预估：** 统一使用毫秒级时间戳，配合单调递增的进度追踪，提供准确的导入剩余时间预估。
- **广泛兼容：** 原生支持 **ZSTD** 压缩和 Spark **V2 目录式日志**。

## 技术栈

- **前端：** Vue 3 + Vite + ECharts + Material Design。
- **后端：** Java 21 (虚拟线程) + Spring Boot 3.x。
- **OLAP 引擎：** [DuckDB](https://duckdb.org/) (进程内嵌入式分析型数据库)。
- **ORM：** MyBatis Plus (基于 XML 优化分析型 SQL)。

## 快速开始

### 构建与运行

1.  **构建项目：**
    该命令会自动构建前端并将其打包至可执行 JAR。
    ```bash
    mvn clean install
    ```

2.  **启动应用：**
    ```bash
    java -jar target/spark-performance-insight-1.0.0.jar
    ```

3.  **访问界面：**
    在浏览器中打开 `http://localhost:18081`。

## 功能规划 (Roadmap)

- [x] **奖章架构：** 已完成 Bronze/Silver/Gold 存储引擎实现。
- [x] **LLM 诊断：** 已集成 AI 深度分析。
- [x] **存储模块升级：** 已支持结构化标签与深层链接。
- [ ] **DAG 视图增强：** 提供更丰富的 Job/Stage 关联交互图谱。
- [ ] **实时流式解析：** 支持对运行中任务的近实时监控。

## 致谢

- 特别感谢 **smart-commit** 和 **release-skills** 工具的作者，显著提升了本项目的开发与发布效率。
