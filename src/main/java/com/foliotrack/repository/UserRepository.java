package com.foliotrack.repository;

import com.foliotrack.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

// Spring Data generates the implementation at startup; JpaRepository already provides findById, save, etc.
public interface UserRepository extends JpaRepository<User, Long> {
}
