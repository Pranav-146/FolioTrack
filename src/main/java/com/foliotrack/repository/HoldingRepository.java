package com.foliotrack.repository;

import com.foliotrack.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    // JOIN FETCH loads each holding's instrument in the same query. Without it, the LAZY
    // association would fire one extra SELECT per holding (the N+1 query problem).
    @Query("""
            SELECT h FROM Holding h
            JOIN FETCH h.instrument i
            WHERE h.user.id = :userId
            ORDER BY i.symbol
            """)
    List<Holding> findByUserIdWithInstrument(Long userId);
}
