package com.fluffyeti.spark.performance.insight.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fluffyeti.spark.performance.insight.mapper.ApplicationMapper;
import com.fluffyeti.spark.performance.insight.model.GoldApplicationModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Slf4j
@Service
public class ApplicationService extends ServiceImpl<ApplicationMapper, GoldApplicationModel> {

    @Autowired
    @Lazy
    private DuckDBManagerService duckDBManager;

    private final Map<String, Long> lastUpdateTimestamp = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> appLocks = new ConcurrentHashMap<>();

    public void updateAppMetrics(String appId) {
        executeLocked(appId, () -> {
            baseMapper.updateAppMetrics(appId);
            return null;
        });
    }

    /**
     * Helper to execute a task under a per-app lock to prevent DuckDB update conflicts.
     */
    public <T> T executeLocked(String appId, Supplier<T> task) {
        ReentrantLock lock = appLocks.computeIfAbsent(appId, k -> new ReentrantLock());
        lock.lock();
        try {
            return duckDBManager.executeWithRetry(task);
        } finally {
            lock.unlock();
        }
    }

    public void updateStatusAtomic(String appId, String status, Double progressValue, String progressText, Long startTime) {
        long now = System.currentTimeMillis();
        boolean isTerminal = "SUCCESS".equals(status) || "FAILED".equals(status);
        
        if (!isTerminal) {
            Long lastUpdate = lastUpdateTimestamp.get(appId);
            if (lastUpdate != null && (now - lastUpdate < 1000)) {
                return;
            }
        }

        ReentrantLock lock = appLocks.computeIfAbsent(appId, k -> new ReentrantLock());
        
        if (!isTerminal) {
            if (!lock.tryLock()) return;
        } else {
            lock.lock();
        }

        try {
            if (!isTerminal) {
                Long lastUpdate = lastUpdateTimestamp.get(appId);
                if (lastUpdate != null && (System.currentTimeMillis() - lastUpdate < 1000)) {
                    return;
                }
            }

            duckDBManager.runWithRetry(() -> {
                doUpdateStatus(appId, status, progressValue, progressText, startTime);
                lastUpdateTimestamp.put(appId, System.currentTimeMillis());
            });
        } finally {
            lock.unlock();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void doUpdateStatus(String appId, String status, Double progressValue, String progressText, Long startTime) {
        var updateChain = lambdaUpdate()
                .eq(GoldApplicationModel::getAppId, appId)
                .set(GoldApplicationModel::getParsingStatus, status)
                .set(GoldApplicationModel::getParsingProgressValue, progressValue)
                .set(GoldApplicationModel::getParsingProgress, progressText);
        
        if (startTime != null) {
            updateChain.set(GoldApplicationModel::getParsingStartTime, startTime);
        }
        
        updateChain.update();
    }

    public void updateStatusAtomic(String appId, String status, Double progressValue, String progressText) {
        updateStatusAtomic(appId, status, progressValue, progressText, null);
    }
}
