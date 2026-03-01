# 技术设计：Executor 性能协同诊断与时序分析

## 1. 概述
本功能旨在识别不同 Executor 之间的性能差异（如硬件故障、网络瓶颈等），并提供资源利用率和吞吐量的时序分布视图，帮助用户精准定位“慢节点”。

---

## 2. Executor 诊断卡片
该卡片将置于 **Executors** 页签顶部，用于高亮显示潜在的异常节点。

### 2.1 核心对比指标
通过对比单个 Executor 的效率与全集群的中位数，判断是否存在性能异常：
1.  **任务处理效率**：`已完成任务数 / Executor 总运行时间`。
    - 识别在相同运行时间内处理任务数明显偏少的节点。
2.  **HDFS I/O 速率**：`HDFS 读取字节数 / Executor 总运行时间`。
    - 对比 HDFS 访问性能。若某个节点每秒读取字节数显著低于均值，通常代表该节点的磁盘或所在网络链路存在问题。
3.  **Shuffle I/O 速率**：`Shuffle 读写字节数 / Executor 总运行时间`。

### 2.2 异常检测逻辑
- **阈值**：若某个 Executor 的效率低于集群中位数的 `60%`（可配置），则标记为异常。
- **诊断输出示例**：
    - "Executor X 的 HDFS I/O 效率比平均水平低 40%，提示可能存在磁盘或网络瓶颈。"
    - "Executor Y 的任务吞吐量显著偏低，请检查宿主机的 CPU 超卖情况或高负载（Load Average）状态。"

---

## 3. Executor 性能时序分布图
使用多维折线图展示活动分布。

### 3.1 图表配置 (ECharts)
- **X 轴**：事件时间（按分钟或自定义区间分桶）。
- **Y 轴**（可切换）：
    - **任务数 (Tasks)**：该时间窗内处理的任务数量。
    - **HDFS I/O**：该时间窗内从 HDFS 读取/写入的字节数。
    - **Shuffle 指标**：Shuffle 读写的字节数和记录条数。
- **系列 (Series)**：每个 Executor 对应一条折线，支持选择特定节点或查看堆叠面积图。

### 3.2 数据聚合查询
对 `gold_tasks` 表进行时间窗口聚合：
```sql
SELECT 
    floor(launch_time / 60000) * 60000 as time_window, -- 按分钟对齐
    executor_id,
    count(*) as task_count,
    sum(input_bytes) as hdfs_read,
    sum(shuffle_read_bytes) as shuffle_read,
    ...
FROM gold_tasks
WHERE app_id = ?
GROUP BY 1, 2
ORDER BY 1 ASC
```

---

## 4. UI/UX 增强
1.  **诊断告警**：若检测到异常，在页面显眼位置显示诊断结论。
2.  **指标切换器**：支持在任务数、HDFS 字节数、Shuffle 数据等维度间一键切换。
3.  **联动高亮**：在列表中点击某个 Executor 时，时序图中对应的折线自动高亮。
