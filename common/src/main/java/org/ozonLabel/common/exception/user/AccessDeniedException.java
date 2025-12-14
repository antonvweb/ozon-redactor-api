package org.ozonLabel.common.exception.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends BusinessException {
    public AccessDeniedException(String message) {
        super(message != null ? message : "Access denied", HttpStatus.FORBIDDEN);
    }
}