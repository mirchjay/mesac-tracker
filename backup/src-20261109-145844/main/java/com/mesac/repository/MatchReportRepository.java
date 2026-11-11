package com.mesac.repository;

import com.mesac.model.MatchReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchReportRepository extends JpaRepository<MatchReport, Long> {

    Optional<MatchReport> findByMatchId(Long matchId);

    List<MatchReport> findAllByOrderByUpdatedAtDesc();

    void deleteByMatchId(Long matchId);
}
