package com.spark.insight.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice

public class GlobalExceptionHandler {



    @ExceptionHandler(AppParsingException.class)

    public ResponseEntity<Map<String, Object>> handleAppParsingException(AppParsingException ex) {

        Map<String, Object> body = new HashMap<>();

        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());

        body.put("error", "Service Unavailable");

        body.put("message", ex.getMessage());

        body.put("code", "APP_PARSING");

        return new ResponseEntity<>(body, HttpStatus.SERVICE_UNAVAILABLE);

    }



    @ExceptionHandler(IllegalArgumentException.class)

    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {

        Map<String, Object> body = new HashMap<>();

        body.put("status", HttpStatus.BAD_REQUEST.value());

        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);

    }



    @ExceptionHandler(RuntimeException.class)

    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {

        Map<String, Object> body = new HashMap<>();

        // Most our RuntimeExceptions in ComparisonService are actually "Not Found" cases

        HttpStatus status = ex.getMessage().contains("not found") ? HttpStatus.NOT_FOUND : HttpStatus.INTERNAL_SERVER_ERROR;

        body.put("status", status.value());

        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, status);

    }

}
