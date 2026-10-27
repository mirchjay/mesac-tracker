package main.java.com.mesac.controller;

import com.mesac.model.Match;
import com.mesac.service.MatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class MatchController {

    private final MatchService matchService;

    @Autowired
    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/")
    public String homePage() {
        return "index";
    }

    @GetMapping("/matches")
    public String listMatches(Model model) {
        model.addAttribute("matches", matchService.getAllMatches());
        model.addAttribute("newMatch", new Match());
        return "matches";
    }

    @PostMapping("/matches/create")
    public String createMatch(@ModelAttribute Match newMatch) {
        if (newMatch.getStatus() == null || newMatch.getStatus().isEmpty()) {
            newMatch.setStatus("LIVE");
        }
        matchService.saveMatch(newMatch);
        return "redirect:/matches";
    }
}