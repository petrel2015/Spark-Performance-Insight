package com.fluffyeti.spark.performance.insight.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a transformation or aggregation step for monitoring and logging.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonitorStep {
    String value() default "";
    String type() default "GENERAL";
}
