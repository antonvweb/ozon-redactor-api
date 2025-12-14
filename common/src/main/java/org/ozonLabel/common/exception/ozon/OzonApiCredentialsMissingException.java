package org.ozonLabel.common.exception.ozon;

import org.springframework.http.HttpStatus;

public class OzonApiCredentialsMissingException extends OzonApiException {
    public OzonApiCredentialsMissingException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
