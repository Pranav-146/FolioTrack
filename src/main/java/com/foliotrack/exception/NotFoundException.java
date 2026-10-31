package com.foliotrack.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Unchecked so services can throw it without cluttering signatures; Spring maps it to 404.
// Phase 3's global exception handler will turn it into the consistent error JSON.
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
