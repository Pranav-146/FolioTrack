package com.foliotrack.exception;

// Unchecked so services can throw it without cluttering signatures.
// GlobalExceptionHandler maps it to a 404 with the standard error JSON.
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
