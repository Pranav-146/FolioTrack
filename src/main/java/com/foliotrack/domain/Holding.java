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
import java.math.RoundingMode;

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

    // Weighted average: (old shares x old avg + new shares x price) / total shares.
    // Division can produce endless decimals (e.g. 3137.50 / 15), so we must pick a scale and rounding mode;
    // scale 2 matches the avg_cost column and HALF_UP is ordinary "round half away from zero".
    public void addShares(int sharesBought, BigDecimal price) {
        int newQuantity = Math.addExact(quantity, sharesBought);
        BigDecimal totalCost = avgCost.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(sharesBought)));
        this.avgCost = totalCost.divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
        this.quantity = newQuantity;
    }

    // Selling doesn't change the average cost of the shares that remain
    public void removeShares(int sharesSold) {
        this.quantity -= sharesSold;
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
