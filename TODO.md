# Spark-Performance-Insight 任务追踪 (TODO List)

## 1. 后端核心逻辑 (Backend Core)
- [x] 项目基础骨架搭建 (Java 17 兼容版, Spring Boot 3, DuckDB, MyBatis Plus)
- [x] 数据库 Schema 设计与自动初始化
- [x] `LogScannerRunner` 实现 (支持并发扫描)
- [x] `JacksonEventParser` 深度实现 (解析 App, Job, Stage, Task, Executor 指标)
- [x] DuckDB 预计算逻辑实现 (Stage 级别的 P95/P99、GC 占比、倾斜判定)
- [ ] **Stage 统计指标增强**: 计算 Task 的 Min/25%/50%/75%/95%/Max 分布 (Duration, GC, Shuffle, Input)
- [x] App 对比引擎实现 (参数差异对齐、Stage 指标偏差分析)
- [x] 诊断报告生成引擎 (Markdown 格式输出，识别 Skew, GC, Spill)

## 2. API 接口开发 (RESTful API)
- [x] Application 列表与基本信息接口
- [x] Job & Stage 详情接口
- [x] Executor 资源监控接口
- [x] Task 级高性能分页接口
- [x] 配置对比与诊断报告接口

## 3. 前端可视化 (Frontend - Vue3 + ECharts)
- [x] 基础布局与 Tab 导航设计 (Jobs, Stages, Executors, Diagnosis)
- [x] 智能诊断大屏 (Markdown 渲染 + 倾斜 Stage 高亮)
- [x] 原生视图复刻 (Jobs 列表, Stages 列表, Executors 列表)
- [x] Task 级详情分页组件 (超越原生 UI 的加载速度，支持 GC 与 Spill 预警)
- [ ] 双任务 Side-by-Side 对比可视化页面
- [ ] ECharts 增强图表 (Task 耗时分布、Shuffle 读写热力图)

## 4. 优化与打包 (Polish & Delivery)
- [ ] 增强大规模 EventLog 处理稳定性 (流式处理优化)
- [ ] 可配置化增强 (扫描路径、数据库位置等)
- [ ] 可执行 JAR 包打包测试与使用说明
