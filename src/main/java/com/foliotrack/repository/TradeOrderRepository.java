package com.foliotrack.repository;

import com.foliotrack.domain.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {
}
