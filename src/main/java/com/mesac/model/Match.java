package com.mesac.model;

import jakarta.persistence.*;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @Column(name = "match_id")
    private String matchId;

    @Column(name = "sport_name", nullable = false)
    private String sportName;

    @Column(name = "home_team", nullable = false)
    private String homeTeam;

    @Column(name = "away_team", nullable = false)
    private String awayTeam;

    @Column(name = "home_score")
    private Integer homeScore = 0;

    @Column(name = "away_score")
    private Integer awayScore = 0;

    @Column(nullable = false)
    private String status = "UPCOMING";

    // Default Constructor
    public Match() {}

    // Updated Constructor including sportName
    public Match(String matchId, String sportName, String homeTeam, String awayTeam, Integer homeScore, Integer awayScore, String status) {
        this.matchId = matchId;
        this.sportName = sportName;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
        this.status = status;
    }

    // Getters and Setters
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getSportName() { return sportName; }
    public void setSportName(String sportName) { this.sportName = sportName; }

    public String getHomeTeam() { return homeTeam; }
    public void setHomeTeam(String homeTeam) { this.homeTeam = homeTeam; }

    public String getAwayTeam() { return awayTeam; }
    public void setAwayTeam(String awayTeam) { this.awayTeam = awayTeam; }

    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }

    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}