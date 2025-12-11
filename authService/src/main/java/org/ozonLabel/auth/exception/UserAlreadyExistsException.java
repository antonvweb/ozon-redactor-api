package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends AuthException {
    public UserAlreadyExistsException() {
        super("Пользователь с таким адресом электронной почты уже существует", HttpStatus.CONFLICT);
    }
}