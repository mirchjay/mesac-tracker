package com.mesac.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID userId;

    private String email;
    private String role;
    private Integer tokenBalance;

    // Default constructor (Required by JPA)
    public User() {}

    // Parameterized constructor
    public User(String email, String role, Integer tokenBalance) {
        this.email = email;
        this.role = role;
        this.tokenBalance = tokenBalance;
    }

    // Getters and Setters
    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getTokenBalance() {
        return tokenBalance;
    }

    public void setTokenBalance(Integer tokenBalance) {
        this.tokenBalance = tokenBalance;
    }
}