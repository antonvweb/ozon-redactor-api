package org.ozonLabel.ozonApi.exception;

import org.springframework.http.HttpStatus;

public class OzonApiCredentialsMissingException extends OzonApiException {
    public OzonApiCredentialsMissingException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
