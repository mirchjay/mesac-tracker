package com.mesac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One line in a match timeline ("ACS +2", "Q3 started", "Final").
 * Stores the score before and after so any score change can be undone.
 */
@Entity
@Table(name = "match_events")
public class MatchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Enumerated(EnumType.STRING)
    private Side side;

    private int delta;

    @Column(name = "home_before")
    private int homeBefore;
    @Column(name = "away_before")
    private int awayBefore;
    @Column(name = "home_after")
    private int homeAfter;
    @Column(name = "away_after")
    private int awayAfter;

    @Column(name = "current_period")
    private int currentPeriod;

    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** "Admin" or "Coach": who made this change (empty for sample data). */
    private String actor;

    /** True once this score change has been reversed with Undo. */
    private boolean undone = false;

    public MatchEvent() {}

    public MatchEvent(Match match, EventType type, String message) {
        this.match = match;
        this.type = type;
        this.message = message;
        this.homeBefore = match.getHomeScore();
        this.awayBefore = match.getAwayScore();
        this.homeAfter = match.getHomeScore();
        this.awayAfter = match.getAwayScore();
        this.currentPeriod = match.getCurrentPeriod();
        this.createdAt = LocalDateTime.now();
    }

    // ---- Extra read-only fields for the activity feed ----

    public Long getMatchId() {
        return match == null ? null : match.getId();
    }

    public String getMatchCode() {
        return match == null ? null : match.getCode();
    }

    public String getMatchLabel() {
        if (match == null) return null;
        return match.getHomeTeam().getShortName() + " vs " + match.getAwayTeam().getShortName();
    }

    public String getSportIcon() {
        return match == null ? null : match.getSport().getIcon();
    }

    public String getSportColor() {
        return match == null ? null : match.getSport().getColor();
    }

    public String getTeamColor() {
        if (match == null || side == null) return null;
        return match.teamFor(side).getColor();
    }

    // ---- Getters / setters ----

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Match getMatch() { return match; }
    public void setMatch(Match match) { this.match = match; }

    public EventType getType() { return type; }
    public void setType(EventType type) { this.type = type; }

    public Side getSide() { return side; }
    public void setSide(Side side) { this.side = side; }

    public int getDelta() { return delta; }
    public void setDelta(int delta) { this.delta = delta; }

    public int getHomeBefore() { return homeBefore; }
    public void setHomeBefore(int homeBefore) { this.homeBefore = homeBefore; }

    public int getAwayBefore() { return awayBefore; }
    public void setAwayBefore(int awayBefore) { this.awayBefore = awayBefore; }

    public int getHomeAfter() { return homeAfter; }
    public void setHomeAfter(int homeAfter) { this.homeAfter = homeAfter; }

    public int getAwayAfter() { return awayAfter; }
    public void setAwayAfter(int awayAfter) { this.awayAfter = awayAfter; }

    public int getCurrentPeriod() { return currentPeriod; }
    public void setCurrentPeriod(int currentPeriod) { this.currentPeriod = currentPeriod; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public boolean isUndone() { return undone; }
    public void setUndone(boolean undone) { this.undone = undone; }
}