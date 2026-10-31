package com.foliotrack.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// Constraints mirror the column: NUMERIC(12,2) with CHECK (current_price > 0),
// so bad input is rejected with a 400 before it ever reaches the database.
public record UpdatePriceRequest(
        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price) {
}
