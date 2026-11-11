package com.mesac.model;

import jakarta.persistence.*;

/** A school team. The same school plays in every sport and division. */
@Entity
@Table(name = "teams")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Full name, e.g. "American Community School". */
    @Column(nullable = false)
    private String name;

    /** Short code shown on scoreboards, e.g. "ACS". */
    @Column(name = "short_name", nullable = false)
    private String shortName;

    private String city;

    /** Jersey colour, e.g. "#C8102E". */
    private String color;

    public Team() {}

    public Team(String name, String shortName, String city, String color) {
        this.name = name;
        this.shortName = shortName;
        this.city = city;
        this.color = color;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getShortName() { return shortName; }
    public void setShortName(String shortName) { this.shortName = shortName; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}