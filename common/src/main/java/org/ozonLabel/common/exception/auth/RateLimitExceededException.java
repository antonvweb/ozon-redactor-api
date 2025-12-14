package org.ozonLabel.common.exception.auth;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends AuthException {
    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}