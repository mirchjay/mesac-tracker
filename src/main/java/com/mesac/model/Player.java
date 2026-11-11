package com.mesac.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One athlete on a roster. A roster is a school + sport + division,
 * e.g. ACS Basketball Varsity Boys.
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "sport_id", nullable = false)
    private Long sportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Division division;

    @Column(nullable = false, length = 60)
    private String name;

    /** Jersey number (optional). */
    @Column(name = "jersey_number")
    private Integer number;

    /** Optional, e.g. "Guard" or "Goalkeeper". */
    @Column(length = 30)
    private String position;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Player() {}

    public Player(Long teamId, Long sportId, Division division, String name, Integer number, String position) {
        this.teamId = teamId;
        this.sportId = sportId;
        this.division = division;
        this.name = name;
        this.number = number;
        this.position = position;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTeamId() { return teamId; }
    public void setTeamId(Long teamId) { this.teamId = teamId; }

    public Long getSportId() { return sportId; }
    public void setSportId(Long sportId) { this.sportId = sportId; }

    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getNumber() { return number; }
    public void setNumber(Integer number) { this.number = number; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
