package org.ozonLabel.common.exception.auth;

import org.springframework.http.HttpStatus;

public class EmailSendingException extends AuthException {
    public EmailSendingException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}