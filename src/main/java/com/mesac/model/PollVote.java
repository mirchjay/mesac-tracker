package com.mesac.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A fan's "who will win" pick, backed by Viper Tokens (no real money).
 * The student/fan view will cast these; the admin view shows the results.
 */
@Entity
@Table(name = "poll_votes")
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    @JoinColumn(name = "match_id")
    private Match match;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    private int tokens;

    private String voter;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public PollVote() {}

    public PollVote(Match match, Side side, int tokens, String voter) {
        this.match = match;
        this.side = side;
        this.tokens = tokens;
        this.voter = voter;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Match getMatch() { return match; }
    public void setMatch(Match match) { this.match = match; }

    public Side getSide() { return side; }
    public void setSide(Side side) { this.side = side; }

    public int getTokens() { return tokens; }
    public void setTokens(int tokens) { this.tokens = tokens; }

    public String getVoter() { return voter; }
    public void setVoter(String voter) { this.voter = voter; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
