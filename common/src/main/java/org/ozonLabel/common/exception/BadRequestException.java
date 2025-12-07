// src/main/java/org/ozonLabel/common/exception/BadRequestException.java
package org.ozonLabel.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}