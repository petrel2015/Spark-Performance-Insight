package com.spark.insight.controller;

import com.spark.insight.service.ApplicationOverwriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationOverwriteController {

    private final ApplicationOverwriteService overwriteService;

    @PostMapping("/{appId}/overwrite-confirm")
    public void confirm(@PathVariable String appId) {
        overwriteService.confirmOverwrite(appId);
    }

    @PostMapping("/{appId}/overwrite-cancel")
    public void cancel(@PathVariable String appId) {
        overwriteService.cancelOverwrite(appId);
    }
}
