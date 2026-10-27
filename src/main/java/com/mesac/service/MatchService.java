package main.java.com.mesac.service;

import com.mesac.model.Match;
import com.mesac.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRepository matchRepository;

    @Autowired
    public MatchService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public List<Match> getAllMatches() {
        return matchRepository.findAll();
    }

    public List<Match> getLiveMatches() {
        return matchRepository.findByStatus("LIVE");
    }

    public Optional<Match> getMatchById(UUID matchId) {
        return matchRepository.findById(matchId);
    }

    public Match saveMatch(Match match) {
        return matchRepository.save(match);
    }

    public Match updateScore(UUID matchId, Integer homeScore, Integer awayScore) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Match not found with id: " + matchId));
        
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return matchRepository.save(match);
    }
}