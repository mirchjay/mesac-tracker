package com.mesac.service;

import com.mesac.dto.Views.StandingRow;
import com.mesac.model.Division;
import com.mesac.model.Match;
import com.mesac.model.MatchStatus;
import com.mesac.repository.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Builds a league table from finished matches. Nothing is stored; it's worked out fresh every time. */
@Service
@Transactional(readOnly = true)
public class StandingsService {

    private final MatchRepository matches;

    public StandingsService(MatchRepository matches) {
        this.matches = matches;
    }

    public List<StandingRow> table(Long sportId, Division division) {
        List<Match> all = matches.findBySportIdAndDivision(sportId, division);

        // Every team that appears in this sport + division gets a row, even with 0 played.
        Map<Long, StandingRow> rows = new LinkedHashMap<>();
        for (Match m : all) {
            rows.computeIfAbsent(m.getHomeTeam().getId(), k -> new StandingRow(m.getHomeTeam()));
            rows.computeIfAbsent(m.getAwayTeam().getId(), k -> new StandingRow(m.getAwayTeam()));
        }

        all.stream()
                .filter(m -> m.getStatus() == MatchStatus.DONE)
                .sorted(Comparator.comparing(MatchSort::finishedAt))
                .forEach(m -> {
                    rows.get(m.getHomeTeam().getId()).addResult(m.getHomeScore(), m.getAwayScore());
                    rows.get(m.getAwayTeam().getId()).addResult(m.getAwayScore(), m.getHomeScore());
                });

        List<StandingRow> table = new ArrayList<>(rows.values());
        table.sort(Comparator.comparingInt(StandingRow::getStandingPoints).reversed()
                .thenComparing(Comparator.comparingInt(StandingRow::getDifference).reversed())
                .thenComparing(Comparator.comparingInt(StandingRow::getPointsFor).reversed())
                .thenComparing(r -> r.getTeam().getName()));
        return table;
    }

    private static final class MatchSort {
        static LocalDateTime finishedAt(Match m) {
            if (m.getEndedAt() != null) return m.getEndedAt();
            if (m.getScheduledAt() != null) return m.getScheduledAt();
            return LocalDateTime.MIN;
        }
    }
}