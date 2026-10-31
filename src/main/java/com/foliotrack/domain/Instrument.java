package com.foliotrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "instruments")
public class Instrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "current_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentPrice;

    // Set in Java rather than via @UpdateTimestamp: Hibernate only fills that at flush (commit) time,
    // which is after the service has already built its response, so the API would return a stale value.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instrument() {
    }

    public Instrument(String symbol, String name, BigDecimal currentPrice) {
        this.symbol = symbol;
        this.name = name;
        this.currentPrice = currentPrice;
        this.updatedAt = Instant.now();
    }

    public void updatePrice(BigDecimal newPrice) {
        this.currentPrice = newPrice;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
