package com.foliotrack.exception;

// The ask feature's generated SQL was refused, either by SqlValidator (before running) or by the database
// (it failed to run). Nothing was executed, or nothing was returned. Mapped to 422 by the global exception handler;
// the message says why in safe terms and never contains database error details.
public class QueryRejectedException extends RuntimeException {

    public QueryRejectedException(String message) {
        super(message);
    }
}
