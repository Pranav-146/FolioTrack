package com.foliotrack.repository;

import com.foliotrack.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    // Derived query: Spring Data builds the SQL from the method name
    Optional<Instrument> findBySymbol(String symbol);

    List<Instrument> findAllByOrderBySymbolAsc();
}
