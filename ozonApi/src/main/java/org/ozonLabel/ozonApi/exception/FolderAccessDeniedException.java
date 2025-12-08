package org.ozonLabel.ozonApi.exception;

import org.springframework.http.HttpStatus;

public class FolderAccessDeniedException extends FolderException {
    public FolderAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}