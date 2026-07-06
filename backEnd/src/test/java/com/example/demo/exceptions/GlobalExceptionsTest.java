package com.example.demo.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionsTest {
    private final GlobalExceptions exceptions = new GlobalExceptions();

    @Test
    void methodArgumentTypeMismatchReturnsBadRequestInsteadOfServerError() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "NOT_A_STATUS", null, "status", null, new IllegalArgumentException("bad enum"));

        ResponseEntity<Map<String, Object>> response = exceptions.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(((String) response.getBody().get("error")).contains("status"));
    }
}
