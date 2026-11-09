package com.mesac.service;

import com.mesac.dto.Requests.PlayerRequest;
import com.mesac.dto.Views.ScorerView;
import com.mesac.model.*;
import com.mesac.repository.*;
import com.mesac.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Rosters, tagging who scored, and the top scorers table. */
@Service
@Transactional
public class PlayerService {

    private final PlayerRepository players;
    private final TeamRepository teams;
    private final SportRepository sports;
    private final MatchEventRepository events;
    private final LiveHub live;
    private final ActivityService activity;

    public PlayerService(PlayerRepository players, TeamRepository teams, SportRepository sports,
                         MatchEventRepository events, LiveHub live, ActivityService activity) {
        this.players = players;
        this.teams = teams;
        this.sports = sports;
        this.events = events;
        this.live = live;
        this.activity = activity;
    }

    // ------------------------------------------------------------------ rosters

    @Transactional(readOnly = true)
    public List<Player> all() {
        return players.findAllByOrderByNameAsc();
    }

    public Player save(Long id, PlayerRequest req) {
        Player player = id == null ? new Player() : find(id);
        if (req.teamId() == null || !teams.existsById(req.teamId())) throw ApiException.badRequest("Pick a school.");
        if (req.sportId() == null || !sports.existsById(req.sportId())) throw ApiException.badRequest("Pick a sport.");
        if (req.division() == null) throw ApiException.badRequest("Pick a division.");
        String name = req.name() == null ? "" : req.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest("Enter the player's name.");
        if (name.length() > 60) throw ApiException.badRequest("Name can be at most 60 characters.");
        Integer number = req.number();
        if (number != null && (number < 0 || number > 999)) throw ApiException.badRequest("Jersey numbers go from 0 to 999.");
        boolean numberChanged = number != null && !number.equals(player.getNumber());
        boolean rosterChanged = !req.teamId().equals(player.getTeamId()) || !req.sportId().equals(player.getSportId())
                || req.division() != player.getDivision();
        if (number != null && (numberChanged || rosterChanged)
                && players.existsByTeamIdAndSportIdAndDivisionAndNumber(req.teamId(), req.sportId(), req.division(), number)) {
            throw ApiException.conflict("Someone on this roster already wears #" + number + ".");
        }
        String position = req.position() == null || req.position().isBlank() ? null : req.position().trim();
        if (position != null && position.length() > 30) position = position.substring(0, 30);

        boolean isNew = player.getId() == null;
        player.setTeamId(req.teamId());
        player.setSportId(req.sportId());
        player.setDivision(req.division());
        player.setName(name);
        player.setNumber(number);
        player.setPosition(position);
        players.save(player);
        activity.record((isNew ? "Added " : "Edited ") + "player " + label(player) + " (" + roster(player) + ")");
        publish();
        return player;
    }

    public void delete(Long id) {
        Player player = find(id);
        players.delete(player);
        activity.record("Removed player " + label(player) + " (" + roster(player) + ")");
        publish();
    }

    /** Removes every player on one roster. */
    public int clearRoster(Long teamId, Long sportId, Division division) {
        List<Player> list = players.findByTeamIdAndSportIdAndDivisionOrderByNumberAscNameAsc(teamId, sportId, division);
        if (list.isEmpty()) return 0;
        players.deleteAll(list);
        activity.record("Cleared the roster " + roster(list.get(0)) + " (" + list.size() + " players)");
        publish();
        return list.size();
    }

    // ------------------------------------------------------------------ scorers

    /** Says who scored one score change. playerId null clears it. */
    public MatchEvent tagScorer(Long matchId, Long eventId, Long playerId) {
        MatchEvent event = events.findById(eventId).orElseThrow(() -> ApiException.notFound("Timeline entry"));
        Match match = event.getMatch();
        if (match == null || !match.getId().equals(matchId)) throw ApiException.badRequest("That entry isn't from this match.");
        if (event.getType() != EventType.SCORE || event.getDelta() <= 0 || event.getSide() == null) {
            throw ApiException.badRequest("You can only name a scorer on points that were added.");
        }
        if (event.isUndone()) throw ApiException.conflict("Those points were undone.");

        if (playerId == null) {
            event.setPlayerId(null);
            event.setPlayerName(null);
            events.save(event);
            activity.record("Cleared the scorer on " + event.getMessage() + ", " + MatchService.describe(match), match.getId());
        } else {
            Player player = find(playerId);
            Team team = match.teamFor(event.getSide());
            if (!player.getTeamId().equals(team.getId()) || !player.getSportId().equals(match.getSport().getId())
                    || player.getDivision() != match.getDivision()) {
                throw ApiException.badRequest(player.getName() + " isn't on the " + team.getShortName() + " roster for this match.");
            }
            event.setPlayerId(player.getId());
            event.setPlayerName(player.getName());
            events.save(event);
            activity.record("Tagged " + label(player) + " for " + event.getMessage() + ", " + MatchService.describe(match), match.getId());
        }
        live.publish("event", event);
        return event;
    }

    /** Points scored by each tagged player in one sport and division, best first. */
    @Transactional(readOnly = true)
    public List<ScorerView> topScorers(Long sportId, Division division) {
        Map<Long, int[]> totals = new LinkedHashMap<>();          // playerId -> [points, scores]
        Map<Long, Set<Long>> matchesPlayed = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        Map<Long, Long> teamOf = new HashMap<>();
        for (MatchEvent e : events.findByTypeAndUndoneFalseAndPlayerIdIsNotNull(EventType.SCORE)) {
            Match m = e.getMatch();
            if (m == null || e.getDelta() <= 0 || !m.getSport().getId().equals(sportId) || m.getDivision() != division) continue;
            int[] t = totals.computeIfAbsent(e.getPlayerId(), k -> new int[2]);
            t[0] += e.getDelta();
            t[1]++;
            matchesPlayed.computeIfAbsent(e.getPlayerId(), k -> new HashSet<>()).add(m.getId());
            names.putIfAbsent(e.getPlayerId(), e.getPlayerName());
            teamOf.putIfAbsent(e.getPlayerId(), m.teamFor(e.getSide()).getId());
        }
        List<ScorerView> out = new ArrayList<>();
        totals.forEach((pid, t) -> {
            Player p = players.findById(pid).orElse(null);
            out.add(new ScorerView(pid, p != null ? p.getName() : names.get(pid), p != null ? p.getNumber() : null,
                    p != null ? p.getTeamId() : teamOf.get(pid), t[0], t[1], matchesPlayed.get(pid).size()));
        });
        out.sort(Comparator.comparingInt(ScorerView::points).reversed()
                .thenComparing(Comparator.comparingInt(ScorerView::scores).reversed())
                .thenComparing(ScorerView::name, Comparator.nullsLast(String::compareToIgnoreCase)));
        return out.size() > 25 ? out.subList(0, 25) : out;
    }

    // ------------------------------------------------------------------ helpers

    private Player find(Long id) {
        return players.findById(id).orElseThrow(() -> ApiException.notFound("Player"));
    }

    private void publish() {
        live.publish("players", all());
    }

    private static String label(Player p) {
        return p.getNumber() == null ? p.getName() : "#" + p.getNumber() + " " + p.getName();
    }

    private String roster(Player p) {
        String team = teams.findById(p.getTeamId()).map(Team::getShortName).orElse("?");
        String sport = sports.findById(p.getSportId()).map(Sport::getName).orElse("?");
        return team + " " + sport + ", " + p.getDivision().getLabel();
    }
}
