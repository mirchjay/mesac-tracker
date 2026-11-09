package com.mesac.controller;

import com.mesac.dto.Requests.RecordRequest;
import com.mesac.dto.Requests.SportRequest;
import com.mesac.dto.Requests.TeamRequest;
import com.mesac.dto.Views.Option;
import com.mesac.dto.Views.StandingRow;
import com.mesac.dto.Views.Summary;
import com.mesac.model.*;
import com.mesac.repository.MatchRepository;
import com.mesac.service.CatalogService;
import com.mesac.service.MatchService;
import com.mesac.service.PlayerService;
import com.mesac.service.ReportService;
import com.mesac.service.StandingsService;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything that isn't a match: bootstrap data, teams, sports, standings, records. */
@RestController
@RequestMapping("/api")
public class CatalogApiController {

    private final CatalogService catalog;
    private final MatchService matchService;
    private final StandingsService standings;
    private final MatchRepository matches;
    private final PlayerService players;
    private final ReportService reports;

    public CatalogApiController(CatalogService catalog, MatchService matchService, StandingsService standings,
                                MatchRepository matches, PlayerService players, ReportService reports) {
        this.players = players;
        this.reports = reports;
        this.catalog = catalog;
        this.matchService = matchService;
        this.standings = standings;
        this.matches = matches;
    }

    /** One call that gives the admin page everything it needs to draw the first screen. */
    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sports", catalog.sports());
        data.put("teams", catalog.teams());
        data.put("matches", matchService.all());
        data.put("events", matchService.recentEvents());
        data.put("records", catalog.records());
        data.put("players", players.all());
        data.put("reports", reports.all());
        data.put("divisions", Arrays.stream(Division.values()).map(d -> new Option(d.name(), d.getLabel())).toList());
        return data;
    }

    @GetMapping("/summary")
    public Summary summary() {
        long live = matches.countByStatus(MatchStatus.LIVE);
        long upcoming = matches.countByStatus(MatchStatus.UPCOMING);
        long done = matches.countByStatus(MatchStatus.DONE);
        return new Summary(live, upcoming, done, live + upcoming + done);
    }

    @GetMapping("/events/recent")
    public List<MatchEvent> recentEvents() {
        return matchService.recentEvents();
    }

    @GetMapping("/standings")
    public List<StandingRow> standings(@RequestParam("sportId") Long sportId, @RequestParam("division") Division division) {
        return standings.table(sportId, division);
    }

    // ---- teams ----

    @GetMapping("/teams")
    public List<Team> teams() {
        return catalog.teams();
    }

    @PostMapping("/teams")
    public Team createTeam(@RequestBody TeamRequest req) {
        return catalog.saveTeam(null, req);
    }

    @PutMapping("/teams/{id}")
    public Team updateTeam(@PathVariable("id") Long id, @RequestBody TeamRequest req) {
        return catalog.saveTeam(id, req);
    }

    @DeleteMapping("/teams/{id}")
    public Map<String, Object> deleteTeam(@PathVariable("id") Long id) {
        catalog.deleteTeam(id);
        return Map.of("deleted", id);
    }

    // ---- sports ----

    @GetMapping("/sports")
    public List<Sport> sports() {
        return catalog.sports();
    }

    @PostMapping("/sports")
    public Sport createSport(@RequestBody SportRequest req) {
        return catalog.saveSport(null, req);
    }

    @PutMapping("/sports/{id}")
    public Sport updateSport(@PathVariable("id") Long id, @RequestBody SportRequest req) {
        return catalog.saveSport(id, req);
    }

    @DeleteMapping("/sports/{id}")
    public Map<String, Object> deleteSport(@PathVariable("id") Long id) {
        catalog.deleteSport(id);
        return Map.of("deleted", id);
    }

    // ---- conference records ----

    @GetMapping("/records")
    public List<ConferenceRecord> records() {
        return catalog.records();
    }

    @PostMapping("/records")
    public ConferenceRecord createRecord(@RequestBody RecordRequest req) {
        return catalog.saveRecord(null, req);
    }

    @PutMapping("/records/{id}")
    public ConferenceRecord updateRecord(@PathVariable("id") Long id, @RequestBody RecordRequest req) {
        return catalog.saveRecord(id, req);
    }

    @DeleteMapping("/records/{id}")
    public Map<String, Object> deleteRecord(@PathVariable("id") Long id) {
        catalog.deleteRecord(id);
        return Map.of("deleted", id);
    }

    private static String cap(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
