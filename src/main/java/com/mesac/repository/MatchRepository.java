package com.mesac.repository;

import com.mesac.model.Division;
import com.mesac.model.Match;
import com.mesac.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findAllByOrderByScheduledAtAsc();

    List<Match> findByStatusOrderByScheduledAtAsc(MatchStatus status);

    /** Every match for one sport + division (used to build standings). */
    List<Match> findBySportIdAndDivision(Long sportId, Division division);

    long countByStatus(MatchStatus status);

    boolean existsBySportId(Long sportId);

    boolean existsByHomeTeamIdOrAwayTeamId(Long homeTeamId, Long awayTeamId);
}
