package org.ozonLabel.ozonApi.exception;

import org.springframework.http.HttpStatus;

public class FolderException extends OzonApiException {
    public FolderException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public FolderException(String message, HttpStatus status) {
        super(message, status);
    }
}