package com.spark.insight.model;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum EventLogStatus {
    PROCESSING((byte) 0L, "Parsing"),
    IMPORTING((byte) 1, "Importing"),
    SUCCESS((byte) 2, "Success"),
    FAILED((byte) 3, "Failed");

    @EnumValue
    private final Byte code;
    private final String description;

    EventLogStatus(Byte code, String description) {
        this.code = code;
        this.description = description;
    }
}