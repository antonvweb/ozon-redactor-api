package org.ozonLabel.common.exception.ozon;

import org.springframework.http.HttpStatus;

public class FolderAccessDeniedException extends FolderException {
    public FolderAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}