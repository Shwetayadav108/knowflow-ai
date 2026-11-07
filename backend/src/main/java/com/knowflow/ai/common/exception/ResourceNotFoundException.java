package com.knowflow.ai.common.exception;

public class ResourceNotFoundException extends KnowFlowException {
    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier));
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
