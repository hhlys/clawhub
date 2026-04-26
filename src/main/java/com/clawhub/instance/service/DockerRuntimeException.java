package com.clawhub.instance.service;

public class DockerRuntimeException extends RuntimeException {

    public DockerRuntimeException(String message) {
        super(message);
    }

    public DockerRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
