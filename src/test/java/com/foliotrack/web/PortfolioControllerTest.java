package com.foliotrack.web;

import com.foliotrack.dto.HoldingResponse;
import com.foliotrack.dto.PortfolioResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static com.foliotrack.web.ApiErrorMatchers.apiErrorWithoutFieldErrors;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioService portfolioService;

    @Test
    void getPortfolioReturnsCashHoldingsAndTotals() throws Exception {
        HoldingResponse aapl = new HoldingResponse("AAPL", "Apple Inc.", 10,
                new BigDecimal("200.00"), new BigDecimal("227.50"),
                new BigDecimal("2275.00"), new BigDecimal("2000.00"), new BigDecimal("275.00"));
        when(portfolioService.getPortfolio(1L)).thenReturn(new PortfolioResponse(1L, "Alice Johnson",
                new BigDecimal("98000.00"), List.of(aapl),
                new BigDecimal("2275.00"), new BigDecimal("2000.00"), new BigDecimal("275.00"),
                new BigDecimal("100275.00")));

        mockMvc.perform(get("/api/users/1/portfolio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Alice Johnson"))
                .andExpect(jsonPath("$.cashBalance").value(98000.00))
                .andExpect(jsonPath("$.holdings.length()").value(1))
                .andExpect(jsonPath("$.holdings[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(10))
                .andExpect(jsonPath("$.holdings[0].avgCost").value(200.00))
                .andExpect(jsonPath("$.holdings[0].marketValue").value(2275.00))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").value(275.00))
                .andExpect(jsonPath("$.totalUnrealizedPnl").value(275.00))
                .andExpect(jsonPath("$.totalValue").value(100275.00));
    }

    @Test
    void getPortfolioForUnknownUserReturns404() throws Exception {
        when(portfolioService.getPortfolio(99L)).thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/users/99/portfolio"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, "/api/users/99/portfolio"))
                .andExpect(jsonPath("$.message").value("User not found: 99"));
    }
}
