## 0.21.0 - 2026-02-05

### 新功能
- 实现仿 Spark UI 的 SQL / DataFrame 页签
- 添加包含关联作业 ID 的 SQL 执行列表
- 添加包含物理执行计划和关联作业的 SQL 详情页
- 从日志中解析 SparkListenerSQLExecution 事件

### 改进
- 链接现在支持在浏览器新标签页中打开 (使用 router-link)
- 更新 TODO.md 中的最新任务进度

## 0.20.1 - 2026-02-05

### 配置
- 将事件日志扫描间隔更改为一小时（3600 秒）

## 0.20.0 - 2026-02-05

### 新功能
- 确保同一 Spark 应用程序的滚动事件日志按顺序解析
- 在 EventLogWatcherService 中实现并发控制，防止重复解析任务
- 在数据库中添加事件日志的 "PROCESSING" 状态追踪

### 改进
- 优化瀑布流时间轴，改进车道压缩 (lane packing) 逻辑
- 修正 Job DAG 可视化中的 RDD 定位问题

## 0.19.0 - 2026-02-05

### 新功能
- 实现传统的事件时间轴，使用垂直旗帜显示 Executor 生命周期
- 在 Stage 详情中增加数据本地性摘要 (Locality Level Summary)
- 改进 Job DAG 可视化，修复嵌套逻辑和样式
- 为时间轴图表添加缩放锁定控制
- 全面使用 Material Design Icons 图标
- 在摘要表中显示基于时间的指标百分比
- 追踪并记录 EventLog 解析耗时

### 改进
- 优化 /report 接口以使用预计算指标
- 支持在浏览器新标签页中打开列表项和页签 (使用真实链接)
- 默认折叠 Job DAG 和事件时间轴卡片
- 统一 Stage 和 Job DAG 的样式

## 0.18.0 - 2026-02-05

### 新功能
- 从事件日志文件名推断 App ID
- 为 Job 和 Stage 列表添加搜索功能
- 更新 TODO.md 中的任务追踪

## 0.17.1 - 2026-02-04

### 性能优化
- 优化后端解析和前端懒加载

### 重构
- 前端组件大规模重构和后端微调

### 文档
- 将 TODO.md 翻译为中文并更新环境/依赖配置

## 0.17.0 - 2026-02-04

### 新功能
- 作业组搜索，优雅的解析处理和指标改进 (by @hongyu)
- 在作业列表中添加作业组搜索 (by @hongyu)
- 添加应用程序解析状态检查，进度跟踪和带有前端通知的 503 处理 (by @hongyu)
- 为不完整的数据添加应用程序解析状态检查和 503 响应 (by @hongyu)
- 作业列表页面的指标可见性选择器 (by @hongyu)
- 向作业详细信息页面添加指标选择器并在阶段表中支持动态列 (by @hongyu)
- 向带有导航支持的阶段列表添加可排序的作业 ID 列 (by @hongyu)

### 修复
- 解决由于不正确的类引用导致的 InsightController 中的编译错误 (by @hongyu)
- 访问 JobsTab 脚本中计算列的 .value 以修复 TypeError (by @hongyu)
- 从 Vue 导入 computed 以修复 ReferenceError (by @hongyu)
- 访问 StageTable 脚本中计算列的 .value (by @hongyu)

## 0.15.0 - 2026-02-04

### 新功能
- 作业时间表，滚动日志和增强的作业列表 (by @hongyu)
- 向作业列表添加阶段 ID 和阶段计数列 (by @hongyu)
- 将作业 ID 和作业组拆分为单独的可排序列表 (by @hongyu)

### 格式
- 简化作业 DAG 中的阶段组标签，仅显示阶段 ID (by @hongyu)

## 0.14.0 - 2026-02-04

### 新功能
- 作业 DAG 可视化和 SQL 歧义修复 (by @hongyu)

## 0.13.0 - 2026-02-04

### 新功能
- 计划日志解析，诊断阈值和 Docker 支持 (by @hongyu)
- 添加基于 Bitnami Spark 3.5 的 Dockerfile 和带有 History Server 集成的 docker-compose (by @hongyu)

### 格式
- 加宽阶段 ID 列以防止尝试/徽章换行 (by @hongyu)

## 0.12.1 - 2026-02-04

### 新功能
- 与 Spark Web UI 的 UI 对等和润色 (by @hongyu)
- 通过增加列宽和添加分词样式来处理应用程序列表中的长应用程序 ID (by @hongyu)
- 将作业 ID 列更新为“作业 ID (作业组)”并优化徽章样式 (by @hongyu)
- 默认作业列表按 jobId DESC 排序以与 Spark Web UI 对齐 (by @hongyu)

### 修复
- 阶段详细信息“作业”链接现在正确导航到作业详细信息而不是作业列表 (by @hongyu)

## 0.12.0 - 2026-02-04

### 新功能
- 全面支持阶段重试（多次尝试） (by @hongyu)

## 0.11.0 - 2026-02-04

### 新功能
- 主动数据质量治理和强大的诊断报告 (by @hongyu)

## 0.10.1 - 2026-02-04

### 修复
- 启用 Jackson 解析大型 JSON 事件日志（>20MB） (by @hongyu)
- DiagnosisService markdown 报告中的稳健空处理 (by @hongyu)

## 0.10.0 - 2026-02-04

### 新功能
- 不区分大小写的应用程序搜索，新的执行器时间表和 UI 标准化 (by @hongyu)

## 0.9.0 - 2026-02-04

### 新功能
- 完成 RDD 沿袭样式/稳定性并完善时间轴 UX (by @hongyu)

## 0.8.0 - 2026-02-04

### 新功能
- 具有并发趋势的高级事件时间轴，RDD 沿袭 V2 和全局交互锁 (by @hongyu)

## 0.7.0 - 2026-02-04

### 新功能
- 实现 RDD DAG 可视化，增强阶段摘要和响应式 UI 网格 (by @hongyu)

## 0.6.0 - 2026-02-04

### 新功能
- 完成 v0.6.0，包括作业详细信息，高级排序和完整的 UI 标准化 (by @hongyu)
- 增强作业元数据与调用站点和完整的作业详细视图 (by @hongyu)

### 格式
- 统一作业列表链接样式与阶段列表 (by @hongyu)

### 修复
- 正确处理带有阶段名称回退的作业描述并修复编译 (by @hongyu)
- 正确链接作业详细信息视图中的阶段到作业 (by @hongyu)

## 0.3.0 - 2026-02-04

### 新功能
- 全面升级作业/阶段/环境视图和 UI 标准化 (by @hongyu)
- 增强阶段详细信息与执行器聚合和可折叠卡片 (by @hongyu)

## 0.2.0 - 2026-02-04

### 新功能
- 增强阶段摘要指标以匹配 Spark UI 对等 (by @hongyu)

### 修复
- 解决 API_BASE 引用错误和缺少的组件 (by @hongyu)

## 0.1.0 - 2026-02-04

### 新功能
- 增强作业/阶段分析和 UI 与 Spark History Server 的对等 (by @hongyu)
- 启用任务表中的多列排序 (by @hongyu)
- 使用 frontend-maven-plugin 实现自动前端打包到 JAR 中。 (by @hongyu)

### 重构
- 模块化阶段详细信息并增强环境选项卡 (by @hongyu)

### 文档
- 生成 v0.1.0 更改日志并添加致谢 (by @hongyu)