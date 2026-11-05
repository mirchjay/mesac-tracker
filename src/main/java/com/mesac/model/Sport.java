package com.mesac.model;

import jakarta.persistence.*;

@Entity
@Table(name = "sports")
public class Sport {

    @Id
    @Column(name = "sport_id")
    private String sportId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    // Default Constructor
    public Sport() {}

    // Parameterized Constructor
    public Sport(String sportId, String name, String category) {
        this.sportId = sportId;
        this.name = name;
        this.category = category;
    }

    // Getters and Setters
    public String getSportId() {
        return sportId;
    }

    public void setSportId(String sportId) {
        this.sportId = sportId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}