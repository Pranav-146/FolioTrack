package com.foliotrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

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

    // Hibernate sets this on insert and on every update, so price changes are timestamped automatically
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instrument() {
    }

    public Instrument(String symbol, String name, BigDecimal currentPrice) {
        this.symbol = symbol;
        this.name = name;
        this.currentPrice = currentPrice;
    }

    public void updatePrice(BigDecimal newPrice) {
        this.currentPrice = newPrice;
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
