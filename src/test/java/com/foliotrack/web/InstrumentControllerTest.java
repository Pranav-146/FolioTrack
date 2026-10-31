package com.foliotrack.web;

import com.foliotrack.dto.InstrumentResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.service.InstrumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.foliotrack.web.ApiErrorMatchers.apiError;
import static com.foliotrack.web.ApiErrorMatchers.apiErrorWithoutFieldErrors;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @WebMvcTest starts only the web layer (this controller, GlobalExceptionHandler, Jackson, validation):
// no database, no services. The service is a Mockito mock, so these tests check HTTP behaviour only:
// URL mapping, status codes, JSON shape and request validation.
@WebMvcTest(InstrumentController.class)
class InstrumentControllerTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-31T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InstrumentService instrumentService;

    @Test
    void listInstrumentsReturnsAllInstrumentsWithPrices() throws Exception {
        when(instrumentService.listInstruments()).thenReturn(List.of(
                new InstrumentResponse("AAPL", "Apple Inc.", new BigDecimal("227.50"), UPDATED_AT),
                new InstrumentResponse("MSFT", "Microsoft Corporation", new BigDecimal("415.30"), UPDATED_AT)));

        mockMvc.perform(get("/api/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].name").value("Apple Inc."))
                .andExpect(jsonPath("$[0].currentPrice").value(227.50))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-10-31T10:00:00Z"))
                .andExpect(jsonPath("$[1].symbol").value("MSFT"));
    }

    @Test
    void updatePriceReturnsUpdatedInstrument() throws Exception {
        when(instrumentService.updatePrice("AAPL", new BigDecimal("230.10")))
                .thenReturn(new InstrumentResponse("AAPL", "Apple Inc.", new BigDecimal("230.10"), UPDATED_AT));

        mockMvc.perform(put("/api/instruments/AAPL/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 230.10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.currentPrice").value(230.10));

        verify(instrumentService).updatePrice("AAPL", new BigDecimal("230.10"));
    }

    @Test
    void updatePriceForUnknownSymbolReturns404() throws Exception {
        when(instrumentService.updatePrice(any(), any())).thenThrow(new NotFoundException("Instrument not found: XYZ"));

        mockMvc.perform(put("/api/instruments/XYZ/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 10.00}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, "/api/instruments/XYZ/price"))
                .andExpect(jsonPath("$.message").value("Instrument not found: XYZ"));
    }

    // Zero, negative, more than 2 decimals, more than 10 integer digits: each would violate the
    // NUMERIC(12,2) column or its CHECK (current_price > 0), so they must stop at the controller
    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00", "0.001", "12.345", "12345678901.00"})
    void updatePriceWithInvalidPriceReturns400WithFieldError(String price) throws Exception {
        mockMvc.perform(put("/api/instruments/AAPL/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": " + price + "}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, "/api/instruments/AAPL/price"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[0].message").isNotEmpty());

        verifyNoInteractions(instrumentService);
    }

    @Test
    void updatePriceWithMissingPriceReturns400() throws Exception {
        mockMvc.perform(put("/api/instruments/AAPL/price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, "/api/instruments/AAPL/price"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("price"));

        verifyNoInteractions(instrumentService);
    }
}
