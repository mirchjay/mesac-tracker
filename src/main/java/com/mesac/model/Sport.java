package main.java.com.mesac.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "sports")
public class Sport {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID sportId;

    private String name;
    private String category;

    // Default constructor (Required by JPA)
    public Sport() {}

    // Parameterized constructor
    public Sport(String name, String category) {
        this.name = name;
        this.category = category;
    }

    // Getters and Setters
    public UUID getSportId() {
        return sportId;
    }

    public void setSportId(UUID sportId) {
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