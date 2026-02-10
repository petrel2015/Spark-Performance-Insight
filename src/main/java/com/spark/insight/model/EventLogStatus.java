package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EventLogStatus {
    PROCESSING(0, "Parsing"),
    IMPORTING(1, "Importing"),
    SUCCESS(2, "Success"),
    FAILED(3, "Failed");

    @EnumValue
    private final int code;
    private final String description;

    EventLogStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
}