package com.staymatch.service;

import org.springframework.http.HttpStatus;

public class PropertyPhotoException extends RuntimeException {

    private final HttpStatus status;

    public PropertyPhotoException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
