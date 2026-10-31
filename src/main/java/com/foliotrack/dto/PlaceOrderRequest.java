package com.foliotrack.dto;

import com.foliotrack.domain.OrderSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Jackson maps "side": "BUY"/"SELL" straight onto the enum; any other value is a 400 (unreadable body).
public record PlaceOrderRequest(
        @NotBlank
        String symbol,
        @NotNull
        OrderSide side,
        @NotNull
        @Positive
        Integer quantity) {
}
