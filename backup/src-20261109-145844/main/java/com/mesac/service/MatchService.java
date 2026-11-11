package com.mesac.service;

import com.mesac.dto.Requests.MatchRequest;
import com.mesac.dto.Views.MatchDetail;
import com.mesac.dto.Views.DayCount;
import com.mesac.dto.Views.PickView;
import com.mesac.dto.Views.PredictionData;
import com.mesac.dto.Views.PollSummary;
import com.mesac.model.*;
import com.mesac.repository.*;
import com.mesac.web.Actor;
import com.mesac.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** All the rules for creating, scoring and finishing matches. */
@Service
@Transactional
public class MatchService {

    private static final List<EventType> UNDOABLE = List.of(EventType.SCORE, EventType.CORRECTION);

    private final MatchRepository matches;
    private final MatchEventRepository events;
    private final PollVoteRepository votes;
    private final SportRepository sports;
    private final TeamRepository teams;
    private final LiveHub live;
    private final ActivityService activity;
    private final MatchReportRepository reports;

    public MatchService(MatchRepository matches, MatchEventRepository events, PollVoteRepository votes,
                        SportRepository sports, TeamRepository teams, LiveHub live, ActivityService activity,
                        MatchReportRepository reports) {
        this.reports = reports;
        this.matches = matches;
        this.events = events;
        this.votes = votes;
        this.sports = sports;
        this.teams = teams;
        this.live = live;
        this.activity = activity;
    }

    // ------------------------------------------------------------------ reading

    @Transactional(readOnly = true)
    public List<Match> all() {
        return matches.findAllByOrderByScheduledAtAsc();
    }

    @Transactional(readOnly = true)
    public Match get(Long id) {
        return matches.findById(id).orElseThrow(() -> ApiException.notFound("Match " + id));
    }

    @Transactional(readOnly = true)
    public MatchDetail detail(Long id) {
        Match match = get(id);
        return new MatchDetail(match, events.findByMatch_IdOrderByIdDesc(id), poll(id));
    }

    @Transactional(readOnly = true)
    public List<MatchEvent> recentEvents() {
        return events.findTop40ByOrderByIdDesc();
    }

    @Transactional(readOnly = true)
    public PollSummary poll(Long matchId) {
        long homeTokens = 0, awayTokens = 0;
        int homeVotes = 0, awayVotes = 0;
        for (PollVote v : votes.findByMatch_Id(matchId)) {
            if (v.getSide() == Side.HOME) { homeTokens += v.getTokens(); homeVotes++; }
            else { awayTokens += v.getTokens(); awayVotes++; }
        }
        return new PollSummary(homeTokens, awayTokens, homeVotes, awayVotes);
    }

    // ------------------------------------------------------------------ fixtures

    public Match create(MatchRequest req) {
        Match match = new Match();
        applyDetails(match, req);
        MatchStatus status = req.status() == null ? MatchStatus.UPCOMING : req.status();
        match.setStatus(status);
        if (status == MatchStatus.LIVE) {
            match.setCurrentPeriod(1);
            match.setStartedAt(LocalDateTime.now());
        }
        if (status == MatchStatus.DONE) {
            match.setEndedAt(LocalDateTime.now());
        }
        matches.save(match);
        log(match, EventType.STATUS, "Fixture created");
        live.publish("match", match);
        return match;
    }

    public Match update(Long id, MatchRequest req) {
        Match match = get(id);
        applyDetails(match, req);
        matches.save(match);
        activity.record("Edited details, " + describe(match), match.getId());
        live.publish("match", match);
        return match;
    }

    public void delete(Long id) {
        Match match = get(id);
        activity.record("Deleted match, " + describe(match), match.getId());
        events.deleteByMatch_Id(id);
        votes.deleteByMatch_Id(id);
        reports.deleteByMatchId(id);
        matches.delete(match);
        live.publish("match-deleted", Map.of("id", id));
    }

    private void applyDetails(Match match, MatchRequest req) {
        if (req.sportId() == null) throw ApiException.badRequest("Pick a sport.");
        if (req.division() == null) throw ApiException.badRequest("Pick a division.");
        if (req.homeTeamId() == null || req.awayTeamId() == null) throw ApiException.badRequest("Pick both teams.");
        if (req.homeTeamId().equals(req.awayTeamId())) throw ApiException.badRequest("A team can't play itself. Pick two different teams.");

        match.setSport(sports.findById(req.sportId()).orElseThrow(() -> ApiException.notFound("Sport")));
        match.setHomeTeam(teams.findById(req.homeTeamId()).orElseThrow(() -> ApiException.notFound("Home team")));
        match.setAwayTeam(teams.findById(req.awayTeamId()).orElseThrow(() -> ApiException.notFound("Away team")));
        match.setDivision(req.division());
        match.setVenue(blankToNull(req.venue()));
        match.setScheduledAt(req.scheduledAt() == null ? LocalDateTime.now() : req.scheduledAt());
    }

