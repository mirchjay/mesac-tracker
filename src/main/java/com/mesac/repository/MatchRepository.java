package com.mesac.repository;

import com.mesac.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    
    // Custom query methods
    List<Match> findByStatus(String status);
    List<Match> findBySportId(UUID sportId);
}