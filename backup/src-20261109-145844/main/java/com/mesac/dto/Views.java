package com.mesac.dto;

import com.mesac.model.Match;
import com.mesac.model.MatchEvent;
import com.mesac.model.Team;

import java.util.ArrayList;
import java.util.List;

/** JSON shapes the API sends back that aren't plain database rows. */
public final class Views {

    private Views() {}

    public record Option(String value, String label) {}

    /** The signed-in account (null means User view). */
    public record Me(Long id, String name, String username, String role, Long teamId) {}

    /** An account on the Staff page, with how much they've done. */
    public record StaffView(Long id, String name, String username, String role, boolean active,
                            java.time.LocalDateTime createdAt, java.time.LocalDateTime lastLoginAt,
                            long actions, java.time.LocalDateTime lastActionAt, Long teamId) {}

    /** One line of the top scorers table. */
    public record ScorerView(Long playerId, String name, Integer number, Long teamId, int points, int scores, int matches) {}

    /** How many picks were made on one day. */
    public record DayCount(java.time.LocalDate day, int picks) {}

    /** Raw numbers for the Predictions page (the page works out the statistics). */
    public record PredictionData(List<PickView> picks, List<DayCount> daily, int fans, int totalPicks) {}

    public record PollSummary(long homeTokens, long awayTokens, int homeVotes, int awayVotes) {}

    /** Fan picks for one match. mine is "HOME", "AWAY" or null (this fan hasn't picked). */
    public record PickView(Long matchId, int homeVotes, int awayVotes, String mine) {}

    public record MatchDetail(Match match, List<MatchEvent> events, PollSummary poll) {}

    public record Summary(long live, long upcoming, long done, long total) {}

    /** One row of a standings table. Mutable so the service can add up results. */
    public static class StandingRow {
        private final Team team;
        private int played, wins, draws, losses, pointsFor, pointsAgainst;
        private final List<String> form = new ArrayList<>(); // "W", "D", "L", oldest first

        public StandingRow(Team team) {
            this.team = team;
        }

        public void addResult(int scored, int conceded) {
            played++;
            pointsFor += scored;
            pointsAgainst += conceded;
            if (scored > conceded) { wins++; form.add("W"); }
            else if (scored < conceded) { losses++; form.add("L"); }
            else { draws++; form.add("D"); }
        }

        public Team getTeam() { return team; }
        public int getPlayed() { return played; }
        public int getWins() { return wins; }
        public int getDraws() { return draws; }
        public int getLosses() { return losses; }
        public int getPointsFor() { return pointsFor; }
        public int getPointsAgainst() { return pointsAgainst; }
        public int getDifference() { return pointsFor - pointsAgainst; }

        /** League points: 3 for a win, 1 for a draw. */
        public int getStandingPoints() { return wins * 3 + draws; }

        /** Last five results, most recent last. */
        public List<String> getForm() {
            int from = Math.max(0, form.size() - 5);
            return form.subList(from, form.size());
        }
    }
}
