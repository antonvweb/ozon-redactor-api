package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AuthException {
    public InvalidRefreshTokenException() {
        super("Недействительный или просроченный токен обновления", HttpStatus.UNAUTHORIZED);
    }
}
