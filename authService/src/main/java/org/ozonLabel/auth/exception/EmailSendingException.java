package org.ozonLabel.auth.exception;

import org.springframework.http.HttpStatus;

public class EmailSendingException extends AuthException {
    public EmailSendingException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}