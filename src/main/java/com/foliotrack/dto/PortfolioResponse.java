package com.foliotrack.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioResponse(
        Long userId,
        String name,
        BigDecimal cashBalance,
        List<HoldingResponse> holdings,
        BigDecimal totalMarketValue,
        BigDecimal totalCostBasis,
        BigDecimal totalUnrealizedPnl,
        // cash + market value of all holdings
        BigDecimal totalValue) {
}
