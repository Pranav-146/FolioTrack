package com.foliotrack.service;

import com.foliotrack.domain.Holding;
import com.foliotrack.domain.Instrument;
import com.foliotrack.domain.OrderSide;
import com.foliotrack.domain.OrderStatus;
import com.foliotrack.domain.TradeOrder;
import com.foliotrack.domain.Transaction;
import com.foliotrack.domain.TransactionType;
import com.foliotrack.domain.User;
import com.foliotrack.dto.OrderResponse;
import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.PlaceOrderRequest;
import com.foliotrack.exception.InvalidOrderException;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.HoldingRepository;
import com.foliotrack.repository.InstrumentRepository;
import com.foliotrack.repository.TradeOrderRepository;
import com.foliotrack.repository.TransactionRepository;
import com.foliotrack.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

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

    // The whole order is ONE database transaction: Spring's proxy opens it before this method runs and
    // commits after it returns. If anything throws (a bug, a DB constraint, a lost connection), every
    // change made here, including the order and transaction INSERTs, is rolled back together.
    @Transactional
    public OrderResponse placeOrder(Long userId, PlaceOrderRequest request) {
        // Bean Validation already rejects these at the controller; checked again because the service
        // is the boundary that must hold the rule no matter who calls it
        if (request.quantity() == null || request.quantity() <= 0) {
            throw new InvalidOrderException("Quantity must be greater than 0");
        }
        int quantity = request.quantity();

        String symbol = request.symbol().toUpperCase(Locale.ROOT);
        Instrument instrument = instrumentRepository.findBySymbol(symbol)
                .orElseThrow(() -> new NotFoundException("Instrument not found: " + symbol));

        // Row lock (SELECT ... FOR UPDATE), held until commit. Any other order for this user waits here,
        // so the balance/holding checks below can't be invalidated by a concurrent order (no lost updates).
        // Taken after the instrument lookup so the lock is held for as short a time as possible.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        TradeOrder order = switch (request.side()) {
            case BUY -> executeBuy(user, instrument, quantity);
            case SELL -> executeSell(user, instrument, quantity);
        };
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> getOrders(Long userId, int page, int size) {
        // Without this check an unknown user would get an empty page instead of a 404
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("User not found: " + userId);
        }
        return PageResponse.from(
                tradeOrderRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(page, size)),
                OrderResponse::from);
    }

    private TradeOrder executeBuy(User user, Instrument instrument, int quantity) {
        BigDecimal price = instrument.getCurrentPrice();
        BigDecimal cost = price.multiply(BigDecimal.valueOf(quantity));

        // compareTo, not equals: BigDecimal.equals also compares scale, so 10.0 and 10.00 would be "different"
        if (user.getCashBalance().compareTo(cost) < 0) {
            return recordRejected(user, instrument, OrderSide.BUY, quantity,
                    "Insufficient cash: order costs %s, available %s".formatted(cost, user.getCashBalance()));
        }

        user.debit(cost);
        // Upsert: top up an existing position, or open a new one at the execution price
        holdingRepository.findByUserAndInstrument(user, instrument).ifPresentOrElse(
                holding -> holding.addShares(quantity, price),
                () -> holdingRepository.save(new Holding(user, instrument, quantity, price)));

        return recordFilled(user, instrument, OrderSide.BUY, quantity, TransactionType.BUY, cost);
    }

    private TradeOrder executeSell(User user, Instrument instrument, int quantity) {
        Optional<Holding> existing = holdingRepository.findByUserAndInstrument(user, instrument);
        int sharesHeld = existing.map(Holding::getQuantity).orElse(0);

        // isEmpty() is implied by sharesHeld < quantity (quantity > 0), but stating it makes get() below provably safe
        if (existing.isEmpty() || sharesHeld < quantity) {
            return recordRejected(user, instrument, OrderSide.SELL, quantity,
                    "Insufficient holdings: requested %d shares, held %d".formatted(quantity, sharesHeld));
        }

        Holding holding = existing.get();
        if (sharesHeld == quantity) {
            // The table requires quantity > 0, so a closed position is deleted rather than kept at 0
            holdingRepository.delete(holding);
        } else {
            holding.removeShares(quantity);
        }

        BigDecimal proceeds = instrument.getCurrentPrice().multiply(BigDecimal.valueOf(quantity));
        user.credit(proceeds);

        return recordFilled(user, instrument, OrderSide.SELL, quantity, TransactionType.SELL, proceeds);
    }

    // Changes to user and holding are not saved explicitly: they're managed entities, so Hibernate's
    // dirty checking writes them at commit. New rows (order, transaction) do need save().
    private TradeOrder recordFilled(User user, Instrument instrument, OrderSide side, int quantity,
                                    TransactionType type, BigDecimal amount) {
        TradeOrder order = new TradeOrder(user, instrument, side, quantity, instrument.getCurrentPrice(),
                OrderStatus.FILLED, null);
        tradeOrderRepository.save(order);
        transactionRepository.save(new Transaction(user, order, type, amount));
        return order;
    }

    // A rejection is a normal business outcome, not an exception: we return normally so the transaction
    // commits and the REJECTED order is kept for audit. Throwing here would roll that record back.
    private TradeOrder recordRejected(User user, Instrument instrument, OrderSide side, int quantity, String reason) {
        TradeOrder order = new TradeOrder(user, instrument, side, quantity, instrument.getCurrentPrice(),
                OrderStatus.REJECTED, reason);
        tradeOrderRepository.save(order);
        return order;
    }
}
