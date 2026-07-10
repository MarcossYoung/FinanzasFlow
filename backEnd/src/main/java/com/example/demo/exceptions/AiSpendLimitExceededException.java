package com.example.demo.exceptions;

public class AiSpendLimitExceededException extends RuntimeException {
    public AiSpendLimitExceededException(String message) {
        super(message);
    }
}
