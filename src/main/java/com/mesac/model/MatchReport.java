package com.mesac.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A coach's write-up of a finished match: MVP and a short recap. One per match. */
@Entity
@Table(name = "match_reports")
public class MatchReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id", nullable = false, unique = true)
    private Long matchId;

    /** The MVP from the roster (optional). */
    @Column(name = "mvp_player_id")
    private Long mvpPlayerId;

    /** MVP name, kept even if the player is later removed from the roster. */
    @Column(name = "mvp_name", length = 60)
    private String mvpName;

    @Column(length = 2000)
    private String summary;

    /** Who wrote it last. */
    private String author;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MatchReport() {}

    public MatchReport(Long matchId) {
        this.matchId = matchId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public Long getMvpPlayerId() { return mvpPlayerId; }
    public void setMvpPlayerId(Long mvpPlayerId) { this.mvpPlayerId = mvpPlayerId; }

    public String getMvpName() { return mvpName; }
    public void setMvpName(String mvpName) { this.mvpName = mvpName; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
