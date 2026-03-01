package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.ExecutorMapper;
import com.fluffyeti.spark.performance.insight.model.GoldExecutorModel;
import com.fluffyeti.spark.performance.insight.model.dto.ExecutorDiagnosisDTO;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SparkExecutorService extends ServiceImpl<ExecutorMapper, GoldExecutorModel> {
    
    private static final double ANOMALY_THRESHOLD = 0.6;

    public void calculateExecutorMetrics(String appId) {
        baseMapper.updateExecutorMetrics(appId);
    }

    public List<Map<String, Object>> getExecutorTimeSeries(String appId) {
        return baseMapper.getExecutorTimeSeries(appId);
    }

    public List<Map<String, Object>> getExecutorTimeSeriesMetric(String appId, String metricKey) {
        return baseMapper.getExecutorMetricTimeSeries(appId, metricKey);
    }

    public ExecutorDiagnosisDTO getExecutorDiagnosis(String appId) {
        List<Map<String, Object>> metricsData = baseMapper.getExecutorEfficiencyMetrics(appId);
        if (metricsData.isEmpty()) {
            return new ExecutorDiagnosisDTO(new ArrayList<>(), new ArrayList<>());
        }

        List<ExecutorDiagnosisDTO.ExecutorMetrics> metricsList = metricsData.stream().map(m -> 
            ExecutorDiagnosisDTO.ExecutorMetrics.builder()
                .executorId((String) m.get("executor_id"))
                .taskThroughput(((Number) m.get("task_throughput")).doubleValue())
                .hdfsIoRate(((Number) m.get("hdfs_io_rate")).doubleValue())
                .shuffleIoRate(((Number) m.get("shuffle_io_rate")).doubleValue())
                .build()
        ).collect(Collectors.toList());

        // Calculate Medians
        double medianTaskThroughput = calculateMedian(metricsList.stream().map(m -> m.getTaskThroughput()).collect(Collectors.toList()));
        double medianHdfsIoRate = calculateMedian(metricsList.stream().map(m -> m.getHdfsIoRate()).collect(Collectors.toList()));
        double medianShuffleIoRate = calculateMedian(metricsList.stream().map(m -> m.getShuffleIoRate()).collect(Collectors.toList()));

        List<ExecutorDiagnosisDTO.Anomaly> anomalies = new ArrayList<>();

        for (ExecutorDiagnosisDTO.ExecutorMetrics m : metricsList) {
            // Task Throughput Anomaly
            if (medianTaskThroughput > 0 && m.getTaskThroughput() < medianTaskThroughput * ANOMALY_THRESHOLD) {
                anomalies.add(ExecutorDiagnosisDTO.Anomaly.builder()
                    .executorId(m.getExecutorId())
                    .metric("Task Throughput")
                    .reason("Executor " + m.getExecutorId() + " 的任务吞吐量显著偏低，请检查宿主机的 CPU 超卖情况或高负载状态。")
                    .value(m.getTaskThroughput())
                    .median(medianTaskThroughput)
                    .ratio(m.getTaskThroughput() / medianTaskThroughput)
                    .build());
            }
            // HDFS I/O Anomaly
            if (medianHdfsIoRate > 0 && m.getHdfsIoRate() < medianHdfsIoRate * ANOMALY_THRESHOLD) {
                anomalies.add(ExecutorDiagnosisDTO.Anomaly.builder()
                    .executorId(m.getExecutorId())
                    .metric("HDFS I/O Rate")
                    .reason("Executor " + m.getExecutorId() + " 的 HDFS I/O 效率比平均水平低 " + String.format("%.0f%%", (1 - m.getHdfsIoRate() / medianHdfsIoRate) * 100) + "，提示可能存在磁盘或网络瓶颈。")
                    .value(m.getHdfsIoRate())
                    .median(medianHdfsIoRate)
                    .ratio(m.getHdfsIoRate() / medianHdfsIoRate)
                    .build());
            }
            // Shuffle I/O Anomaly
            if (medianShuffleIoRate > 0 && m.getShuffleIoRate() < medianShuffleIoRate * ANOMALY_THRESHOLD) {
                anomalies.add(ExecutorDiagnosisDTO.Anomaly.builder()
                    .executorId(m.getExecutorId())
                    .metric("Shuffle I/O Rate")
                    .reason("Executor " + m.getExecutorId() + " 的 Shuffle I/O 速率异常偏低，可能存在网络抖动或本地磁盘性能下降。")
                    .value(m.getShuffleIoRate())
                    .median(medianShuffleIoRate)
                    .ratio(m.getShuffleIoRate() / medianShuffleIoRate)
                    .build());
            }
        }

        return new ExecutorDiagnosisDTO(anomalies, metricsList);
    }

    private double calculateMedian(List<Double> values) {
        if (values == null || values.isEmpty()) return 0.0;
        List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
        int size = sorted.size();
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }
}
