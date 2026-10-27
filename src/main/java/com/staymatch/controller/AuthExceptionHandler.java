package com.staymatch.controller;

import com.staymatch.dto.AuthErrorResponse;
import com.staymatch.service.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.core.AuthenticationException;

@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<AuthErrorResponse> handleDuplicateEmail(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new AuthErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AuthErrorResponse> handleValidationFailure(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse("Request validation failed."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AuthErrorResponse> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new AuthErrorResponse("Invalid registration request."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<AuthErrorResponse> handleAuthenticationFailure(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse("Invalid email or password."));
    }
}
