package com.foliotrack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Size cap keeps prompts (and LLM cost) bounded; a portfolio question never needs 500 characters
public record AskRequest(
        @NotBlank
        @Size(max = 500)
        String question) {
}
