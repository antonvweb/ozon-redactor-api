package org.ozonLabel.common.exception.auth;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends AuthException {
    public InvalidRefreshTokenException() {
        super("Недействительный или просроченный токен обновления", HttpStatus.UNAUTHORIZED);
    }
}
