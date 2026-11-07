package com.logismart.controller;

import com.logismart.data.CsvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Consistent JSON error responses: {"error": "...", "details": "..."}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({CsvParser.CsvFormatException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid input",
                "details", e.getMessage() == null ? "" : e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Malformed request body",
                "details", "Check JSON syntax and field types (e.g. objective must be DISTANCE or COST)"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> database(DataAccessException e) {
        log.error("Database access failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "Database unavailable",
                "details", "Check DB_URL / DB_USERNAME / DB_PASSWORD, or start with "
                        + "APP_DATA_SOURCE=csv to use the bundled CSV data"));
    }
}
