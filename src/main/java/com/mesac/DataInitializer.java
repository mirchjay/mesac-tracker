package com.mesac;

import com.mesac.model.*;
import com.mesac.repository.*;
import com.mesac.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
    private final PlayerRepository players;
    private final MatchReportRepository reports;
    private final StaffRepository staff;

    /** Set mesac.demo-data=false in application.properties before the real tournament. */
    @Value("${mesac.demo-data:true}")
    private boolean demoData;

    public DataInitializer(SportRepository sports, TeamRepository teams, MatchRepository matches,
                           MatchEventRepository events, PollVoteRepository votes,
                           ConferenceRecordRepository records, AuthService auth, PlayerRepository players,
                           MatchReportRepository reports, StaffRepository staff) {
        this.players = players;
        this.reports = reports;
        this.staff = staff;
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

        if (sports.count() == 0) loadSampleData();
        syncMemberSchools();
        if (demoData) {
            seedRosters();
            seedDemoPicks();
        }
    }

    private void loadSampleData() {

        // ---- Sports: name, icon, colour, score buttons, period name, periods, draws allowed
        Sport basketball = sports.save(new Sport("Basketball", "🏀", "#F08A24", "1,2,3", "Quarter", 4, false, 70));
        Sport soccer = sports.save(new Sport("Soccer", "⚽", "#2FBF71", "1", "Half", 2, true, 80));
        Sport volleyball = sports.save(new Sport("Volleyball", "🏐", "#F5C542", "1", "Set", 5, false, 60));
        Sport handball = sports.save(new Sport("Handball", "🤾", "#E5566F", "1", "Half", 2, true, 60));

        // ---- MESAC schools
        Team asd = teams.save(new Team("American School of Dubai", "ASD", "Dubai", "#C8102E"));
        Team daa = teams.save(new Team("Dubai American Academy", "DAA", "Dubai", "#1F5FBF"));
        Team acs = teams.save(new Team("American Community School", "ACS", "Abu Dhabi", "#6C3FC5"));
        Team doha = teams.save(new Team("American School of Doha", "DOHA", "Doha", "#0F8A6B"));
        Team aes = teams.save(new Team("American Embassy School", "AES", "New Delhi", "#E07A12"));
        Team aba = teams.save(new Team("ABA Oman International School", "ABA", "Muscat", "#0E7FA8"));

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // ---- Finished matches (these build the standings)
        done(basketball, Division.VARSITY_BOYS, asd, daa, 58, 52, "Main Gym", yesterday.atTime(9, 0));
        done(basketball, Division.VARSITY_BOYS, acs, doha, 61, 55, "Aux Gym", yesterday.atTime(10, 30));
        done(basketball, Division.VARSITY_BOYS, aes, aba, 47, 50, "Main Gym", yesterday.atTime(12, 0));
        done(basketball, Division.VARSITY_BOYS, asd, acs, 49, 54, "Main Gym", yesterday.atTime(15, 0));
        done(basketball, Division.VARSITY_BOYS, daa, doha, 63, 60, "Aux Gym", yesterday.atTime(16, 30));
        done(basketball, Division.VARSITY_BOYS, aba, asd, 44, 52, "Main Gym", today.atTime(8, 0));

        done(soccer, Division.VARSITY_GIRLS, acs, asd, 2, 1, "Field A", yesterday.atTime(9, 0));
        done(soccer, Division.VARSITY_GIRLS, doha, aes, 0, 0, "Field B", yesterday.atTime(9, 0));
        done(soccer, Division.VARSITY_GIRLS, daa, aba, 3, 2, "Field A", yesterday.atTime(14, 0));
        done(soccer, Division.VARSITY_GIRLS, asd, doha, 1, 1, "Field B", yesterday.atTime(14, 0));
        done(soccer, Division.VARSITY_GIRLS, acs, daa, 1, 0, "Field A", today.atTime(8, 30));

        done(volleyball, Division.JV_GIRLS, acs, daa, 25, 21, "Aux Gym", yesterday.atTime(11, 0));
        done(volleyball, Division.JV_GIRLS, asd, aes, 25, 18, "Aux Gym", yesterday.atTime(13, 0));
        done(volleyball, Division.JV_GIRLS, doha, aba, 23, 25, "Aux Gym", yesterday.atTime(17, 0));

        // ---- Live right now, with a short scoring trail each
        Match liveBball = live(basketball, Division.VARSITY_BOYS, acs, aba, 41, 38, 3, "Main Gym", now.minusMinutes(38));
        trail(liveBball, new Side[]{Side.HOME, Side.AWAY, Side.HOME, Side.AWAY, Side.HOME}, new int[]{3, 2, 2, 3, 2});
        fans(liveBball, 14, 9);

        Match liveSoccer = live(soccer, Division.VARSITY_GIRLS, daa, aes, 1, 0, 2, "Field A", now.minusMinutes(52));
        trail(liveSoccer, new Side[]{Side.HOME}, new int[]{1});
        fans(liveSoccer, 11, 6);

        Match liveVolley = live(volleyball, Division.JV_GIRLS, doha, asd, 18, 22, 2, "Aux Gym", now.minusMinutes(24));
        trail(liveVolley, new Side[]{Side.AWAY, Side.HOME, Side.AWAY, Side.AWAY}, new int[]{1, 1, 1, 1});
        fans(liveVolley, 5, 12);

        // ---- Coming up
        upcoming(basketball, Division.VARSITY_GIRLS, daa, asd, "Aux Gym", roundUp(now.plusMinutes(40)));
        upcoming(soccer, Division.VARSITY_BOYS, doha, acs, "Field B", roundUp(now.plusMinutes(70)));
        upcoming(handball, Division.JV_BOYS, aba, aes, "Main Gym", roundUp(now.plusMinutes(100)));
        upcoming(volleyball, Division.VARSITY_GIRLS, acs, aes, "Aux Gym", roundUp(now.plusMinutes(150)));
        upcoming(basketball, Division.VARSITY_BOYS, doha, aba, "Main Gym", tomorrow.atTime(9, 0));
        upcoming(basketball, Division.VARSITY_BOYS, daa, acs, "Main Gym", tomorrow.atTime(10, 30));
        upcoming(soccer, Division.VARSITY_GIRLS, aes, aba, "Field A", tomorrow.atTime(9, 30));
        upcoming(handball, Division.JV_BOYS, asd, doha, "Aux Gym", tomorrow.atTime(13, 0));

        // ---- Conference records (sample data - replace with real ones)
        records.save(new ConferenceRecord(basketball, Division.VARSITY_BOYS, "Most points in a single game", "Sam Smith", "DAA", "45 pts", 2023));
        records.save(new ConferenceRecord(basketball, null, "Highest team score", "ASD Varsity Boys", "ASD", "98 pts", 2022));
        records.save(new ConferenceRecord(soccer, Division.VARSITY_GIRLS, "Most goals in one tournament", "Leila Haddad", "ACS", "9 goals", 2025));
        records.save(new ConferenceRecord(volleyball, Division.VARSITY_GIRLS, "Most aces in a match", "Priya Nair", "DOHA", "14 aces", 2024));
        records.save(new ConferenceRecord(soccer, Division.VARSITY_BOYS, "Fastest goal from kick-off", "Omar Khalil", "ABA", "11 seconds", 2021));

        System.out.println(">>> MESAC sample data loaded: " + matches.count() + " matches, " + teams.count() + " teams.");
    }

    // ------------------------------------------------------------------ member schools

    /** The six MESAC member schools, as listed on mesacleague.com. Logos live in static/img/logos. */
    private static final String[][] MEMBERS = {
            // full name, code, city, colour, logo
            {"ABA Oman International School", "ABA", "Muscat", "#0E7FA8", "/img/logos/aba.png"},
            {"Dubai American Academy", "DAA", "Dubai", "#1F5FBF", "/img/logos/daa.png"},
            {"American School of Dubai", "ASD", "Dubai", "#C8102E", "/img/logos/asd.png"},
            {"American Community School", "ACS", "Abu Dhabi", "#6C3FC5", "/img/logos/acs.png"},
            {"American Embassy School", "AES", "New Delhi", "#E07A12", "/img/logos/aes.png"},
            {"American School of Doha", "DOHA", "Doha", "#0F8A6B", "/img/logos/doha.png"},
    };

    /** Schools that used to be in the sample data but aren't MESAC members. */
    private static final String[] NOT_MEMBERS = {"TAISM"};

    /**
     * Brings the teams in line with the MESAC member list: adds missing member schools, sets their logos,
     * and removes non-member schools with their matches. Runs once: only while no team has a logo yet,
     * so teams you add or change later are left alone.
     */
    private void syncMemberSchools() {
        List<Team> all = teams.findAll();
        if (all.stream().anyMatch(t -> t.getLogoUrl() != null)) return;

        for (String[] m : MEMBERS) {
            Team team = all.stream()
                    .filter(t -> t.getName().equalsIgnoreCase(m[0]) || t.getShortName().equalsIgnoreCase(m[1]))
                    .findFirst().orElse(null);
            if (team == null) {
                team = new Team(m[0], m[1], m[2], m[3]);
                System.out.println(">>> Added member school " + m[0] + " (" + m[1] + ")");
            }
            team.setLogoUrl(m[4]);
            teams.save(team);
        }

        for (String code : NOT_MEMBERS) {
            teams.findAll().stream().filter(t -> t.getShortName().equalsIgnoreCase(code)).findFirst().ifPresent(t -> {
                List<Match> theirs = matches.findByHomeTeamIdOrAwayTeamId(t.getId(), t.getId());
                for (Match match : theirs) {
                    events.deleteByMatch_Id(match.getId());
                    votes.deleteByMatch_Id(match.getId());
                    reports.deleteByMatchId(match.getId());
                    matches.delete(match);
                }
                players.deleteByTeamId(t.getId());
                for (Staff s : staff.findByTeamId(t.getId())) {
                    s.setTeamId(null);
                    staff.save(s);
                }
                for (ConferenceRecord r : records.findAll()) {
                    if (code.equalsIgnoreCase(r.getSchool())) records.delete(r);
                }
                teams.delete(t);
                System.out.println(">>> Removed " + t.getName() + " (not a MESAC member) and its " + theirs.size() + " matches");
            });
        }
    }

    // ------------------------------------------------------------------ demo rosters and picks

    private static final String[] BOYS = {"Omar", "Arjun", "Yusuf", "Daniel", "Kenji", "Ethan", "Zaid", "Lucas", "Rayan", "Mateo", "Hamza", "Leo", "Karim", "Noah", "Aditya", "Sami"};
    private static final String[] GIRLS = {"Lena", "Maya", "Sara", "Aisha", "Noor", "Priya", "Hana", "Mariam", "Zoe", "Layla", "Ananya", "Emma", "Yara", "Sofia", "Dana", "Ines"};
    private static final String[] LAST = {"Haddad", "Nair", "Khalil", "Smith", "Rahman", "Tanaka", "Malik", "Fernandes", "Kim", "Saleh", "Patel", "Okafor", "Costa", "Hughes", "Aziz", "Rossi", "Chen", "Farah"};

    /**
     * Sample rosters (8 players) for every school, sport and division that has a match,
     * and sample scorers on the live matches. Runs once: only while there are no players.
     */
    private void seedRosters() {
        if (players.count() > 0) return;
        Random rnd = new Random(7);
        Set<String> done = new HashSet<>();
        Map<String, List<Player>> rosters = new HashMap<>();
        for (Match m : matches.findAll()) {
            for (Team t : List.of(m.getHomeTeam(), m.getAwayTeam())) {
                String key = t.getId() + "-" + m.getSport().getId() + "-" + m.getDivision();
                if (!done.add(key)) continue;
                boolean girls = m.getDivision().name().endsWith("GIRLS");
                List<Player> list = new ArrayList<>();
                Set<Integer> numbers = new HashSet<>();
                for (int i = 0; i < 8; i++) {
                    String first = (girls ? GIRLS : BOYS)[rnd.nextInt(16)];
                    String last = LAST[rnd.nextInt(LAST.length)];
                    int number;
                    do { number = 1 + rnd.nextInt(30); } while (!numbers.add(number));
                    list.add(players.save(new Player(t.getId(), m.getSport().getId(), m.getDivision(), first + " " + last, number, null)));
                }
                rosters.put(key, list);
            }
        }
        // Tag scorers on points already on live matches, so Top scorers has something to show.
        for (MatchEvent e : events.findAll()) {
            Match m = e.getMatch();
            if (e.getType() != EventType.SCORE || e.getDelta() <= 0 || e.getSide() == null || e.isUndone() || e.getPlayerId() != null) continue;
            List<Player> list = rosters.get(m.teamFor(e.getSide()).getId() + "-" + m.getSport().getId() + "-" + m.getDivision());
            if (list == null) continue;
            Player p = list.get(rnd.nextInt(3)); // a few players score most of the points
            e.setPlayerId(p.getId());
            e.setPlayerName(p.getName());
            events.save(e);
        }
        System.out.println(">>> Sample rosters added: " + players.count() + " players. Remove them on the Teams page, or set mesac.demo-data=false.");
    }

    /**
     * Sample fan picks on finished and upcoming matches, so the Predictions page has data.
     * Runs once: only if no demo picks exist yet. Fans favour the eventual winner about 2 times in 3.
     */
    private void seedDemoPicks() {
        if (votes.existsByVoterStartingWith("demo-")) return;
        Random rnd = new Random(11);
        LocalDateTime now = LocalDateTime.now();
        int added = 0;
        for (Match m : matches.findAll()) {
            if (m.getStatus() == MatchStatus.LIVE || !votes.findByMatch_Id(m.getId()).isEmpty()) continue;
            int fansHere = 8 + rnd.nextInt(22);
            double homeShare = 0.5;
            if (m.getStatus() == MatchStatus.DONE && m.getHomeScore() != m.getAwayScore()) {
                boolean homeWon = m.getHomeScore() > m.getAwayScore();
                double favour = rnd.nextDouble() < 0.7 ? 0.55 + rnd.nextDouble() * 0.3 : 0.2 + rnd.nextDouble() * 0.25;
                homeShare = homeWon ? favour : 1 - favour;
            } else {
                homeShare = 0.3 + rnd.nextDouble() * 0.4;
            }
            LocalDateTime closes = m.getScheduledAt() == null || m.getScheduledAt().isAfter(now) ? now : m.getScheduledAt();
            List<Integer> pool = new ArrayList<>();
            for (int i = 0; i < 45; i++) pool.add(i);
            Collections.shuffle(pool, rnd);
            for (int i = 0; i < fansHere; i++) {
                PollVote v = new PollVote(m, rnd.nextDouble() < homeShare ? Side.HOME : Side.AWAY, 1, "demo-fan-" + pool.get(i));
                v.setCreatedAt(closes.minusMinutes(10 + rnd.nextInt(60 * 48)));
                votes.save(v);
                added++;
            }
        }
        System.out.println(">>> Sample fan picks added: " + added + ". Set mesac.demo-data=false to stop adding sample data.");
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
