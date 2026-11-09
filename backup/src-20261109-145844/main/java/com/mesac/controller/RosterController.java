package com.mesac.controller;

import com.mesac.dto.Requests.PlayerRequest;
import com.mesac.dto.Requests.ReportRequest;
import com.mesac.dto.Requests.ScorerRequest;
import com.mesac.dto.Views.PredictionData;
import com.mesac.dto.Views.ScorerView;
import com.mesac.model.Division;
import com.mesac.model.MatchEvent;
import com.mesac.model.MatchReport;
import com.mesac.model.Player;
import com.mesac.service.MatchService;
import com.mesac.service.PlayerService;
import com.mesac.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Rosters, scorers, match reports and prediction stats.
 * Reading is open to everyone; changes need a coach or admin (checked by AuthInterceptor).
 */
@RestController
@RequestMapping("/api")
public class RosterController {

    private final PlayerService players;
    private final ReportService reports;
    private final MatchService matches;

    public RosterController(PlayerService players, ReportService reports, MatchService matches) {
        this.players = players;
        this.reports = reports;
        this.matches = matches;
    }

    // ---- players / rosters ----

    @GetMapping("/players")
    public List<Player> players() {
        return players.all();
    }

    @PostMapping("/players")
    public Player createPlayer(@RequestBody PlayerRequest req) {
        return players.save(null, req);
    }

    @PutMapping("/players/{id}")
    public Player updatePlayer(@PathVariable("id") Long id, @RequestBody PlayerRequest req) {
        return players.save(id, req);
    }

    @DeleteMapping("/players/{id}")
    public Map<String, Object> deletePlayer(@PathVariable("id") Long id) {
        players.delete(id);
        return Map.of("deleted", id);
    }

    /** Removes a whole roster, e.g. ?teamId=3&sportId=1&division=VARSITY_BOYS */
    @DeleteMapping("/players")
    public Map<String, Object> clearRoster(@RequestParam("teamId") Long teamId, @RequestParam("sportId") Long sportId,
                                           @RequestParam("division") Division division) {
        return Map.of("deleted", players.clearRoster(teamId, sportId, division));
    }

    // ---- who scored ----

    @PostMapping("/matches/{id}/events/{eventId}/scorer")
    public MatchEvent tagScorer(@PathVariable("id") Long id, @PathVariable("eventId") Long eventId,
                                @RequestBody ScorerRequest req) {
        return players.tagScorer(id, eventId, req.playerId());
    }

    @GetMapping("/scorers")
    public List<ScorerView> scorers(@RequestParam("sportId") Long sportId, @RequestParam("division") Division division) {
        return players.topScorers(sportId, division);
    }

    // ---- match reports ----

    @GetMapping("/reports")
    public List<MatchReport> reports() {
        return reports.all();
    }

    @PutMapping("/matches/{id}/report")
    public MatchReport saveReport(@PathVariable("id") Long id, @RequestBody ReportRequest req) {
        return reports.save(id, req);
    }

    @DeleteMapping("/matches/{id}/report")
    public Map<String, Object> deleteReport(@PathVariable("id") Long id) {
        reports.delete(id);
        return Map.of("deleted", id);
    }

    // ---- predictions ----

    @GetMapping("/predictions")
    public PredictionData predictions(@RequestParam(value = "voter", required = false) String voter) {
        return matches.predictions(voter);
    }
}
