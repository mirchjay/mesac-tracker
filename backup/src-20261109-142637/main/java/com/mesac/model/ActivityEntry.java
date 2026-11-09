package com.mesac.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One line in the activity log: who did what, and when. */
@Entity
@Table(name = "activity_log")
public class ActivityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id")
    private Long staffId;

    /** The person's name at the time, so the log still reads right if they are renamed. */
    @Column(name = "staff_name")
    private String staffName;

    @Column(name = "action_text", nullable = false, length = 400)
    private String action;

    /** Set when the action was about a match, so the log can link to it. */
    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ActivityEntry() {}

    public ActivityEntry(Long staffId, String staffName, String action, Long matchId) {
        this.staffId = staffId;
        this.staffName = staffName;
        this.action = action;
        this.matchId = matchId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStaffId() { return staffId; }
    public void setStaffId(Long staffId) { this.staffId = staffId; }

    public String getStaffName() { return staffName; }
    public void setStaffName(String staffName) { this.staffName = staffName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
