package com.foliotrack.exception;

// Thrown for orders that are malformed (e.g. quantity <= 0) and so cannot even be recorded as REJECTED:
// the orders table itself requires quantity > 0. Mapped to 400 by the global exception handler.
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
