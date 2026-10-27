package com.staymatch.controller;

import com.staymatch.dto.OwnerPropertyErrorResponse;
import com.staymatch.service.InvalidOwnerPropertyException;
import com.staymatch.service.PropertyPhotoException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {OwnerPropertyController.class, OwnerPropertyPhotoController.class})
public class OwnerPropertyExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<OwnerPropertyErrorResponse> handleRequestValidation(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new OwnerPropertyErrorResponse("Property request validation failed."));
    }

    @ExceptionHandler(InvalidOwnerPropertyException.class)
    public ResponseEntity<OwnerPropertyErrorResponse> handleInvalidProperty(InvalidOwnerPropertyException exception) {
        return ResponseEntity.badRequest().body(new OwnerPropertyErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(PropertyPhotoException.class)
    public ResponseEntity<OwnerPropertyErrorResponse> handleInvalidPhoto(PropertyPhotoException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new OwnerPropertyErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<OwnerPropertyErrorResponse> handleNotFound(EntityNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new OwnerPropertyErrorResponse("Property not found."));
    }
}
