package com.aerodag.core.exception;

public class DagGenerationException extends RuntimeException {

    public DagGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public DagGenerationException(String message){
        super(message);
    }
}
