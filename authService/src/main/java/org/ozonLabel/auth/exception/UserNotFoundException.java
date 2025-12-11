package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AuthException {
    public UserNotFoundException() {
        super("Пользователь не найден", HttpStatus.NOT_FOUND);
    }
}
