package com.spark.insight.service;

import com.spark.insight.model.EnvironmentConfigModel;
import com.spark.insight.model.StageModel;
import com.spark.insight.model.dto.AppComparisonResult;
import com.spark.insight.model.dto.ConfigDiff;
import com.spark.insight.model.dto.StageDiff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComparisonService {

    private final EnvironmentConfigService envService;
    private final StageService stageService;

    public AppComparisonResult compareApps(String appId1, String appId2) {
        AppComparisonResult result = new AppComparisonResult();
        result.setAppId1(appId1);
        result.setAppId2(appId2);

        // 1. 对比配置
        result.setConfigDiffs(compareConfigs(appId1, appId2));

        // 2. 对比 Stage
        result.setStageDiffs(compareStages(appId1, appId2));

        return result;
    }

    private List<ConfigDiff> compareConfigs(String appId1, String appId2) {
        Map<String, String> conf1 = envService.lambdaQuery().eq(EnvironmentConfigModel::getAppId, appId1)
                .list().stream().collect(Collectors.toMap(EnvironmentConfigModel::getParamKey, EnvironmentConfigModel::getParamValue, (a, b) -> a));
        Map<String, String> conf2 = envService.lambdaQuery().eq(EnvironmentConfigModel::getAppId, appId2)
                .list().stream().collect(Collectors.toMap(EnvironmentConfigModel::getParamKey, EnvironmentConfigModel::getParamValue, (a, b) -> a));

        Set<String> allKeys = new TreeSet<>(conf1.keySet());
        allKeys.addAll(conf2.keySet());

        List<ConfigDiff> diffs = new ArrayList<>();
        for (String key : allKeys) {
            String v1 = conf1.getOrDefault(key, "N/A");
            String v2 = conf2.getOrDefault(key, "N/A");
            if (!v1.equals(v2)) {
                diffs.add(new ConfigDiff(key, v1, v2, true));
            }
        }
        return diffs;
    }

    private List<StageDiff> compareStages(String appId1, String appId2) {
        List<StageModel> stages1 = stageService.lambdaQuery().eq(StageModel::getAppId, appId1).list();
        List<StageModel> stages2 = stageService.lambdaQuery().eq(StageModel::getAppId, appId2).list();

        // 以 StageName 为 Key 进行对齐
        Map<String, StageModel> map2 = stages2.stream()
                .collect(Collectors.toMap(StageModel::getStageName, s -> s, (a, b) -> a));

        List<StageDiff> diffs = new ArrayList<>();
        for (StageModel s1 : stages1) {
            StageModel s2 = map2.get(s1.getStageName());
            if (s2 != null) {
                StageDiff diff = new StageDiff();
                diff.setStageName(s1.getStageName());
                diff.setStageId1(s1.getStageId());
                diff.setStageId2(s2.getStageId());
                
                diff.setDurationP95_1(s1.getDurationP95());
                diff.setDurationP95_2(s2.getDurationP95());
                diff.setDurationDelta(calculateDelta(s1.getDurationP95(), s2.getDurationP95()));

                diff.setGcTime1(s1.getGcTimeSum());
                diff.setGcTime2(s2.getGcTimeSum());
                diff.setGcDelta(calculateDelta(s1.getGcTimeSum(), s2.getGcTimeSum()));

                diff.setShuffleRead1(s1.getShuffleReadBytes());
                diff.setShuffleRead2(s2.getShuffleReadBytes());
                
                diffs.add(diff);
            }
        }
        return diffs;
    }

    private Double calculateDelta(Long v1, Long v2) {
        if (v1 == null || v2 == null || v1 == 0) return 0.0;
        return (double) (v2 - v1) / v1;
    }
}
