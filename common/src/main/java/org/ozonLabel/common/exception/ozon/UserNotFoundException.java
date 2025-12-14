package org.ozonLabel.common.exception.ozon;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends OzonApiException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}