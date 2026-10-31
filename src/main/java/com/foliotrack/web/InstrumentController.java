package com.foliotrack.web;

import com.foliotrack.dto.InstrumentResponse;
import com.foliotrack.dto.UpdatePriceRequest;
import com.foliotrack.service.InstrumentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Controllers stay thin: HTTP mapping and validation only; business logic lives in the service.
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    @GetMapping
    public List<InstrumentResponse> listInstruments() {
        return instrumentService.listInstruments();
    }

    // @Valid triggers Bean Validation on the body; violations become a 400 before this method runs
    @PutMapping("/{symbol}/price")
    public InstrumentResponse updatePrice(@PathVariable String symbol,
                                          @Valid @RequestBody UpdatePriceRequest request) {
        return instrumentService.updatePrice(symbol, request.price());
    }
}
