package com.foliotrack.web;

import com.foliotrack.domain.OrderSide;
import com.foliotrack.domain.OrderStatus;
import com.foliotrack.dto.OrderResponse;
import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.PlaceOrderRequest;
import com.foliotrack.exception.InvalidOrderException;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.service.OrderService;
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
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final String ORDERS_URL = "/api/users/1/orders";
    private static final Instant CREATED_AT = Instant.parse("2026-10-31T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    // ---------- POST /orders ----------

    @Test
    void filledOrderReturns201WithOrder() throws Exception {
        when(orderService.placeOrder(eq(1L), any())).thenReturn(new OrderResponse(10L, "AAPL", OrderSide.BUY, 10,
                new BigDecimal("227.50"), OrderStatus.FILLED, null, CREATED_AT));

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"AAPL\", \"side\": \"BUY\", \"quantity\": 10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10))
                .andExpect(jsonPath("$.priceAtExecution").value(227.50))
                .andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.createdAt").value("2026-10-31T10:00:00Z"));

        // The JSON body was mapped onto the request record exactly as sent
        verify(orderService).placeOrder(1L, new PlaceOrderRequest("AAPL", OrderSide.BUY, 10));
    }

    // A rejection is a business outcome, not an HTTP error: the REJECTED order was still created and stored
    @Test
    void rejectedOrderStillReturns201WithStatusAndReason() throws Exception {
        String reason = "Insufficient cash: order costs 227500.00, available 100000.00";
        when(orderService.placeOrder(eq(1L), any())).thenReturn(new OrderResponse(11L, "AAPL", OrderSide.BUY, 1000,
                new BigDecimal("227.50"), OrderStatus.REJECTED, reason, CREATED_AT));

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"AAPL\", \"side\": \"BUY\", \"quantity\": 1000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value(reason));
    }

    @Test
    void orderWithAllFieldsMissingReturns400ListingEveryInvalidField() throws Exception {
        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(containsInAnyOrder("symbol", "side", "quantity")));

        verifyNoInteractions(orderService);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void orderWithNonPositiveQuantityReturns400(int quantity) throws Exception {
        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"AAPL\", \"side\": \"BUY\", \"quantity\": " + quantity + "}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.fieldErrors.length()").value(1))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));

        verifyNoInteractions(orderService);
    }

    @Test
    void orderWithBlankSymbolReturns400() throws Exception {
        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"  \", \"side\": \"SELL\", \"quantity\": 1}"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("symbol"));

        verifyNoInteractions(orderService);
    }

    @Test
    void orderForUnknownSymbolReturns404() throws Exception {
        when(orderService.placeOrder(eq(1L), any())).thenThrow(new NotFoundException("Instrument not found: XYZ"));

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"XYZ\", \"side\": \"BUY\", \"quantity\": 1}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, ORDERS_URL))
                .andExpect(jsonPath("$.message").value("Instrument not found: XYZ"));
    }

    @Test
    void orderForUnknownUserReturns404() throws Exception {
        when(orderService.placeOrder(eq(99L), any())).thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(post("/api/users/99/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"AAPL\", \"side\": \"BUY\", \"quantity\": 1}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, "/api/users/99/orders"))
                .andExpect(jsonPath("$.message").value("User not found: 99"));
    }

    // The service re-checks its own rules for callers other than this controller; if it ever throws,
    // the handler must still answer 400 with the standard shape
    @Test
    void invalidOrderExceptionFromServiceReturns400() throws Exception {
        when(orderService.placeOrder(eq(1L), any())).thenThrow(new InvalidOrderException("Quantity must be greater than 0"));

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"symbol\": \"AAPL\", \"side\": \"BUY\", \"quantity\": 1}"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.message").value("Quantity must be greater than 0"));
    }

    // ---------- GET /orders ----------

    @Test
    void getOrdersUsesDefaultPagingAndReturnsPage() throws Exception {
        OrderResponse order = new OrderResponse(10L, "AAPL", OrderSide.BUY, 10, new BigDecimal("227.50"),
                OrderStatus.FILLED, null, CREATED_AT);
        when(orderService.getOrders(1L, 0, 20)).thenReturn(new PageResponse<>(List.of(order), 0, 20, 1, 1));

        mockMvc.perform(get(ORDERS_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].status").value("FILLED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getOrdersPassesRequestedPageAndSizeToService() throws Exception {
        when(orderService.getOrders(1L, 2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));

        mockMvc.perform(get(ORDERS_URL).param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5));

        verify(orderService).getOrders(1L, 2, 5);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void getOrdersWithSizeOutOfRangeReturns400(String size) throws Exception {
        mockMvc.perform(get(ORDERS_URL).param("size", size))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getOrdersWithNegativePageReturns400() throws Exception {
        mockMvc.perform(get(ORDERS_URL).param("page", "-1"))
                .andExpect(apiError(HttpStatus.BAD_REQUEST, ORDERS_URL))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("page"));

        verifyNoInteractions(orderService);
    }

    @Test
    void getOrdersForUnknownUserReturns404() throws Exception {
        when(orderService.getOrders(99L, 0, 20)).thenThrow(new NotFoundException("User not found: 99"));

        mockMvc.perform(get("/api/users/99/orders"))
                .andExpect(apiErrorWithoutFieldErrors(HttpStatus.NOT_FOUND, "/api/users/99/orders"));
    }
}
