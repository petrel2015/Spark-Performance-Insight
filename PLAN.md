# Spark Performance Insight - Medallion 状态机重构计划 (v4)

## 1. 目标
通过引入文件指纹（MD5）校验、细粒度的管道状态以及全过程生命周期日志，提供一个工业级稳定且透明的日志处理流程。

## 2. 状态机与日志定义

| 状态码 | UI 显示 | 日志事件 (application_logs) | 可用操作 |
| :--- | :--- | :--- | :--- |
| `PENDING_LOAD` | 等待加载 | `SCAN: New App Found` | 导入并分析 |
| `INGESTING_BRONZE` | 正在导入青铜层 | `IMPORT: Bronze Start` | 无 |
| `TRANSFORMING_SILVER` | 正在构建白银层 | `TRANSFORM: Silver Start` | 无 |
| `AGGREGATING_GOLD` | 正在计算黄金层 | `AGGREGATE: Gold Start` | 无 |
| `SUCCESS` | 解析成功 | `SUCCESS: Pipeline Finished` | 详情、重新导入、删除 |
| `FAILED` | 解析失败 | `FAILED: Pipeline Error` | 重新导入、删除 |
| `PENDING_REIMPORT` | 日志已变动 | `SCAN: Log File Changed` | 重新导入、删除 |

## 3. 执行任务清单

### 第一阶段：数据模型 (Schema)
- [ ] 修改 `src/main/resources/db/schema.sql`：
    - [ ] `applications` 表增加 `source_file_metadata` 列 (JSON)，存储 `[{name, md5, size}]`。

### 第二阶段：后端逻辑重构
- [ ] **文件监控与指纹计算** (`EventLogWatcherService`):
    - [ ] 发现新 App -> 写入 `applications` -> 状态 `PENDING_LOAD` -> 记录 `SCAN` 日志。
    - [ ] 发现已有 App 指纹不符 -> 更新状态 `PENDING_REIMPORT` -> 记录 `SCAN` 日志。
- [ ] **管道状态控制** (`BronzeController`):
    - [ ] Bronze 开始前：更新状态 `INGESTING_BRONZE` + 记录 `IMPORT` 日志。
    - [ ] Silver 开始前：更新状态 `TRANSFORMING_SILVER` + 记录 `TRANSFORM` 日志。
    - [ ] Gold 开始前：更新状态 `AGGREGATING_GOLD` + 记录 `AGGREGATE` 日志。
    - [ ] 成功完成后：更新状态 `SUCCESS` + 更新 `source_file_metadata` + 记录 `SUCCESS` 日志。

### 第三阶段：前端界面重构 (`AppList.vue`)
- [ ] **状态映射**：将 7 种状态映射为相应的文本和颜色。
- [ ] **Action 列动态渲染**：
    - [ ] `PENDING_LOAD`: 仅紫色 “导入并分析” 按钮。
    - [ ] `PENDING_REIMPORT`: 显示警告图标及 “重新导入” 按钮。
    - [ ] 处理中状态：显示进度条，禁用所有按钮。

## 4. 验证计划
1. **日志审计**：执行一次导入，检查 `application_logs` 表是否按顺序记录了从 Bronze 到 Success 的所有步骤。
2. **状态流转**：确认前端 UI 能够实时跟随管道进度（通过 WebSocket）。
3. **一致性**：确认 `source_file_metadata` 在成功后被正确持久化。
