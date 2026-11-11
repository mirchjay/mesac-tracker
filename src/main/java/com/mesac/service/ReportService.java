package com.mesac.service;

import com.mesac.dto.Requests.ReportRequest;
import com.mesac.model.*;
import com.mesac.repository.MatchReportRepository;
import com.mesac.repository.MatchRepository;
import com.mesac.repository.PlayerRepository;
import com.mesac.web.Actor;
import com.mesac.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Match reports: the MVP and a short recap, written by a coach after the final whistle. */
@Service
@Transactional
public class ReportService {

    private static final int MAX_SUMMARY = 2000;

    private final MatchReportRepository reports;
    private final MatchRepository matches;
    private final PlayerRepository players;
    private final LiveHub live;
    private final ActivityService activity;

    public ReportService(MatchReportRepository reports, MatchRepository matches, PlayerRepository players,
                         LiveHub live, ActivityService activity) {
        this.reports = reports;
        this.matches = matches;
        this.players = players;
        this.live = live;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<MatchReport> all() {
        return reports.findAllByOrderByUpdatedAtDesc();
    }

    public MatchReport save(Long matchId, ReportRequest req) {
        Match match = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("Match " + matchId));
        if (match.getStatus() != MatchStatus.DONE) throw ApiException.conflict("Write the report after the match has ended.");

        String summary = req.summary() == null ? "" : req.summary().trim();
        if (summary.length() > MAX_SUMMARY) throw ApiException.badRequest("Keep the recap under " + MAX_SUMMARY + " characters.");

        Long mvpId = null;
        String mvpName = req.mvpName() == null || req.mvpName().isBlank() ? null : req.mvpName().trim();
        if (req.mvpPlayerId() != null) {
            Player p = players.findById(req.mvpPlayerId()).orElseThrow(() -> ApiException.notFound("Player"));
            boolean onTeam = p.getTeamId().equals(match.getHomeTeam().getId()) || p.getTeamId().equals(match.getAwayTeam().getId());
            if (!onTeam || !p.getSportId().equals(match.getSport().getId()) || p.getDivision() != match.getDivision()) {
                throw ApiException.badRequest(p.getName() + " didn't play in this match.");
            }
            mvpId = p.getId();
            mvpName = p.getName();
        }
        if (mvpName != null && mvpName.length() > 60) throw ApiException.badRequest("MVP name can be at most 60 characters.");
        if (mvpName == null && summary.isEmpty()) throw ApiException.badRequest("Pick an MVP or write a recap.");

        MatchReport report = reports.findByMatchId(matchId).orElse(null);
        boolean isNew = report == null;
        if (isNew) report = new MatchReport(matchId);
        report.setMvpPlayerId(mvpId);
        report.setMvpName(mvpName);
        report.setSummary(summary.isEmpty() ? null : summary);
        report.setAuthor(Actor.name());
        report.setAuthorId(Actor.id());
        report.setUpdatedAt(LocalDateTime.now());
        reports.save(report);
        activity.record((isNew ? "Wrote the match report" : "Edited the match report") + ", " + MatchService.describe(match), matchId);
        live.publish("report", report);
        return report;
    }

    public void delete(Long matchId) {
        Match match = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("Match " + matchId));
        MatchReport report = reports.findByMatchId(matchId).orElseThrow(() -> ApiException.notFound("Report"));
        reports.delete(report);
        activity.record("Deleted the match report, " + MatchService.describe(match), matchId);
        live.publish("report-deleted", Map.of("matchId", matchId));
    }
}
