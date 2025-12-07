// src/main/java/org/ozonLabel/common/exception/BusinessException.java
package org.ozonLabel.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public BusinessException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(String message, HttpStatus status) {
        this(message, status, "BUSINESS_ERROR");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}