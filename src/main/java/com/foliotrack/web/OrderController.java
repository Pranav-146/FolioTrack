package com.foliotrack.web;

import com.foliotrack.dto.OrderResponse;
import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.PlaceOrderRequest;
import com.foliotrack.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 201 for both FILLED and REJECTED: either way an order resource was created and stored.
    // The rejection is a business outcome reported in the body's status field, not an HTTP error.
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@PathVariable Long userId, @Valid @RequestBody PlaceOrderRequest request) {
        return orderService.placeOrder(userId, request);
    }

    // Constraints on plain parameters are checked by Spring MVC's built-in method validation (400 on violation).
    // Size is capped so a client can't ask for the whole table in one request.
    @GetMapping
    public PageResponse<OrderResponse> getOrders(@PathVariable Long userId,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orderService.getOrders(userId, page, size);
    }
}
