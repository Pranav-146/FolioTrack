package com.foliotrack.web;

import com.foliotrack.service.InstrumentService;
import com.foliotrack.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.foliotrack.web.ApiErrorMatchers.apiErrorWithoutFieldErrors;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// Error paths that don't belong to one controller: DB constraint failures, unexpected crashes and
// Spring MVC's own errors. Each must keep its correct status AND come back in the ApiError shape.
@WebMvcTest({OrderController.class, InstrumentController.class})
class GlobalExceptionHandlerTest {

    private static final String ORDERS_URL = "/api/users/1/orders";
    private static final String VALID_ORDER = "{\"symbol\": \"AAPL\", \"side\": \"BUY\", \"quantity\": 1}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private InstrumentService instrumentService;

    @Test
    void dataIntegrityViolationReturns409WithoutSqlDetails() throws Exception {
        when(orderService.placeOrder(eq(1L), any()))
                .thenThrow(new DataIntegrityViolationException("violates check constraint \"users_cash_balance_check\""));

        mockMvc.perform(post(ORDERS_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.CONFLICT, ORDERS_URL))
                .andExpect(jsonPath("$.message").value("Request violates a data constraint; no changes were saved"))
                .andExpect(jsonPath("$.message").value(not(containsString("constraint \""))));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingInternals() throws Exception {
        when(orderService.placeOrder(eq(1L), any())).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(post(ORDERS_URL).contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.INTERNAL_SERVER_ERROR, ORDERS_URL))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post(ORDERS_URL).contentType(MediaType.APPLICATION_JSON).content("{\"symbol\": \"AAPL\","))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.message").value(containsString("malformed")));

        verifyNoInteractions(orderService);
    }

    // Jackson can't map "HOLD" onto the OrderSide enum; the message must not expose Java class names
    @Test
    void unknownOrderSideReturns400WithoutClassNames() throws Exception {
        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"AAPL\", \"side\": \"HOLD\", \"quantity\": 1}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.message").value(containsString("side must be BUY or SELL")))
                .andExpect(jsonPath("$.message").value(not(containsString("com.foliotrack"))));

        verifyNoInteractions(orderService);
    }

    @Test
    void nonNumericUserIdReturns400() throws Exception {
        mockMvc.perform(get("/api/users/abc/orders"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.BAD_REQUEST, "/api/users/abc/orders"));

        verifyNoInteractions(orderService);
    }

    @Test
    void unsupportedMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/api/instruments"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.METHOD_NOT_ALLOWED, "/api/instruments"))
                .andExpect(header().string("Allow", containsString("GET")));
    }

    @Test
    void unknownUrlReturns404() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, "/api/does-not-exist"));
    }
}
