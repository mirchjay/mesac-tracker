package com.mesac.controller;

import com.mesac.model.Match;
import com.mesac.repository.MatchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Controller
public class MatchController {

    @Autowired
    private MatchRepository matchRepository;

    @GetMapping("/")
    public String index(Model model) {
        List<Match> matches = matchRepository.findAll();
        model.addAttribute("matches", matches);
        model.addAttribute("totalMatches", matches.size());
        model.addAttribute("liveCount", matches.stream().filter(m -> "LIVE".equalsIgnoreCase(m.getStatus())).count());
        model.addAttribute("upcomingCount", matches.stream().filter(m -> "UPCOMING".equalsIgnoreCase(m.getStatus())).count());
        model.addAttribute("completedCount", matches.stream().filter(m -> "DONE".equalsIgnoreCase(m.getStatus())).count());
        return "index";
    }

    @PostMapping("/api/matches/add")
    public String createMatch(@RequestParam String sportName,
                              @RequestParam String homeTeam, 
                              @RequestParam String awayTeam, 
                              @RequestParam(defaultValue = "UPCOMING") String status) {
        String customId = "m-" + UUID.randomUUID().toString().substring(0, 5);
        Match newMatch = new Match(customId, sportName, homeTeam, awayTeam, 0, 0, status);
        matchRepository.save(newMatch);
        return "redirect:/";
    }

    @PostMapping("/api/matches/score")
    public String updateScore(@RequestParam String matchId, 
                              @RequestParam Integer homeScore, 
                              @RequestParam Integer awayScore,
                              @RequestParam String status) {
        matchRepository.findById(matchId).ifPresent(match -> {
            match.setHomeScore(homeScore);
            match.setAwayScore(awayScore);
            match.setStatus(status);
            matchRepository.save(match);
        });
        return "redirect:/";
    }
}