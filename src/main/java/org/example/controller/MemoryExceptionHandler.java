package org.example.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {MemoryController.class, AgentRunController.class})
public class MemoryExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidRequest(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("error", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("error", error.getMessage()));
    }
}
