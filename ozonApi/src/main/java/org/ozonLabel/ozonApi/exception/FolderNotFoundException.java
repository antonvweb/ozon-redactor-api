package org.ozonLabel.ozonApi.exception;

import org.springframework.http.HttpStatus;

public class FolderNotFoundException extends FolderException {
    public FolderNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}