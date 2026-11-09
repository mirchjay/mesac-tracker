package com.mesac.controller;

import com.mesac.dto.Requests.*;
import com.mesac.dto.Views.MatchDetail;
import com.mesac.dto.Views.PickView;
import com.mesac.dto.Views.PollSummary;
import com.mesac.model.Match;
import com.mesac.model.MatchEvent;
import com.mesac.service.MatchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Matches: fixtures, live scoring, timeline and fan poll. */
@RestController
@RequestMapping("/api/matches")
public class MatchApiController {

    private final MatchService service;

    public MatchApiController(MatchService service) {
        this.service = service;
    }

    @GetMapping
    public List<Match> list() {
        return service.all();
    }

    @GetMapping("/{id}")
    public MatchDetail detail(@PathVariable("id") Long id) {
        return service.detail(id);
    }

    @PostMapping
    public Match create(@RequestBody MatchRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Match update(@PathVariable("id") Long id, @RequestBody MatchRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return Map.of("deleted", id);
    }

    // ---- live scoring ----

    @PostMapping("/{id}/score")
    public Match score(@PathVariable("id") Long id, @RequestBody ScoreRequest req) {
        return service.score(id, req.side(), req.delta() == null ? 1 : req.delta());
    }

    @PostMapping("/{id}/set-score")
    public Match setScore(@PathVariable("id") Long id, @RequestBody SetScoreRequest req) {
        return service.setScore(id, req.home() == null ? 0 : req.home(), req.away() == null ? 0 : req.away());
    }

    @PostMapping("/{id}/undo")
    public Match undo(@PathVariable("id") Long id) {
        return service.undo(id);
    }

    @PostMapping("/{id}/start")
    public Match start(@PathVariable("id") Long id) {
        return service.start(id);
    }

    @PostMapping("/{id}/period")
    public Match period(@PathVariable("id") Long id, @RequestBody PeriodRequest req) {
        return service.changePeriod(id, req.direction() == null ? 1 : req.direction());
    }

    @PostMapping("/{id}/finish")
    public Match finish(@PathVariable("id") Long id) {
        return service.finish(id);
    }

    @PostMapping("/{id}/reopen")
    public Match reopen(@PathVariable("id") Long id) {
        return service.reopen(id);
    }

    @PostMapping("/{id}/note")
    public MatchEvent note(@PathVariable("id") Long id, @RequestBody NoteRequest req) {
        return service.note(id, req.message());
    }

    // ---- fan poll (votes will come from the student/fan view later) ----

    @GetMapping("/{id}/poll")
    public PollSummary poll(@PathVariable("id") Long id) {
        return service.poll(id);
    }

    @PostMapping("/{id}/poll/vote")
    public PollSummary vote(@PathVariable("id") Long id, @RequestBody VoteRequest req) {
        return service.vote(id, req.side(), req.tokens() == null ? 1 : req.tokens(), req.voter());
    }

    /** Fan page: pick counts for every match, and this fan's own picks. */
    @GetMapping("/picks")
    public List<PickView> picks(@RequestParam(value = "voter", required = false) String voter) {
        return service.picks(voter);
    }

    @DeleteMapping("/{id}/poll")
    public PollSummary resetPoll(@PathVariable("id") Long id) {
        return service.resetPoll(id);
    }
}
