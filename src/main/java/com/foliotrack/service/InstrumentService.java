package com.foliotrack.service;

import com.foliotrack.domain.Instrument;
import com.foliotrack.dto.InstrumentResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.InstrumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    // Constructor injection: Spring passes in the repository bean. Dependencies are final and explicit,
    // and tests can construct the service with mocks without any Spring magic.
    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    @Transactional(readOnly = true)
    public List<InstrumentResponse> listInstruments() {
        return instrumentRepository.findAllByOrderBySymbolAsc().stream()
                .map(InstrumentResponse::from)
                .toList();
    }

    @Transactional
    public InstrumentResponse updatePrice(String symbol, BigDecimal newPrice) {
        String normalizedSymbol = symbol.toUpperCase(Locale.ROOT);
        Instrument instrument = instrumentRepository.findBySymbol(normalizedSymbol)
                .orElseThrow(() -> new NotFoundException("Instrument not found: " + normalizedSymbol));

        // No save() call: the entity is managed inside this transaction, so Hibernate detects the
        // change (dirty checking) and issues the UPDATE when the transaction commits.
        instrument.updatePrice(newPrice);
        return InstrumentResponse.from(instrument);
    }
}
