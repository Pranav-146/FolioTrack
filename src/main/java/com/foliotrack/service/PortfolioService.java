package com.foliotrack.service;

import com.foliotrack.domain.Holding;
import com.foliotrack.domain.User;
import com.foliotrack.dto.HoldingResponse;
import com.foliotrack.dto.PortfolioResponse;
import com.foliotrack.exception.NotFoundException;
import com.foliotrack.repository.HoldingRepository;
import com.foliotrack.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

@Service
public class PortfolioService {

    // Scale 2 so an empty portfolio reports "0.00", consistent with every other money value
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);

    private final UserRepository userRepository;
    private final HoldingRepository holdingRepository;

    public PortfolioService(UserRepository userRepository, HoldingRepository holdingRepository) {
        this.userRepository = userRepository;
        this.holdingRepository = holdingRepository;
    }

    // readOnly lets Hibernate skip dirty checking and hints the DB that no writes will happen
    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));

        List<HoldingResponse> holdings = holdingRepository.findByUserIdWithInstrument(userId).stream()
                .map(PortfolioService::toHoldingResponse)
                .toList();

        BigDecimal totalMarketValue = sum(holdings, HoldingResponse::marketValue);
        BigDecimal totalCostBasis = sum(holdings, HoldingResponse::costBasis);

        return new PortfolioResponse(
                userId,
                user.getName(),
                user.getCashBalance(),
                holdings,
                totalMarketValue,
                totalCostBasis,
                totalMarketValue.subtract(totalCostBasis),
                user.getCashBalance().add(totalMarketValue));
    }

    private static HoldingResponse toHoldingResponse(Holding holding) {
        BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
        BigDecimal currentPrice = holding.getInstrument().getCurrentPrice();
        // Price (scale 2) x whole shares (scale 0) is exact at scale 2, so no rounding is involved
        BigDecimal marketValue = currentPrice.multiply(quantity);
        BigDecimal costBasis = holding.getAvgCost().multiply(quantity);

        return new HoldingResponse(
                holding.getInstrument().getSymbol(),
                holding.getInstrument().getName(),
                holding.getQuantity(),
                holding.getAvgCost(),
                currentPrice,
                marketValue,
                costBasis,
                marketValue.subtract(costBasis));
    }

    private static BigDecimal sum(List<HoldingResponse> holdings, Function<HoldingResponse, BigDecimal> field) {
        return holdings.stream().map(field).reduce(ZERO_MONEY, BigDecimal::add);
    }
}
