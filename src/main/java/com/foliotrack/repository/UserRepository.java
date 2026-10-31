package com.foliotrack.repository;

import com.foliotrack.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

// Spring Data generates the implementation at startup; JpaRepository already provides findById, save, etc.
public interface UserRepository extends JpaRepository<User, Long> {

    // PESSIMISTIC_WRITE makes Hibernate add a row lock (FOR UPDATE / FOR NO KEY UPDATE on Postgres) to the SELECT.
    // A second transaction asking for the same user blocks here until the first commits or rolls back,
    // so concurrent orders for one user run strictly one after another.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(Long id);
}
