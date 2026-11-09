package com.mesac.service;

import com.mesac.dto.Requests.RecordRequest;
import com.mesac.dto.Requests.SportRequest;
import com.mesac.dto.Requests.TeamRequest;
import com.mesac.model.ConferenceRecord;
import com.mesac.model.Sport;
import com.mesac.model.Team;
import com.mesac.repository.ConferenceRecordRepository;
import com.mesac.repository.MatchRepository;
import com.mesac.repository.SportRepository;
import com.mesac.repository.TeamRepository;
import com.mesac.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Teams, sports and conference records. */
@Service
@Transactional
public class CatalogService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private static final Pattern INCREMENTS = Pattern.compile("^\\d{1,2}(,\\d{1,2}){0,3}$");

    private final TeamRepository teams;
    private final SportRepository sports;
    private final ConferenceRecordRepository records;
    private final MatchRepository matches;
    private final LiveHub live;

    public CatalogService(TeamRepository teams, SportRepository sports, ConferenceRecordRepository records,
                          MatchRepository matches, LiveHub live) {
        this.teams = teams;
        this.sports = sports;
        this.records = records;
        this.matches = matches;
        this.live = live;
    }

    // ------------------------------------------------------------------ teams

    @Transactional(readOnly = true)
    public List<Team> teams() {
        return teams.findAllByOrderByNameAsc();
    }

    public Team saveTeam(Long id, TeamRequest req) {
        Team team = id == null ? new Team() : teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
        String name = required(req.name(), "Give the team a name.");
        String code = required(req.shortName(), "Give the team a short code, like ACS.").toUpperCase();
        if (code.length() > 6) throw ApiException.badRequest("Short code can be at most 6 letters.");
        boolean codeChanged = team.getShortName() == null || !team.getShortName().equalsIgnoreCase(code);
        if (codeChanged && teams.existsByShortNameIgnoreCase(code)) {
            throw ApiException.conflict("Another team already uses the code " + code + ".");
        }
        team.setName(name);
        team.setShortName(code);
        team.setCity(trimToNull(req.city()));
        team.setColor(color(req.color(), "#5B8DEF"));
        teams.save(team);
        live.publish("teams", teams());
        return team;
    }

    public void deleteTeam(Long id) {
        Team team = teams.findById(id).orElseThrow(() -> ApiException.notFound("Team"));
        if (matches.existsByHomeTeamIdOrAwayTeamId(id, id)) {
            throw ApiException.conflict(team.getShortName() + " still has matches. Delete those matches first.");
        }
        teams.delete(team);
        live.publish("teams", teams());
    }

    // ------------------------------------------------------------------ sports

    @Transactional(readOnly = true)
    public List<Sport> sports() {
        return sports.findAll();
    }

    public Sport saveSport(Long id, SportRequest req) {
        Sport sport = id == null ? new Sport() : sports.findById(id).orElseThrow(() -> ApiException.notFound("Sport"));
        String name = required(req.name(), "Give the sport a name.");
        sports.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (!existing.getId().equals(sport.getId())) throw ApiException.conflict(name + " already exists.");
        });
        String increments = req.scoreIncrements() == null || req.scoreIncrements().isBlank()
                ? "1" : req.scoreIncrements().replace(" ", "");
        if (!INCREMENTS.matcher(increments).matches()) {
            throw ApiException.badRequest("Score buttons should look like 1 or 1,2,3.");
        }
        int periods = req.periodCount() == null ? 2 : req.periodCount();
        if (periods < 1 || periods > 9) throw ApiException.badRequest("Number of periods must be between 1 and 9.");
        int minutes = req.matchMinutes() == null ? 60 : req.matchMinutes();
        if (minutes < 10 || minutes > 300) throw ApiException.badRequest("Match length must be between 10 and 300 minutes.");

        sport.setName(name);
        sport.setIcon(req.icon() == null || req.icon().isBlank() ? "🏅" : req.icon().trim());
        sport.setColor(color(req.color(), "#5B8DEF"));
        sport.setScoreIncrements(increments);
        sport.setPeriodName(req.periodName() == null || req.periodName().isBlank() ? "Period" : req.periodName().trim());
        sport.setPeriodCount(periods);
        sport.setAllowsDraw(Boolean.TRUE.equals(req.allowsDraw()));
        sport.setMatchMinutes(minutes);
        sports.save(sport);
        live.publish("sports", sports());
        return sport;
    }

    public void deleteSport(Long id) {
        Sport sport = sports.findById(id).orElseThrow(() -> ApiException.notFound("Sport"));
        if (matches.existsBySportId(id) || records.existsBySportId(id)) {
            throw ApiException.conflict(sport.getName() + " still has matches or records. Remove those first.");
        }
        sports.delete(sport);
        live.publish("sports", sports());
    }

    // ------------------------------------------------------------------ records

    @Transactional(readOnly = true)
    public List<ConferenceRecord> records() {
        return records.findAllByOrderByYearSetDesc();
    }

    public ConferenceRecord saveRecord(Long id, RecordRequest req) {
        ConferenceRecord record = id == null ? new ConferenceRecord()
                : records.findById(id).orElseThrow(() -> ApiException.notFound("Record"));
        int year = req.yearSet() == null ? Year.now().getValue() : req.yearSet();
        if (year < 1950 || year > Year.now().getValue()) throw ApiException.badRequest("Year must be between 1950 and this year.");

        record.setSport(req.sportId() == null ? null
                : sports.findById(req.sportId()).orElseThrow(() -> ApiException.notFound("Sport")));
        record.setDivision(req.division());
        record.setTitle(required(req.title(), "Say what the record is for."));
        record.setHolder(required(req.holder(), "Say who holds the record."));
        record.setSchool(trimToNull(req.school()));
        record.setRecordValue(trimToNull(req.recordValue()));
        record.setYearSet(year);
        records.save(record);
        live.publish("records", Map.of("changed", true));
        return record;
    }

    public void deleteRecord(Long id) {
        records.delete(records.findById(id).orElseThrow(() -> ApiException.notFound("Record")));
        live.publish("records", Map.of("changed", true));
    }

    // ------------------------------------------------------------------ helpers

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw ApiException.badRequest(message);
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String color(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        if (!HEX_COLOR.matcher(value.trim()).matches()) throw ApiException.badRequest("Colour must look like #1A2B3C.");
        return value.trim().toUpperCase();
    }
}