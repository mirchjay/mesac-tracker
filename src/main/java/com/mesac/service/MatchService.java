package com.mesac.service;

import com.mesac.model.Match;
import com.mesac.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

    public Optional<Match> getMatchById(String matchId) {
        return matchRepository.findById(matchId);
    }

    public Match saveMatch(Match match) {
        return matchRepository.save(match);
    }

    public Match updateScore(String matchId, Integer homeScore, Integer awayScore, String status) {
        Optional<Match> optionalMatch = matchRepository.findById(matchId);
        if (optionalMatch.isPresent()) {
            Match match = optionalMatch.get();
            match.setHomeScore(homeScore);
            match.setAwayScore(awayScore);
            match.setStatus(status);
            return matchRepository.save(match);
        }
        throw new IllegalArgumentException("Match not found with ID: " + matchId);
    }

    public List<Match> getMatchesByStatus(String status) {
        return matchRepository.findByStatus(status);
    }

    public List<Match> getMatchesBySportName(String sportName) {
        return matchRepository.findBySportName(sportName);
    }
}