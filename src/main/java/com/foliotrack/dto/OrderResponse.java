package com.foliotrack.dto;

import com.foliotrack.domain.OrderSide;
import com.foliotrack.domain.OrderStatus;
import com.foliotrack.domain.TradeOrder;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        String symbol,
        OrderSide side,
        int quantity,
        BigDecimal priceAtExecution,
        OrderStatus status,
        // null for FILLED orders
        String rejectionReason,
        Instant createdAt) {

    public static OrderResponse from(TradeOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getInstrument().getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getPriceAtExecution(),
                order.getStatus(),
                order.getRejectionReason(),
                order.getCreatedAt());
    }
}
