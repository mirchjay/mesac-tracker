package com.mesac.model;

import jakarta.persistence.*;

/**
 * A conference record ("Most points in a single game - 45 pts").
 * Named ConferenceRecord because "Record" clashes with Java's built-in java.lang.Record.
 */
@Entity
@Table(name = "conference_records")
public class ConferenceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sport_id")
    private Sport sport;

    @Enumerated(EnumType.STRING)
    private Division division;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String holder;

    private String school;

    @Column(name = "record_value")
    private String recordValue;

    @Column(name = "year_set")
    private int yearSet;

    public ConferenceRecord() {}

    public ConferenceRecord(Sport sport, Division division, String title, String holder,
                            String school, String recordValue, int yearSet) {
        this.sport = sport;
        this.division = division;
        this.title = title;
        this.holder = holder;
        this.school = school;
        this.recordValue = recordValue;
        this.yearSet = yearSet;
    }

    public String getDivisionLabel() {
        return division == null ? null : division.getLabel();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Sport getSport() { return sport; }
    public void setSport(Sport sport) { this.sport = sport; }

    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getHolder() { return holder; }
    public void setHolder(String holder) { this.holder = holder; }

    public String getSchool() { return school; }
    public void setSchool(String school) { this.school = school; }

    public String getRecordValue() { return recordValue; }
    public void setRecordValue(String recordValue) { this.recordValue = recordValue; }

    public int getYearSet() { return yearSet; }
    public void setYearSet(int yearSet) { this.yearSet = yearSet; }
}
