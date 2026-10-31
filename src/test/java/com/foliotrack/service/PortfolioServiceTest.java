package com.foliotrack.service;

import com.foliotrack.domain.Holding;
import com.foliotrack.domain.Instrument;
import com.foliotrack.domain.User;
import com.foliotrack.dto.HoldingResponse;
import com.foliotrack.dto.PortfolioResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.HoldingRepository;
import com.foliotrack.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pure unit test: Mockito replaces the repositories, so no Spring context or database is started
@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private HoldingRepository holdingRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    private final User alice = new User("Alice Johnson", "alice@example.com", new BigDecimal("1000.00"));

    @Test
    void emptyPortfolioHasZeroTotalsAndTotalValueEqualToCash() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(alice));
        when(holdingRepository.findByUserIdWithInstrument(USER_ID)).thenReturn(List.of());

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.userId()).isEqualTo(USER_ID);
        assertThat(portfolio.name()).isEqualTo("Alice Johnson");
        assertThat(portfolio.cashBalance()).isEqualTo("1000.00");
        assertThat(portfolio.holdings()).isEmpty();
        assertThat(portfolio.totalMarketValue()).isEqualTo("0.00");
        assertThat(portfolio.totalCostBasis()).isEqualTo("0.00");
        assertThat(portfolio.totalUnrealizedPnl()).isEqualTo("0.00");
        assertThat(portfolio.totalValue()).isEqualTo("1000.00");
    }

    @Test
    void computesPerHoldingAndTotalValuesForProfitAndLoss() {
        Instrument aapl = new Instrument("AAPL", "Apple Inc.", new BigDecimal("227.50"));
        Instrument tsla = new Instrument("TSLA", "Tesla Inc.", new BigDecimal("248.90"));
        // 10 AAPL bought at 200.00 -> in profit; 4 TSLA bought at 300.10 -> at a loss
        Holding aaplHolding = new Holding(alice, aapl, 10, new BigDecimal("200.00"));
        Holding tslaHolding = new Holding(alice, tsla, 4, new BigDecimal("300.10"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(alice));
        when(holdingRepository.findByUserIdWithInstrument(USER_ID)).thenReturn(List.of(aaplHolding, tslaHolding));

        PortfolioResponse portfolio = portfolioService.getPortfolio(USER_ID);

        assertThat(portfolio.holdings()).hasSize(2);
        HoldingResponse aaplRow = portfolio.holdings().get(0);
        assertThat(aaplRow.symbol()).isEqualTo("AAPL");
        assertThat(aaplRow.name()).isEqualTo("Apple Inc.");
        assertThat(aaplRow.quantity()).isEqualTo(10);
        assertThat(aaplRow.avgCost()).isEqualTo("200.00");
        assertThat(aaplRow.currentPrice()).isEqualTo("227.50");
        assertThat(aaplRow.marketValue()).isEqualTo("2275.00");
        assertThat(aaplRow.costBasis()).isEqualTo("2000.00");
        assertThat(aaplRow.unrealizedPnl()).isEqualTo("275.00");

        HoldingResponse tslaRow = portfolio.holdings().get(1);
        assertThat(tslaRow.marketValue()).isEqualTo("995.60");
        assertThat(tslaRow.costBasis()).isEqualTo("1200.40");
        assertThat(tslaRow.unrealizedPnl()).isEqualTo("-204.80");

        assertThat(portfolio.totalMarketValue()).isEqualTo("3270.60");
        assertThat(portfolio.totalCostBasis()).isEqualTo("3200.40");
        assertThat(portfolio.totalUnrealizedPnl()).isEqualTo("70.20");
        assertThat(portfolio.totalValue()).isEqualTo("4270.60");
    }

    @Test
    void unknownUserThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getPortfolio(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99");
        verify(holdingRepository, never()).findByUserIdWithInstrument(99L);
    }
}
