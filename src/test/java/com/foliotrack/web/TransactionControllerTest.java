package com.foliotrack.web;

import com.foliotrack.domain.TransactionType;
import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.TransactionResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.foliotrack.web.ApiErrorMatchers.apiError;
import static com.foliotrack.web.ApiErrorMatchers.apiErrorWithoutFieldErrors;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final String TRANSACTIONS_URL = "/api/users/1/transactions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void getTransactionsUsesDefaultPagingAndReturnsPage() throws Exception {
        TransactionResponse buy = new TransactionResponse(5L, 10L, TransactionType.BUY, new BigDecimal("2275.00"),
                Instant.parse("2026-10-31T10:00:00Z"));
        when(transactionService.getTransactions(1L, 0, 20)).thenReturn(new PageResponse<>(List.of(buy), 0, 20, 1, 1));

        mockMvc.perform(get(TRANSACTIONS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.content[0].orderId").value(10))
                .andExpect(jsonPath("$.content[0].type").value("BUY"))
                .andExpect(jsonPath("$.content[0].amount").value(2275.00))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getTransactionsPassesRequestedPageAndSizeToService() throws Exception {
        when(transactionService.getTransactions(1L, 1, 100)).thenReturn(new PageResponse<>(List.of(), 1, 100, 0, 0));

        mockMvc.perform(get(TRANSACTIONS_URL).param("page", "1").param("size", "100"))
                .andExpect(status().isOk());

        verify(transactionService).getTransactions(1L, 1, 100);
    }

    @Test
    void getTransactionsWithSizeAboveCapReturns400() throws Exception {
        mockMvc.perform(get(TRANSACTIONS_URL).param("size", "500"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, TRANSACTIONS_URL))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));

        verifyNoInteractions(transactionService);
    }

    @Test
    void getTransactionsForUnknownUserReturns404() throws Exception {
        when(transactionService.getTransactions(99L, 0, 20)).thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/users/99/transactions"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, "/api/users/99/transactions"))
                .andExpect(jsonPath("$.message").value("User not found: 99"));
    }
}
