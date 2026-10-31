package com.foliotrack.web;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Every error response must have the same ApiError shape, so the contract is checked in one place
// instead of being re-typed (and drifting) in each controller test.
final class ApiErrorMatchers {

    // ISO-8601 instant, e.g. 2026-10-31T10:15:30.123Z: proves Jackson writes timestamps as text, not epoch numbers
    private static final String ISO_INSTANT = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z";

    private ApiErrorMatchers() {
    }

    static ResultMatcher apiError(HttpStatus status, String path) {
        return ResultMatcher.matchAll(
                status().is(status.value()),
                content().contentTypeCompatibleWith("application/json"),
                jsonPath("$.timestamp").value(matchesPattern(ISO_INSTANT)),
                jsonPath("$.status").value(status.value()),
                jsonPath("$.error").value(status.getReasonPhrase()),
                jsonPath("$.message").isString(),
                jsonPath("$.path").value(path),
                jsonPath("$.fieldErrors").isArray());
    }

    // Errors that aren't about specific inputs still send fieldErrors, as an empty list
    static ResultMatcher apiErrorWithoutFieldErrors(HttpStatus status, String path) {
        return ResultMatcher.matchAll(
                apiError(status, path),
                jsonPath("$.fieldErrors").isEmpty());
    }
}
