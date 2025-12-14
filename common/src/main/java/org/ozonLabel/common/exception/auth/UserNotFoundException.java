package org.ozonLabel.common.exception.auth;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AuthException {
    public UserNotFoundException() {
        super("Пользователь не найден", HttpStatus.NOT_FOUND);
    }
}
