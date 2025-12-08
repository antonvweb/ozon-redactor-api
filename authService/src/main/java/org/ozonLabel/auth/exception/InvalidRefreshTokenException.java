package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AuthException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
    }
}
