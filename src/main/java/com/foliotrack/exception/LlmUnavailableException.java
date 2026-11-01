package com.foliotrack.exception;

// The LLM provider is not configured, unreachable, rate limiting us, or returned nothing usable.
// Mapped to 503 by the global exception handler: the request was fine, a dependency wasn't.
public class LlmUnavailableException extends RuntimeException {

    public LlmUnavailableException(String message) {
        super(message);
    }
}
