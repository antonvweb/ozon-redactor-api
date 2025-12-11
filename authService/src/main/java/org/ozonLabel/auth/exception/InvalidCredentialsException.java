package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends AuthException {
    public InvalidCredentialsException() {
        super("Неверный адрес электронной почты или пароль", HttpStatus.UNAUTHORIZED);
    }
}