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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Each test is one order rule from SPEC.md. Repositories are mocks, so the tests check the service's
// decisions (what it saves, deletes and changes) without a database.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private TradeOrderRepository tradeOrderRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private OrderService orderService;

    private final Instrument aapl = new Instrument("AAPL", "Apple Inc.", new BigDecimal("227.50"));

    // ---------- BUY ----------

    @Test
    void buyWithoutExistingHoldingDeductsCashCreatesHoldingAndRecordsFilledOrderAndTransaction() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        givenNoAaplHolding(alice);

        OrderResponse response = orderService.placeOrder(USER_ID, request(OrderSide.BUY, 10));

        // 10 x 227.50 = 2275.00
        assertThat(alice.getCashBalance()).isEqualTo("7725.00");

        Holding newHolding = captureSavedHolding();
        assertThat(newHolding.getUser()).isSameAs(alice);
        assertThat(newHolding.getInstrument()).isSameAs(aapl);
        assertThat(newHolding.getQuantity()).isEqualTo(10);
        assertThat(newHolding.getAvgCost()).isEqualTo("227.50");

        TradeOrder order = captureSavedOrder();
        assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(order.getQuantity()).isEqualTo(10);
        assertThat(order.getPriceAtExecution()).isEqualTo("227.50");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.getRejectionReason()).isNull();

        Transaction transaction = captureSavedTransaction();
        assertThat(transaction.getOrder()).isSameAs(order);
        assertThat(transaction.getUser()).isSameAs(alice);
        assertThat(transaction.getType()).isEqualTo(TransactionType.BUY);
        assertThat(transaction.getAmount()).isEqualTo("2275.00");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void buyIntoExistingHoldingRecomputesWeightedAverageCost() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        Holding holding = givenAaplHolding(alice, 10, "200.00");

        orderService.placeOrder(USER_ID, request(OrderSide.BUY, 5));

        // (10 x 200.00 + 5 x 227.50) / 15 = 3137.50 / 15 = 209.1666... -> rounded to 209.17
        assertThat(holding.getQuantity()).isEqualTo(15);
        assertThat(holding.getAvgCost()).isEqualTo("209.17");
        assertThat(alice.getCashBalance()).isEqualTo("8862.50");
        // Existing holding is a managed entity: JPA dirty checking persists the change, no save() needed
        verify(holdingRepository, never()).save(any());
        assertThat(captureSavedOrder().getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void buyCostingExactlyAllAvailableCashIsFilled() {
        User alice = givenUserWithCash("2275.00");
        givenAapl();
        givenNoAaplHolding(alice);

        OrderResponse response = orderService.placeOrder(USER_ID, request(OrderSide.BUY, 10));

        // The rule is "reject if cash < cost", so cash == cost must succeed
        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(alice.getCashBalance()).isEqualTo("0.00");
    }

    @Test
    void buyWithInsufficientCashIsRecordedAsRejectedAndChangesNothing() {
        User alice = givenUserWithCash("1000.00");
        givenAapl();

        OrderResponse response = orderService.placeOrder(USER_ID, request(OrderSide.BUY, 10));

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.rejectionReason()).contains("Insufficient cash");

        TradeOrder order = captureSavedOrder();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).contains("Insufficient cash");
        assertThat(order.getPriceAtExecution()).isEqualTo("227.50");

        assertThat(alice.getCashBalance()).isEqualTo("1000.00");
        verify(holdingRepository, never()).save(any());
        // No money moved, so there is no transaction row
        verify(transactionRepository, never()).save(any());
    }

    // ---------- SELL ----------

    @Test
    void sellPartOfHoldingAddsCashReducesQuantityAndKeepsAvgCost() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        Holding holding = givenAaplHolding(alice, 10, "200.00");

        OrderResponse response = orderService.placeOrder(USER_ID, request(OrderSide.SELL, 4));

        // 4 x 227.50 = 910.00
        assertThat(alice.getCashBalance()).isEqualTo("10910.00");
        assertThat(holding.getQuantity()).isEqualTo(6);
        // Selling realises P&L but doesn't change the cost of the shares still held
        assertThat(holding.getAvgCost()).isEqualTo("200.00");
        verify(holdingRepository, never()).delete(any());

        TradeOrder order = captureSavedOrder();
        assertThat(order.getSide()).isEqualTo(OrderSide.SELL);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FILLED);

        Transaction transaction = captureSavedTransaction();
        assertThat(transaction.getType()).isEqualTo(TransactionType.SELL);
        assertThat(transaction.getAmount()).isEqualTo("910.00");
        assertThat(transaction.getOrder()).isSameAs(order);

        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void sellEntireHoldingDeletesIt() {
        User alice = givenUserWithCash("0.00");
        givenAapl();
        Holding holding = givenAaplHolding(alice, 10, "200.00");

        orderService.placeOrder(USER_ID, request(OrderSide.SELL, 10));

        // The holdings table has CHECK (quantity > 0), so a fully sold position must be deleted
        verify(holdingRepository).delete(holding);
        assertThat(alice.getCashBalance()).isEqualTo("2275.00");
        assertThat(captureSavedTransaction().getAmount()).isEqualTo("2275.00");
    }

    @Test
    void sellMoreThanHeldIsRecordedAsRejectedAndChangesNothing() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        Holding holding = givenAaplHolding(alice, 10, "200.00");

        OrderResponse response = orderService.placeOrder(USER_ID, request(OrderSide.SELL, 11));

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.rejectionReason()).contains("Insufficient holdings");
        assertThat(captureSavedOrder().getStatus()).isEqualTo(OrderStatus.REJECTED);

        assertThat(alice.getCashBalance()).isEqualTo("10000.00");
        assertThat(holding.getQuantity()).isEqualTo(10);
        verify(holdingRepository, never()).delete(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void sellWithoutAnyHoldingIsRecordedAsRejected() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        givenNoAaplHolding(alice);

        OrderResponse response = orderService.placeOrder(USER_ID, request(OrderSide.SELL, 1));

        assertThat(response.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(response.rejectionReason()).contains("Insufficient holdings");
        assertThat(alice.getCashBalance()).isEqualTo("10000.00");
        verify(transactionRepository, never()).save(any());
    }

    // ---------- Validation, lookups and locking ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -5})
    void nonPositiveOrMissingQuantityIsInvalidAndTouchesNoRepository(Integer quantity) {
        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, new PlaceOrderRequest("AAPL", OrderSide.BUY, quantity)))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("Quantity");
        // Can't be recorded as REJECTED either: orders.quantity has CHECK (quantity > 0)
        verifyNoInteractions(userRepository, instrumentRepository, holdingRepository,
                tradeOrderRepository, transactionRepository);
    }

    @Test
    void unknownSymbolThrowsNotFoundAndRecordsNothing() {
        when(instrumentRepository.findBySymbol("ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(USER_ID, new PlaceOrderRequest("ZZZZ", OrderSide.BUY, 1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ZZZZ");
        verify(tradeOrderRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void unknownUserThrowsNotFoundAndRecordsNothing() {
        givenAapl();
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.placeOrder(99L, request(OrderSide.BUY, 1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
        verify(tradeOrderRepository, never()).save(any());
    }

    @Test
    void symbolIsNormalisedToUpperCase() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        givenNoAaplHolding(alice);

        OrderResponse response = orderService.placeOrder(USER_ID, new PlaceOrderRequest("aapl", OrderSide.BUY, 1));

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void userRowIsLoadedWithPessimisticLockNotPlainFind() {
        User alice = givenUserWithCash("10000.00");
        givenAapl();
        givenNoAaplHolding(alice);

        orderService.placeOrder(USER_ID, request(OrderSide.BUY, 1));

        // Concurrency guarantee: the user row must be read with SELECT ... FOR UPDATE
        verify(userRepository).findByIdForUpdate(USER_ID);
        verify(userRepository, never()).findById(any());
    }

    // ---------- Order history ----------

    @Test
    void getOrdersReturnsRequestedPageMappedToResponses() {
        User alice = new User("Alice Johnson", "alice@example.com", new BigDecimal("1000.00"));
        TradeOrder filled = new TradeOrder(alice, aapl, OrderSide.BUY, 2, new BigDecimal("227.50"), OrderStatus.FILLED, null);
        TradeOrder rejected = new TradeOrder(alice, aapl, OrderSide.SELL, 9, new BigDecimal("227.50"),
                OrderStatus.REJECTED, "Insufficient holdings");
        PageRequest pageRequest = PageRequest.of(1, 2);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(tradeOrderRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID, pageRequest))
                .thenReturn(new PageImpl<>(List.of(filled, rejected), pageRequest, 5));

        PageResponse<OrderResponse> page = orderService.getOrders(USER_ID, 1, 2);

        assertThat(page.content()).extracting(OrderResponse::status)
                .containsExactly(OrderStatus.FILLED, OrderStatus.REJECTED);
        assertThat(page.content().get(1).rejectionReason()).isEqualTo("Insufficient holdings");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void getOrdersForUnknownUserThrowsNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> orderService.getOrders(99L, 0, 20))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
        verifyNoInteractions(tradeOrderRepository);
    }

    // ---------- Helpers ----------

    private static PlaceOrderRequest request(OrderSide side, int quantity) {
        return new PlaceOrderRequest("AAPL", side, quantity);
    }

    private User givenUserWithCash(String cash) {
        User user = new User("Alice Johnson", "alice@example.com", new BigDecimal(cash));
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    private void givenAapl() {
        when(instrumentRepository.findBySymbol("AAPL")).thenReturn(Optional.of(aapl));
    }

    private void givenNoAaplHolding(User user) {
        when(holdingRepository.findByUserAndInstrument(user, aapl)).thenReturn(Optional.empty());
    }

    private Holding givenAaplHolding(User user, int quantity, String avgCost) {
        Holding holding = new Holding(user, aapl, quantity, new BigDecimal(avgCost));
        when(holdingRepository.findByUserAndInstrument(user, aapl)).thenReturn(Optional.of(holding));
        return holding;
    }

    private Holding captureSavedHolding() {
        ArgumentCaptor<Holding> captor = ArgumentCaptor.forClass(Holding.class);
        verify(holdingRepository).save(captor.capture());
        return captor.getValue();
    }

    private TradeOrder captureSavedOrder() {
        ArgumentCaptor<TradeOrder> captor = ArgumentCaptor.forClass(TradeOrder.class);
        verify(tradeOrderRepository).save(captor.capture());
        return captor.getValue();
    }

    private Transaction captureSavedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        return captor.getValue();
    }
}
