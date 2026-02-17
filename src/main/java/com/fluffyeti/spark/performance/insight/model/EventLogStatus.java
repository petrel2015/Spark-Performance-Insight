package com.fluffyeti.spark.performance.insight.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EventLogStatus {
    PROCESSING(0, "Parsing"),
    IMPORTING(1, "Importing"),
    SUCCESS(2, "Success"),
    FAILED(3, "Failed");

    @EnumValue
    private final Integer code;
    private final String description;

    EventLogStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}