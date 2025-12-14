package org.ozonLabel.common.exception.auth;

import org.springframework.http.HttpStatus;

public class InvalidVerificationCodeException extends AuthException {
    public InvalidVerificationCodeException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
