package com.example.demo.exceptions;

public class AiServiceException extends RuntimeException {
    public enum Reason {
        NOT_CONFIGURED,
        HTTP_ERROR,
        EMPTY_RESPONSE,
        INVALID_JSON
    }

    private final Reason reason;

    public AiServiceException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AiServiceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
