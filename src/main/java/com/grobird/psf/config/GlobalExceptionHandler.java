package com.grobird.psf.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Ensures 401 from login (and other ResponseStatusException) return a clear JSON body
 * so API docs and clients see the reason (e.g. "Invalid email or password").
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
            message = status != null ? status.getReasonPhrase() : "Error";
        }
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("error", message));
    }
}
