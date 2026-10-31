package com.foliotrack.service;

import com.foliotrack.domain.Instrument;
import com.foliotrack.dto.InstrumentResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.InstrumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private InstrumentService instrumentService;

    @Test
    void listInstrumentsMapsEntitiesToResponses() {
        when(instrumentRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(
                new Instrument("AAPL", "Apple Inc.", new BigDecimal("227.50")),
                new Instrument("MSFT", "Microsoft Corporation", new BigDecimal("415.30"))));

        List<InstrumentResponse> instruments = instrumentService.listInstruments();

        assertThat(instruments).extracting(InstrumentResponse::symbol).containsExactly("AAPL", "MSFT");
        assertThat(instruments.get(0).name()).isEqualTo("Apple Inc.");
        assertThat(instruments.get(0).currentPrice()).isEqualTo("227.50");
    }

    @Test
    void updatePriceChangesEntityAndReturnsResponse() {
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", new BigDecimal("227.50"));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(aapl));
        Instant beforeUpdate = Instant.now();

        InstrumentResponse response = instrumentService.updatePrice("AAPL", new BigDecimal("250.00"));

        // The managed entity is modified; JPA dirty checking writes it on commit, so no save() is needed
        assertThat(aapl.getCurrentPrice()).isEqualTo("250.00");
        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.currentPrice()).isEqualTo("250.00");
        // The response must carry the new timestamp, not the one from before the update
        assertThat(response.updatedAt()).isAfterOrEqualTo(beforeUpdate);
    }

    @Test
    void updatePriceNormalisesSymbolToUpperCase() {
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", new BigDecimal("227.50"));
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(aapl));

        InstrumentResponse response = instrumentService.updatePrice("aapl", new BigDecimal("230.00"));

        assertThat(response.currentPrice()).isEqualTo("230.00");
    }

    @Test
    void updatePriceForUnknownSymbolThrowsNotFound() {
        when(instrumentRepository.findBySymbol("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> instrumentService.updatePrice("ZZZZ", new BigDecimal("1.00")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ZZZZ");
    }
}
