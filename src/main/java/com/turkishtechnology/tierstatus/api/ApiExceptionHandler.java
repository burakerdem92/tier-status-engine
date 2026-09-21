package com.turkishtechnology.tierstatus.api;

import java.time.Instant;
import java.util.Map;

import com.turkishtechnology.tierstatus.domain.EventValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MemberNotFoundException.class)
    ResponseEntity<Map<String, Object>> notFound(MemberNotFoundException exception) {
        return response(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(EventValidationException.class)
    ResponseEntity<Map<String, Object>> invalidEvent(EventValidationException exception) {
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
