# Spark-Performance-Insight

[English](./README.md) | 中文

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](./CHANGELOG.zh.md)
[![AI Powered](https://img.shields.io/badge/Powered%20by-Gemini%20AI-blue.svg)](https://deepmind.google/technologies/gemini/)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)

---

一个专为解决原生 Spark Web UI/History Server 核心痛点而生的深度性能分析系统。通过 **奖章架构**、**智能诊断** 以及 **多维深度对标** 技术，彻底告别“重放慢”、“信息过载”与“无对比”的性能诊断困境。

> **💡 核心目标：将原始日志转化为“行动建议”，直接给出答案，而非堆砌数据。**

---

## 为什么需要它？

虽然 Spark 原生 Web UI 提供了基础监控，但在实际的性能调优和生产运维中，用户常面临以下障碍：

### 1. 指标繁杂且展示晦涩
- **指标迷宫：** 原生 UI 罗列了海量的原始指标和并不直观的图表。新手很难从中快速定位关键信息，即使是经验丰富的老手，也需要反复检索和跳转页面来关联各项数据。
- **洞察埋没：** 诸如 GC 压力、数据倾斜等核心瓶颈往往隐藏在多层菜单下，无法让用户在数秒内对应用的“健康状况”产生全局认识。

### 2. 缺乏有效的横向对比
- **差异难以量化：** 当作业运行变慢时，原生 UI 无法直接对比两个作业实例（如“今天”与“昨天”）的指标差异，难以一眼看出是哪个 Stage 或 Task 发生了退化。
- **排障如同盲拆：** 难以快速识别性能波动是由参数微调、资源波动还是底层硬件差异引起的，缺乏直观的对标工具。

### 3. History Server 的性能瓶颈
- **重放开销巨大：** 依赖原始 JSON 日志的回放。处理大型作业时，TB 级日志会导致 SHS 响应极慢，甚至因内存不足（OOM）而崩溃。
- **查询效率低下：** 线性存储不支持高效索引，在数万个 Stage 或百万级 Task 中进行搜索和翻页体验极差。

---

## 核心功能

### 1. 智能化 AI 诊断
- **深度瓶颈分析：** 集成 **智谱 AI (GLM-4.7)** 与 **OpenAI**，针对具体的性能瓶颈（如 Shuffle IO、GC 严重等）一键生成专家级诊断报告。
- **针对性优化建议：** AI 根据应用实际运行指标，提供可落地的参数调优和代码重构建议。

![智能化诊断](docs/img/pic1.jpg)

### 2. 规则诊断引擎
- **统计学精度：** 不同于大模型的概率性输出，规则引擎基于严谨的统计阈值，提供确定、稳定且极高准确性的分析结果。
- **专家启发式规则：** 将多年的 Spark 性能调优经验沉淀为自动化规则，覆盖数据倾斜、Executor GC 压力、磁盘溢写（Spill）及本地化（Locality）等核心维度。
- **秒级定位根因：** 为性能退化提供即时、可量化的证据，是生产环境排障中无可争议的“黄金标准”。

![规则引擎](docs/img/pic4.jpg)

### 3. 多维度深度对比
- **跨应用实例对比：** 支持选择两个不同的 Application 实例进行全指标 side-by-side 对标，快速定位配置或环境带来的偏差。
- **Stage 专项对标：** 深入对比两个 Stage 的指标分布（P95, Median, Min, Max）及 Task 执行轨迹，揪出隐藏的长尾任务。

![多维度对比](docs/img/pic2.jpg)

### 3. 经典视图复刻与增强
- **无缝迁移体验：** 深度复刻原生 Spark UI 的 Jobs, Stages, Tasks 列表展示及逻辑描述，让开发人员无需学习即可快速上手。
- **增强统计面板：** 提供所有核心指标的详细五分位数分布，并为百万级 Task 提供极速的后端分页与多列组合排序。

![经典视图复刻](docs/img/pic3.jpg)

### 4. 奖章架构数据管道
- **高效入库 (Bronze)：** 针对大数据量场景，采用基于 Jackson 的超高速流式解析，轻松处理 TB 级日志。
- **规范化处理 (Silver)：** 自动化数据建模，恢复逻辑关联，确保在海量数据下也能精准提取特征。
- **极速响应 (Gold)：** 预计算分析宽表存储于 **DuckDB**，支撑秒级的复杂聚合查询与 UI 渲染。

### 5. 系统鲁棒性与兼容性
- **自动容错：** 针对 DuckDB 实现 OOM 自动检测与重试机制，确保在大规模数据处理时的稳定性。
- **精准时间同步：** 统一毫秒级时间戳与单调递增的进度汇报，消除了跨时区环境下的导入时间估算偏差。
- **广泛兼容：** 原生支持 **ZSTD** 压缩和 Spark **V2 目录式日志**。

## 📖 文档与架构

如需深入了解系统设计与技术规格，请参阅我们的结构化文档：

*   **[技术文档索引](./docs/zh/index.md)**
    *   [系统架构设计](./docs/zh/Architecture.md)
    *   [数据库存储设计](./docs/zh/Database_Design.md)
    *   [Application 导入状态机](./docs/zh/Application_Import_State_Machine.md)
    *   [EventLog 技术参考](./docs/zh/EventLog_Reference.md)

## 技术栈

- **前端：** Vue 3 + Vite + ECharts + Material Design。
- **后端：** Java 21 (虚拟线程) + Spring Boot 3.x。
- **OLAP 引擎：** [DuckDB](https://duckdb.org/) (进程内嵌入式分析型数据库)。
- **ORM：** MyBatis Plus (基于 XML 优化分析型 SQL)。

## 快速开始

### 开发调试模式 (Maven 本地运行)

1.  **构建并启动：**
    ```bash
    mvn clean install -Pbuild-frontend
    mvn spring-boot:run
    ```

2.  **访问界面：**
    在浏览器中打开 `http://localhost:18081`。

### 全自动构建与启动 (Docker 托管)

```bash
mvn clean install -Pbuild-frontend -Prun
```

### 生产与对比模式 (Docker Compose)

该方式会同时启动 **本项目 UI** 和 **原生 History Server** 共享同一日志目录。

1.  **启动服务：**
    ```bash
    docker compose up -d
    ```

2.  **访问地址：**
    -   **本项目 UI:** `http://localhost:18081`
    -   **Spark History Server:** `http://localhost:18080`

## 致谢

- 特别感谢 [JimLiu/baoyu-skills](https://github.com/JimLiu/baoyu-skills.git) 为本项目提供自动化的 **release-skills** 发布流程支持。
