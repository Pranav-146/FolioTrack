package com.foliotrack.service;

import com.foliotrack.dto.OrderResponse;
import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.PlaceOrderRequest;
import com.foliotrack.repository.HoldingRepository;
import com.foliotrack.repository.InstrumentRepository;
import com.foliotrack.repository.TradeOrderRepository;
import com.foliotrack.repository.TransactionRepository;
import com.foliotrack.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final UserRepository userRepository;
    private final InstrumentRepository instrumentRepository;
    private final HoldingRepository holdingRepository;
    private final TradeOrderRepository tradeOrderRepository;
    private final TransactionRepository transactionRepository;

    public OrderService(UserRepository userRepository,
                        InstrumentRepository instrumentRepository,
                        HoldingRepository holdingRepository,
                        TradeOrderRepository tradeOrderRepository,
                        TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.instrumentRepository = instrumentRepository;
        this.holdingRepository = holdingRepository;
        this.tradeOrderRepository = tradeOrderRepository;
        this.transactionRepository = transactionRepository;
    }

    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public PageResponse<OrderResponse> getOrders(Long userId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
