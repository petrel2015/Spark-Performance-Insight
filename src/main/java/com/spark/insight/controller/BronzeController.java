package com.spark.insight.controller;

import com.spark.insight.service.ParsingQueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/bronze")
@RequiredArgsConstructor
public class BronzeController {

    private final ParsingQueueService parsingQueueService;

    @PostMapping("/import/{appId}")
    public void importToBronze(@PathVariable String appId) {
        log.info("Queueing full Medallion pipeline (Bronze->Silver->Gold) for appId: {}", appId);
        parsingQueueService.submit(appId, "FULL");
    }

    @PostMapping("/reimport/{appId}/full")
    public void reimportFull(@PathVariable String appId) {
        importToBronze(appId);
    }

    @PostMapping("/reimport/{appId}/bronze-to-gold")
    public void reimportBronzeToGold(@PathVariable String appId) {
        log.info("Queueing Medallion pipeline (Bronze->Silver->Gold) for appId: {}", appId);
        parsingQueueService.submit(appId, "BRONZE_TO_GOLD");
    }

    @PostMapping("/reimport/{appId}/silver-to-gold")
    public void reimportSilverToGold(@PathVariable String appId) {
        log.info("Queueing Medallion pipeline (Silver->Gold) for appId: {}", appId);
        parsingQueueService.submit(appId, "SILVER_TO_GOLD");
    }
    
    @PostMapping("/cancel/{appId}")
    public void cancel(@PathVariable String appId) {
        log.info("Cancelling parsing for appId: {}", appId);
        parsingQueueService.cancel(appId);
    }
}
