package main.java.com.mesac.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "votes")
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID voteId;

    private UUID userId;
    private UUID matchId;
    private String selection;

    // Default constructor (Required by JPA)
    public Vote() {}

    // Parameterized constructor
    public Vote(UUID userId, UUID matchId, String selection) {
        this.userId = userId;
        this.matchId = matchId;
        this.selection = selection;
    }

    // Getters and Setters
    public UUID getVoteId() {
        return voteId;
    }

    public void setVoteId(UUID voteId) {
        this.voteId = voteId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }
}