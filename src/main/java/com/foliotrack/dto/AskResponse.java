package com.foliotrack.dto;

// sql is returned for transparency, so the user (or a reviewer) can check what the answer is based on.
// It is null when the question was outside what the views can answer and no query was run.
public record AskResponse(
        String question,
        String sql,
        int rowCount,
        String answer) {
}
