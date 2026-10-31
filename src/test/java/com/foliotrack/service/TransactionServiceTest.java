package com.foliotrack.service;

import com.foliotrack.domain.Instrument;
import com.foliotrack.domain.OrderSide;
import com.foliotrack.domain.OrderStatus;
import com.foliotrack.domain.TradeOrder;
import com.foliotrack.domain.Transaction;
import com.foliotrack.domain.TransactionType;
import com.foliotrack.domain.User;
import com.foliotrack.dto.PageResponse;
import com.foliotrack.dto.TransactionResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.TransactionRepository;
import com.foliotrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void getTransactionsReturnsRequestedPageMappedToResponses() {
        User alice = new User("Alice Johnson", "alice@example.com", new BigDecimal("1000.00"));
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", new BigDecimal("227.50"));
        TradeOrder order = new TradeOrder(alice, aapl, OrderSide.BUY, 2, new BigDecimal("227.50"), OrderStatus.FILLED, null);
        Transaction buy = new Transaction(alice, order, TransactionType.BUY, new BigDecimal("455.00"));
        PageRequest pageRequest = PageRequest.of(0, 20);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(transactionRepository.findByUserIdOrderByCreatedAtDescIdDesc(USER_ID, pageRequest))
                .thenReturn(new PageImpl<>(List.of(buy), pageRequest, 1));

        PageResponse<TransactionResponse> page = transactionService.getTransactions(USER_ID, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).type()).isEqualTo(TransactionType.BUY);
        assertThat(page.content().get(0).amount()).isEqualTo("455.00");
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void getTransactionsForUnknownUserThrowsNotFound() {
        when(userRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.getTransactions(99L, 0, 20))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
        verifyNoInteractions(transactionRepository);
    }
}
