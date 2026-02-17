package com.fluffyeti.spark.performance.insight.parser;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EventEnvelope {
    public enum Type {DATA, EOF, EOA}

    private String appId;
    private String content;
    private Type type;
    private String fileName;

    public static EventEnvelope data(String appId, String content) {
        return new EventEnvelope(appId, content, Type.DATA, null);
    }

    public static EventEnvelope eof(String appId, String fileName) {
        return new EventEnvelope(appId, null, Type.EOF, fileName);
    }

    public static EventEnvelope eoa(String appId) {
        return new EventEnvelope(appId, null, Type.EOA, null);
    }
}
