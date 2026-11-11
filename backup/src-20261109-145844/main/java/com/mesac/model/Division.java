package com.mesac.model;

/** The four MESAC competition divisions. */
public enum Division {
    VARSITY_BOYS("Varsity Boys"),
    VARSITY_GIRLS("Varsity Girls"),
    JV_BOYS("JV Boys"),
    JV_GIRLS("JV Girls");

    private final String label;

    Division(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
