package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends AuthException {
    public UserAlreadyExistsException() {
        super("User with this email already exists", HttpStatus.CONFLICT);
    }
}