    // ------------------------------------------------------------------ live scoring

    public Match score(Long id, Side side, int delta) {
        Match match = get(id);
        if (side == null) throw ApiException.badRequest("Say which team scored (HOME or AWAY).");
        if (delta == 0) throw ApiException.badRequest("Score change can't be zero.");
        requireLive(match, "Start the match before adding points.");

        int current = match.scoreFor(side);
        int next = Math.max(0, current + delta);
        if (next == current) throw ApiException.conflict("Score is already 0.");

        MatchEvent event = new MatchEvent(match, EventType.SCORE, null);
        event.setSide(side);
        event.setDelta(next - current);
        if (side == Side.HOME) match.setHomeScore(next); else match.setAwayScore(next);
        String sign = event.getDelta() > 0 ? "+" : "";
        event.setMessage(match.teamFor(side).getShortName() + " " + sign + event.getDelta());
        return saveWithEvent(match, event);
    }

    public Match setScore(Long id, int home, int away) {
        Match match = get(id);
        if (home < 0 || away < 0) throw ApiException.badRequest("Scores can't be negative.");
        if (match.getStatus() == MatchStatus.UPCOMING) throw ApiException.conflict("Start the match before setting a score.");
        MatchEvent event = new MatchEvent(match, EventType.CORRECTION, "Score corrected to " + home + "–" + away);
        match.setHomeScore(home);
        match.setAwayScore(away);
        return saveWithEvent(match, event);
    }

    public Match undo(Long id) {
        Match match = get(id);
        MatchEvent last = events.findFirstByMatch_IdAndUndoneFalseAndTypeInOrderByIdDesc(id, UNDOABLE)
                .orElseThrow(() -> ApiException.conflict("Nothing to undo yet."));
        last.setUndone(true);
        events.save(last);
        live.publish("event", last);

        MatchEvent event = new MatchEvent(match, EventType.UNDO, "Undid " + last.getMessage());
        match.setHomeScore(last.getHomeBefore());
        match.setAwayScore(last.getAwayBefore());
        return saveWithEvent(match, event);
    }

    public Match start(Long id) {
        Match match = get(id);
        if (match.getStatus() != MatchStatus.UPCOMING) throw ApiException.conflict("This match has already started.");
        match.setStatus(MatchStatus.LIVE);
        match.setCurrentPeriod(1);
        match.setStartedAt(LocalDateTime.now());
        return saveWithEvent(match, new MatchEvent(match, EventType.STATUS, "Match started"));
    }

    public Match changePeriod(Long id, int direction) {
        Match match = get(id);
        requireLive(match, "Periods can only change while the match is live.");
        int max = match.getSport().getPeriodCount() + 3; // room for overtime
        int next = match.getCurrentPeriod() + (direction >= 0 ? 1 : -1);
        if (next < 1) throw ApiException.conflict("Already in the first " + match.getSport().getPeriodName().toLowerCase() + ".");
        if (next > max) throw ApiException.conflict("That's the last overtime period this console allows.");
        match.setCurrentPeriod(next);
        return saveWithEvent(match, new MatchEvent(match, EventType.PERIOD, periodLabel(match) + " started"));
    }

    public Match finish(Long id) {
        Match match = get(id);
        requireLive(match, "Only a live match can be finished.");
        if (!match.getSport().isAllowsDraw() && match.getHomeScore() == match.getAwayScore()) {
            throw ApiException.conflict(match.getSport().getName() + " can't end in a tie. Play another period or correct the score.");
        }
        match.setStatus(MatchStatus.DONE);
        match.setEndedAt(LocalDateTime.now());
        String message = "Final: " + match.getHomeTeam().getShortName() + " " + match.getHomeScore()
                + "–" + match.getAwayScore() + " " + match.getAwayTeam().getShortName();
        return saveWithEvent(match, new MatchEvent(match, EventType.STATUS, message));
    }

    public Match reopen(Long id) {
        Match match = get(id);
        if (match.getStatus() != MatchStatus.DONE) throw ApiException.conflict("Only a finished match can be reopened.");
        match.setStatus(MatchStatus.LIVE);
        match.setEndedAt(null);
        return saveWithEvent(match, new MatchEvent(match, EventType.STATUS, "Match reopened"));
    }

    public MatchEvent note(Long id, String message) {
        Match match = get(id);
        if (message == null || message.isBlank()) throw ApiException.badRequest("Write a note first.");
        String text = message.trim();
        MatchEvent event = new MatchEvent(match, EventType.NOTE, text.length() > 240 ? text.substring(0, 240) : text);
        event.setActor(Actor.name());
        event.setActorId(Actor.id());
        events.save(event);
        activity.record("Note \"" + event.getMessage() + "\", " + describe(match), match.getId());
        live.publish("event", event);
        return event;
    }

