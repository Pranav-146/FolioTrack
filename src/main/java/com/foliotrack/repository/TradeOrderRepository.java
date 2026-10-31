package com.foliotrack.repository;

import com.foliotrack.domain.TradeOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, Long> {

    // Newest first, served by the (user_id, created_at DESC) index; id breaks ties between same-instant orders.
    // The entity graph fetches each order's instrument in the same query, since the response needs its symbol.
    @EntityGraph(attributePaths = "instrument")
    Page<TradeOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
}
