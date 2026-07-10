package com.example.demo.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @Test
    void aiServiceNotConfiguredReturnsServiceUnavailable() {
        ResponseEntity<Map<String, Object>> response = exceptions.handleAiServiceException(
                new AiServiceException(AiServiceException.Reason.NOT_CONFIGURED, "missing"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    @Test
    void aiServiceHttpErrorReturnsBadGateway() {
        ResponseEntity<Map<String, Object>> response = exceptions.handleAiServiceException(
                new AiServiceException(AiServiceException.Reason.HTTP_ERROR, "bad"));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
    }

    @Test
    void aiServiceEmptyOrInvalidExtractionReturnsUnprocessableEntity() {
        ResponseEntity<Map<String, Object>> emptyResponse = exceptions.handleAiServiceException(
                new AiServiceException(AiServiceException.Reason.EMPTY_RESPONSE, "empty"));
        ResponseEntity<Map<String, Object>> invalidResponse = exceptions.handleAiServiceException(
                new AiServiceException(AiServiceException.Reason.INVALID_JSON, "bad json"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, emptyResponse.getStatusCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, invalidResponse.getStatusCode());
    }

    @Test
    void maxUploadSizeExceededReturnsPayloadTooLarge() {
        ResponseEntity<Map<String, Object>> response = exceptions.handleMaxUploadSizeExceeded(
                new MaxUploadSizeExceededException(10));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    }

    @Test
    void aiSpendLimitExceededReturnsTooManyRequests() {
        ResponseEntity<Map<String, Object>> response = exceptions.handleAiSpendLimitExceeded(
                new AiSpendLimitExceededException("ignored"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("Se alcanzo el limite de uso de IA para este mes.", response.getBody().get("error"));
    }
}
