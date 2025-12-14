package org.ozonLabel.common.exception.user;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base exception for business logic errors
 */
@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;

    public BusinessException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BusinessException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

}