package com.fluffyeti.spark.performance.insight.aspect;

import com.fluffyeti.spark.performance.insight.annotation.MonitorStep;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class TransformationMonitorAspect {

    private static final Logger log = LogManager.getLogger(TransformationMonitorAspect.class);
    private static final Level PROGRESS = Level.forName("PROGRESS", 350);

    @Around("@annotation(monitorStep)")
    public Object monitor(ProceedingJoinPoint joinPoint, MonitorStep monitorStep) throws Throwable {
        String stepName = monitorStep.value();
        if (stepName.isEmpty()) {
            stepName = joinPoint.getSignature().getName();
        }
        String type = monitorStep.type();
        
        Object[] args = joinPoint.getArgs();
        String appId = (args.length > 0 && args[0] instanceof String) ? (String) args[0] : "unknown";
        
        long startTime = System.currentTimeMillis();
        // Log at PROGRESS level
        log.log(PROGRESS, "[{}] [{}] Starting: {} for App: {}", 
            type, Thread.currentThread().getName(), stepName, appId);
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            log.log(PROGRESS, "[{}] [{}] Finished: {} for App: {} in {}ms", 
                type, Thread.currentThread().getName(), stepName, appId, duration);
            return result;
        } catch (Throwable e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[{}] [{}] Failed: {} for App: {} after {}ms. Error: {}", 
                type, Thread.currentThread().getName(), stepName, appId, duration, e.getMessage());
            throw e;
        }
    }
}
