package com.example.demo.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptions {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptions.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.CONFLICT.value());
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", ex.getMessage());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiServiceException(AiServiceException ex) {
        HttpStatus status;
        String message;
        switch (ex.getReason()) {
            case NOT_CONFIGURED -> {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                message = "IA no configurada.";
            }
            case HTTP_ERROR -> {
                status = HttpStatus.BAD_GATEWAY;
                message = "Error al contactar el servicio de IA.";
            }
            case EMPTY_RESPONSE, INVALID_JSON -> {
                status = HttpStatus.UNPROCESSABLE_ENTITY;
                message = "No se pudieron extraer datos del documento. Proba con una imagen mas clara.";
            }
            default -> {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                message = "An internal error occurred!";
            }
        }
        Map<String, Object> response = new HashMap<>();
        response.put("error", message);
        response.put("status", status.value());
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "El archivo es demasiado grande (maximo 10MB).");
        response.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        return new ResponseEntity<>(response, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(AiSpendLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleAiSpendLimitExceeded(AiSpendLimitExceededException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Se alcanzo el limite de uso de IA para este mes.");
        response.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        return new ResponseEntity<>(response, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFoundError(NoHandlerFoundException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Resource not found!");
        response.put("status", HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleError(Exception e) {
        log.error("Unhandled exception: {}", e.getMessage(), e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "An internal error occurred!");
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
