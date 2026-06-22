package com.example.demo.exceptions;

public class TelegramApiException extends RuntimeException {
    public enum Reason {
        NOT_CONFIGURED,
        METADATA,
        DOWNLOAD,
        TOO_LARGE,
        API
    }

    private final Reason reason;

    public TelegramApiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public TelegramApiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
