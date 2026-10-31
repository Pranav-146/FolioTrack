package com.foliotrack.dto;

import com.foliotrack.domain.Instrument;

import java.math.BigDecimal;
import java.time.Instant;

// Records are immutable data carriers: constructor, getters, equals/hashCode and toString for free.
public record InstrumentResponse(String symbol, String name, BigDecimal currentPrice, Instant updatedAt) {

    public static InstrumentResponse from(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getCurrentPrice(),
                instrument.getUpdatedAt());
    }
}
