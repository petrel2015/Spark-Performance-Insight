package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EventLogStatus {
    PROCESSING(0, "Importing"),
    SUCCESS(1, "Success"),
    FAILED(2, "Failed");

    @EnumValue
    private final int code;
    private final String description;

    EventLogStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