    // ------------------------------------------------------------------ fan poll

    /**
     * A fan picks who they think will win. One pick per fan per match (the fan page sends a
     * random id saved in the browser). Picking again changes the pick. Picks close at the start.
     */
    public PollSummary vote(Long id, Side side, int tokens, String voter) {
        Match match = get(id);
        if (side == null) throw ApiException.badRequest("Pick a team.");
        String who = blankToNull(voter);
        if (who == null || who.length() > 64) throw ApiException.badRequest("Missing fan id. Reload the page and try again.");
        if (match.getStatus() != MatchStatus.UPCOMING) throw ApiException.conflict("Picks closed when the match started.");
        PollVote vote = votes.findByMatch_IdAndVoter(id, who).orElse(null);
        if (vote == null) {
            votes.save(new PollVote(match, side, Math.max(1, tokens), who));
        } else {
            vote.setSide(side);
            votes.save(vote);
        }
        return publishPoll(id);
    }

    /** Pick counts for every match, plus which side this fan picked. */
    @Transactional(readOnly = true)
    public List<PickView> picks(String voter) {
        Map<Long, int[]> counts = new HashMap<>();
        Map<Long, String> mine = new HashMap<>();
        for (PollVote v : votes.findAll()) {
            Long matchId = v.getMatch().getId();
            int[] c = counts.computeIfAbsent(matchId, k -> new int[2]);
            c[v.getSide() == Side.HOME ? 0 : 1]++;
            if (voter != null && voter.equals(v.getVoter())) mine.put(matchId, v.getSide().name());
        }
        List<PickView> out = new ArrayList<>();
        counts.forEach((matchId, c) -> out.add(new PickView(matchId, c[0], c[1], mine.get(matchId))));
        return out;
    }

    /** Everything the Predictions page needs: picks per match, picks per day, how many fans. */
    @Transactional(readOnly = true)
    public PredictionData predictions(String voter) {
        Map<LocalDate, Integer> perDay = new TreeMap<>();
        Set<String> fans = new HashSet<>();
        int total = 0;
        for (PollVote v : votes.findAll()) {
            total++;
            if (v.getVoter() != null) fans.add(v.getVoter());
            if (v.getCreatedAt() != null) perDay.merge(v.getCreatedAt().toLocalDate(), 1, Integer::sum);
        }
        List<DayCount> daily = new ArrayList<>();
        perDay.forEach((day, n) -> daily.add(new DayCount(day, n)));
        return new PredictionData(picks(voter), daily, fans.size(), total);
    }

    public PollSummary resetPoll(Long id) {
        activity.record("Cleared fan picks, " + describe(get(id)), id);
        votes.deleteByMatch_Id(id);
        return publishPoll(id);
    }

    private PollSummary publishPoll(Long id) {
        PollSummary summary = poll(id);
        live.publish("poll", Map.of("matchId", id, "poll", summary));
        return summary;
    }

    // ------------------------------------------------------------------ helpers

    private Match saveWithEvent(Match match, MatchEvent event) {
        event.setHomeAfter(match.getHomeScore());
        event.setAwayAfter(match.getAwayScore());
        event.setCurrentPeriod(match.getCurrentPeriod());
        event.setActor(Actor.name());
        event.setActorId(Actor.id());
        matches.save(match);
        events.save(event);
        activity.record(event.getMessage() + ", " + describe(match), match.getId());
        live.publish("match", match);
        live.publish("event", event);
        return match;
    }

    private void log(Match match, EventType type, String message) {
        MatchEvent event = new MatchEvent(match, type, message);
        event.setActor(Actor.name());
        event.setActorId(Actor.id());
        events.save(event);
        activity.record(message + ", " + describe(match), match.getId());
        live.publish("event", event);
    }

    private void requireLive(Match match, String message) {
        if (match.getStatus() != MatchStatus.LIVE) throw ApiException.conflict(message);
    }

    /** "M-012 ACS v ABA (Basketball, Varsity Boys)" for the activity log. */
    public static String describe(Match m) {
        return m.getCode() + " " + m.getHomeTeam().getShortName() + " v " + m.getAwayTeam().getShortName()
                + " (" + m.getSport().getName() + ", " + m.getDivisionLabel() + ")";
    }

    /** "Quarter 3", or "OT 1" once regular periods are used up. */
    public static String periodLabel(Match match) {
        Sport sport = match.getSport();
        int p = match.getCurrentPeriod();
        if (p > sport.getPeriodCount()) return "OT " + (p - sport.getPeriodCount());
        return sport.getPeriodName() + " " + p;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
