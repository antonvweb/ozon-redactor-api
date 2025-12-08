package org.ozonLabel.ozonApi.exception;

import org.springframework.http.HttpStatus;

public class OzonApiException extends RuntimeException {
    private final HttpStatus status;

    public OzonApiException(String message) {
        super(message);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public OzonApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public OzonApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}