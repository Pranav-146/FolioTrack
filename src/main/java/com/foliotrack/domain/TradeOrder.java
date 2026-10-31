package com.foliotrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

// Named TradeOrder, not Order: ORDER is a reserved word in SQL and JPQL ("ORDER BY")
@Entity
@Table(name = "orders")
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    // STRING stores "BUY"/"SELL"; ORDINAL would store 0/1 and silently break if the enum were reordered
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private OrderSide side;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "price_at_execution", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceAtExecution;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TradeOrder() {
    }

    public TradeOrder(User user, Instrument instrument, OrderSide side, int quantity,
                      BigDecimal priceAtExecution, OrderStatus status, String rejectionReason) {
        this.user = user;
        this.instrument = instrument;
        this.side = side;
        this.quantity = quantity;
        this.priceAtExecution = priceAtExecution;
        this.status = status;
        this.rejectionReason = rejectionReason;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instrument getInstrument() {
        return instrument;
    }

    public OrderSide getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getPriceAtExecution() {
        return priceAtExecution;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
