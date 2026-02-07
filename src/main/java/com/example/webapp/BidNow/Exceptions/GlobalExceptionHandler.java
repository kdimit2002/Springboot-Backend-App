package com.example.webapp.BidNow.Exceptions;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    //Todo: Logs and emails

    // @Field validations return field error and error message for example @NotBlank has had blank input
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage
                ));

        return ResponseEntity.badRequest().body(errors);
    }


    // Validation exceptions in controllers
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraint(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(
                Map.of("error", ex.getMessage())
        );
    }

    // When we don't have resource throw it
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<?> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.badRequest().body(
                Map.of("error", ex.getMessage())
        );
    }
    // For firebase connection exceptions
    @ExceptionHandler(FirebaseConnectionException.class)
    public ResponseEntity<?> handleConnectionIssues(FirebaseConnectionException ex) {
        return ResponseEntity.status(500).body(
                Map.of("error", ex.getMessage() + "\nThis is a connection issue")
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntime(RuntimeException ex) {
        // Build safe response
        Map<String, Object> body = Map.of(
                "error", "Internal server error",
                "message", ex.getMessage(),
                "timestamp", LocalDateTime.now()
        );

        return ResponseEntity.status(500).body(body);
    }


    @ExceptionHandler(FirebaseUserDeleteException.class)
    public ResponseEntity<?> handleRuntime(FirebaseUserDeleteException ex) {
        // Build safe response
        log.warn("User was deleted unreliable input:"+ ex.getMessage());
        return ResponseEntity.badRequest().body(
                Map.of("error: ", ex.getMessage())
        );
    }


    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<?> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(429).body(
                Map.of("error", ex.getMessage())
        );
    }


    @ExceptionHandler(R2StorageException.class)
    public ResponseEntity<?> handleR2StorageException(R2StorageException ex) {
        log.error("R2 storage error", ex);
        return ResponseEntity.status(500).body(
                Map.of(
                        "error", "Failed to upload image",
                        "message", ex.getMessage() != null ? ex.getMessage() : "",
                        "timestamp", LocalDateTime.now().toString()
                )
        );
    }


    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(404).body(ex.getMessage());
    }

}