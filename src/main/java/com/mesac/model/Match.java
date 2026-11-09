package com.mesac.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One fixture between two teams in a sport and division. */
@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sport_id")
    private Sport sport;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Division division;

    @ManyToOne(optional = false)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    @Column(name = "home_score")
    private int homeScore = 0;

    @Column(name = "away_score")
    private int awayScore = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status = MatchStatus.UPCOMING;

    /** 0 before start, then 1, 2, 3... */
    @Column(name = "current_period")
    private int currentPeriod = 0;

    private String venue;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    public Match() {}

    /** Friendly ID shown in the UI, e.g. "M-007". */
    public String getCode() {
        return String.format("M-%03d", id == null ? 0 : id);
    }

    public String getDivisionLabel() {
        return division == null ? null : division.getLabel();
    }

    public int scoreFor(Side side) {
        return side == Side.HOME ? homeScore : awayScore;
    }

    public Team teamFor(Side side) {
        return side == Side.HOME ? homeTeam : awayTeam;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Sport getSport() { return sport; }
    public void setSport(Sport sport) { this.sport = sport; }

    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }

    public Team getHomeTeam() { return homeTeam; }
    public void setHomeTeam(Team homeTeam) { this.homeTeam = homeTeam; }

    public Team getAwayTeam() { return awayTeam; }
    public void setAwayTeam(Team awayTeam) { this.awayTeam = awayTeam; }

    public int getHomeScore() { return homeScore; }
    public void setHomeScore(int homeScore) { this.homeScore = homeScore; }

    public int getAwayScore() { return awayScore; }
    public void setAwayScore(int awayScore) { this.awayScore = awayScore; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public int getCurrentPeriod() { return currentPeriod; }
    public void setCurrentPeriod(int currentPeriod) { this.currentPeriod = currentPeriod; }

    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}