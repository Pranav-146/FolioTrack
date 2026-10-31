package com.foliotrack.dto;

import com.foliotrack.domain.Transaction;
import com.foliotrack.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id, Long orderId, TransactionType type, BigDecimal amount, Instant createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                // Reading only the id of a LAZY association doesn't trigger a query; the proxy already holds it
                transaction.getOrder().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getCreatedAt());
    }
}
