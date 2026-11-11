package com.mesac;

import com.mesac.model.*;
import com.mesac.repository.*;
import com.mesac.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills an empty database with sample sports, MESAC schools, matches, records and passes
 * so the admin console has something to show. Runs only when there are no sports yet.
 *
 * To start fresh: stop the app, delete the ./data folder, start the app again.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final SportRepository sports;
    private final TeamRepository teams;
    private final MatchRepository matches;
    private final MatchEventRepository events;
    private final PollVoteRepository votes;
    private final ConferenceRecordRepository records;
    private final AuthService auth;

    public DataInitializer(SportRepository sports, TeamRepository teams, MatchRepository matches,
                           MatchEventRepository events, PollVoteRepository votes,
                           ConferenceRecordRepository records, AuthService auth) {
        this.sports = sports;
        this.teams = teams;
        this.matches = matches;
        this.events = events;
        this.votes = votes;
        this.records = records;
        this.auth = auth;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // First admin account, so someone can log in. Change this password after your first login!
        if (auth.createFirstAdmin("Tournament Admin", "admin", "mesac-admin-2026")) {
            System.out.println(">>> First admin login: username admin, password mesac-admin-2026 (change it after you sign in)");
        }

        if (sports.count() > 0) return;

        // ---- Sports: name, icon, colour, score buttons, period name, periods, draws allowed
        Sport basketball = sports.save(new Sport("Basketball", "🏀", "#F08A24", "1,2,3", "Quarter", 4, false, 70));
        Sport soccer = sports.save(new Sport("Soccer", "⚽", "#2FBF71", "1", "Half", 2, true, 80));
        Sport volleyball = sports.save(new Sport("Volleyball", "🏐", "#F5C542", "1", "Set", 5, false, 60));
        Sport handball = sports.save(new Sport("Handball", "🤾", "#E5566F", "1", "Half", 2, true, 60));

        // ---- MESAC schools
        Team asd = teams.save(new Team("American School of Dubai", "ASD", "Dubai", "#C8102E"));
        Team daa = teams.save(new Team("Dubai American Academy", "DAA", "Dubai", "#1F5FBF"));
        Team acs = teams.save(new Team("American Community School", "ACS", "Abu Dhabi", "#6C3FC5"));
        Team taism = teams.save(new Team("The American International School Muscat", "TAISM", "Muscat", "#0F8A6B"));
        Team aes = teams.save(new Team("American Embassy School", "AES", "New Delhi", "#E07A12"));
        Team aba = teams.save(new Team("ABA Oman International School", "ABA", "Muscat", "#0E7FA8"));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // ---- Finished matches (these build the standings)
        done(basketball, Division.VARSITY_BOYS, asd, daa, 58, 52, "Main Gym", yesterday.atTime(9, 0));
        done(basketball, Division.VARSITY_BOYS, acs, taism, 61, 55, "Aux Gym", yesterday.atTime(10, 30));
        done(basketball, Division.VARSITY_BOYS, aes, aba, 47, 50, "Main Gym", yesterday.atTime(12, 0));
        done(basketball, Division.VARSITY_BOYS, asd, acs, 49, 54, "Main Gym", yesterday.atTime(15, 0));
        done(basketball, Division.VARSITY_BOYS, daa, taism, 63, 60, "Aux Gym", yesterday.atTime(16, 30));
        done(basketball, Division.VARSITY_BOYS, aba, asd, 44, 52, "Main Gym", today.atTime(8, 0));

        done(soccer, Division.VARSITY_GIRLS, acs, asd, 2, 1, "Field A", yesterday.atTime(9, 0));
        done(soccer, Division.VARSITY_GIRLS, taism, aes, 0, 0, "Field B", yesterday.atTime(9, 0));
        done(soccer, Division.VARSITY_GIRLS, daa, aba, 3, 2, "Field A", yesterday.atTime(14, 0));
        done(soccer, Division.VARSITY_GIRLS, asd, taism, 1, 1, "Field B", yesterday.atTime(14, 0));
        done(soccer, Division.VARSITY_GIRLS, acs, daa, 1, 0, "Field A", today.atTime(8, 30));

        done(volleyball, Division.JV_GIRLS, acs, daa, 25, 21, "Aux Gym", yesterday.atTime(11, 0));
        done(volleyball, Division.JV_GIRLS, asd, aes, 25, 18, "Aux Gym", yesterday.atTime(13, 0));
        done(volleyball, Division.JV_GIRLS, taism, aba, 23, 25, "Aux Gym", yesterday.atTime(17, 0));

        // ---- Live right now, with a short scoring trail each
        Match liveBball = live(basketball, Division.VARSITY_BOYS, acs, aba, 41, 38, 3, "Main Gym", now.minusMinutes(38));
        trail(liveBball, new Side[]{Side.HOME, Side.AWAY, Side.HOME, Side.AWAY, Side.HOME}, new int[]{3, 2, 2, 3, 2});
        fans(liveBball, 14, 9);

        Match liveSoccer = live(soccer, Division.VARSITY_GIRLS, daa, aes, 1, 0, 2, "Field A", now.minusMinutes(52));
        trail(liveSoccer, new Side[]{Side.HOME}, new int[]{1});
        fans(liveSoccer, 11, 6);

        Match liveVolley = live(volleyball, Division.JV_GIRLS, taism, asd, 18, 22, 2, "Aux Gym", now.minusMinutes(24));
        trail(liveVolley, new Side[]{Side.AWAY, Side.HOME, Side.AWAY, Side.AWAY}, new int[]{1, 1, 1, 1});
        fans(liveVolley, 5, 12);

        // ---- Coming up
        upcoming(basketball, Division.VARSITY_GIRLS, daa, asd, "Aux Gym", roundUp(now.plusMinutes(40)));
        upcoming(soccer, Division.VARSITY_BOYS, taism, acs, "Field B", roundUp(now.plusMinutes(70)));
        upcoming(handball, Division.JV_BOYS, aba, aes, "Main Gym", roundUp(now.plusMinutes(100)));
        upcoming(volleyball, Division.VARSITY_GIRLS, acs, aes, "Aux Gym", roundUp(now.plusMinutes(150)));
        upcoming(basketball, Division.VARSITY_BOYS, taism, aba, "Main Gym", tomorrow.atTime(9, 0));
        upcoming(basketball, Division.VARSITY_BOYS, daa, acs, "Main Gym", tomorrow.atTime(10, 30));
        upcoming(soccer, Division.VARSITY_GIRLS, aes, aba, "Field A", tomorrow.atTime(9, 30));
        upcoming(handball, Division.JV_BOYS, asd, taism, "Aux Gym", tomorrow.atTime(13, 0));

        // ---- Conference records (sample data - replace with real ones)
        records.save(new ConferenceRecord(basketball, Division.VARSITY_BOYS, "Most points in a single game", "Sam Smith", "DAA", "45 pts", 2023));
        records.save(new ConferenceRecord(basketball, null, "Highest team score", "ASD Varsity Boys", "ASD", "98 pts", 2022));
        records.save(new ConferenceRecord(soccer, Division.VARSITY_GIRLS, "Most goals in one tournament", "Leila Haddad", "ACS", "9 goals", 2025));
        records.save(new ConferenceRecord(volleyball, Division.VARSITY_GIRLS, "Most aces in a match", "Priya Nair", "TAISM", "14 aces", 2024));
        records.save(new ConferenceRecord(soccer, Division.VARSITY_BOYS, "Fastest goal from kick-off", "Omar Khalil", "ABA", "11 seconds", 2021));

        System.out.println(">>> MESAC sample data loaded: " + matches.count() + " matches, " + teams.count() + " teams.");
    }

    // ------------------------------------------------------------------ helpers

    private Match base(Sport sport, Division division, Team home, Team away, String venue, LocalDateTime when) {
        Match m = new Match();
        m.setSport(sport);
        m.setDivision(division);
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setVenue(venue);
        m.setScheduledAt(when);
        return m;
    }

    private void done(Sport sport, Division division, Team home, Team away, int hs, int as, String venue, LocalDateTime when) {
        Match m = base(sport, division, home, away, venue, when);
        m.setHomeScore(hs);
        m.setAwayScore(as);
        m.setStatus(MatchStatus.DONE);
        m.setCurrentPeriod(sport.getPeriodCount());
        m.setStartedAt(when);
        m.setEndedAt(when.plusMinutes(70));
        matches.save(m);
        MatchEvent e = new MatchEvent(m, EventType.STATUS,
                "Final: " + home.getShortName() + " " + hs + "–" + as + " " + away.getShortName());
        e.setCreatedAt(m.getEndedAt());
        events.save(e);
    }

    private Match live(Sport sport, Division division, Team home, Team away, int hs, int as, int period,
                       String venue, LocalDateTime startedAt) {
        Match m = base(sport, division, home, away, venue, startedAt);
        m.setHomeScore(hs);
        m.setAwayScore(as);
        m.setStatus(MatchStatus.LIVE);
        m.setCurrentPeriod(period);
        m.setStartedAt(startedAt);
        matches.save(m);
        MatchEvent e = new MatchEvent(m, EventType.STATUS, "Match started");
        e.setHomeBefore(0); e.setAwayBefore(0); e.setHomeAfter(0); e.setAwayAfter(0); e.setCurrentPeriod(1);
        e.setCreatedAt(startedAt);
        events.save(e);
        return m;
    }

    private void upcoming(Sport sport, Division division, Team home, Team away, String venue, LocalDateTime when) {
        matches.save(base(sport, division, home, away, venue, when));
    }

    /** Creates score events that end exactly at the match's current score. */
    private void trail(Match m, Side[] sides, int[] deltas) {
        int home = m.getHomeScore(), away = m.getAwayScore();
        List<MatchEvent> list = new ArrayList<>();
        for (int i = sides.length - 1; i >= 0; i--) {
            MatchEvent e = new MatchEvent(m, EventType.SCORE, m.teamFor(sides[i]).getShortName() + " +" + deltas[i]);
            e.setSide(sides[i]);
            e.setDelta(deltas[i]);
            e.setHomeAfter(home);
            e.setAwayAfter(away);
            if (sides[i] == Side.HOME) home -= deltas[i]; else away -= deltas[i];
            e.setHomeBefore(home);
            e.setAwayBefore(away);
            list.add(0, e);
        }
        LocalDateTime t = LocalDateTime.now().minusMinutes(sides.length * 3L);
        for (MatchEvent e : list) {
            e.setCreatedAt(t);
            t = t.plusMinutes(3);
            events.save(e);
        }
    }

    private void fans(Match m, int homeVotes, int awayVotes) {
        for (int i = 0; i < homeVotes; i++) votes.save(new PollVote(m, Side.HOME, 1 + (i * 7) % 10, "fan-h" + i));
        for (int i = 0; i < awayVotes; i++) votes.save(new PollVote(m, Side.AWAY, 1 + (i * 5) % 10, "fan-a" + i));
    }

    private static LocalDateTime roundUp(LocalDateTime t) {
        int add = (15 - t.getMinute() % 15) % 15;
        return t.plusMinutes(add).withSecond(0).withNano(0);
    }
}
