package com.foliotrack.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: the related row is only loaded when used; queries that need it fetch-join instead
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private Instrument instrument;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "avg_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal avgCost;

    protected Holding() {
    }

    public Holding(User user, Instrument instrument, int quantity, BigDecimal avgCost) {
        this.user = user;
        this.instrument = instrument;
        this.quantity = quantity;
        this.avgCost = avgCost;
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

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getAvgCost() {
        return avgCost;
    }
}
