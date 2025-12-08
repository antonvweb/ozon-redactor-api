package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends AuthException {
    public RateLimitExceededException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}