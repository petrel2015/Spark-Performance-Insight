package com.spark.insight.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfigDiff {
    private String paramKey;
    private String value1;
    private String value2;
    private boolean different;
}
