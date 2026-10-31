package com.foliotrack.dto;

import java.math.BigDecimal;

public record HoldingResponse(
        String symbol,
        String name,
        int quantity,
        BigDecimal avgCost,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal unrealizedPnl) {
}
