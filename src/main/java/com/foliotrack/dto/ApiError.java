package com.foliotrack.dto;

import java.time.Instant;
import java.util.List;

// The single error shape every endpoint returns, so clients parse failures one way.
// fieldErrors is always present (empty when the error isn't about specific inputs).
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }
}
