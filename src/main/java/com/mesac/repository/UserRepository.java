package com.mesac.repository;

import com.mesac.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    
    // Custom query methods
    Optional<User> findByEmail(String email);
}