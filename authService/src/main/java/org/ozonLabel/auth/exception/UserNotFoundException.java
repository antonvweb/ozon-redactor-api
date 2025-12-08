package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AuthException {
    public UserNotFoundException() {
        super("User not found", HttpStatus.NOT_FOUND);
    }
}
