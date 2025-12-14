package org.ozonLabel.common.exception.auth;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("Неверный адрес электронной почты или пароль", HttpStatus.UNAUTHORIZED);
    }
}