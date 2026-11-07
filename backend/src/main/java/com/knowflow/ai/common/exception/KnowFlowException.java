package com.knowflow.ai.common.exception;

public class KnowFlowException extends RuntimeException {
    public KnowFlowException(String message) {
        super(message);
    }

    public KnowFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
