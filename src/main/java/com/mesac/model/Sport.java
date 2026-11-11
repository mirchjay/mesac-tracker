package com.mesac.model;

import jakarta.persistence.*;

/**
 * A sport and the rules the scoring console needs:
 * which score buttons to show (+1, +2, +3), what a period is called, and how many there are.
 */
@Entity
@Table(name = "sports")
public class Sport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    /** Emoji shown next to the name, e.g. "🏀". */
    private String icon;

    /** Accent colour used across the UI, e.g. "#F08A24". */
    private String color;

    /** Comma-separated score buttons, e.g. "1,2,3" for basketball, "1" for soccer. */
    @Column(name = "score_increments")
    private String scoreIncrements = "1";

    /** "Quarter", "Half", "Set"... */
    @Column(name = "period_name")
    private String periodName = "Period";

    /** Regular number of periods (4 quarters, 2 halves...). Extra periods show as overtime. */
    @Column(name = "period_count")
    private int periodCount = 2;

    /** Typical match length in minutes, used to draw matches on the Day line. */
    @Column(name = "match_minutes")
    private Integer matchMinutes = 60;

    /** Can a match end level? (soccer yes, basketball no) */
    @Column(name = "allows_draw")
    private boolean allowsDraw = false;

    public Sport() {}

    public Sport(String name, String icon, String color, String scoreIncrements,
                 String periodName, int periodCount, boolean allowsDraw, int matchMinutes) {
        this.name = name;
        this.icon = icon;
        this.color = color;
        this.scoreIncrements = scoreIncrements;
        this.periodName = periodName;
        this.periodCount = periodCount;
        this.allowsDraw = allowsDraw;
        this.matchMinutes = matchMinutes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getScoreIncrements() { return scoreIncrements; }
    public void setScoreIncrements(String scoreIncrements) { this.scoreIncrements = scoreIncrements; }

    public String getPeriodName() { return periodName; }
    public void setPeriodName(String periodName) { this.periodName = periodName; }

    public int getPeriodCount() { return periodCount; }
    public void setPeriodCount(int periodCount) { this.periodCount = periodCount; }

    /** Older databases may not have a value yet, so fall back to 60. */
    public int getMatchMinutes() { return matchMinutes == null ? 60 : matchMinutes; }
    public void setMatchMinutes(Integer matchMinutes) { this.matchMinutes = matchMinutes; }

    public boolean isAllowsDraw() { return allowsDraw; }
    public void setAllowsDraw(boolean allowsDraw) { this.allowsDraw = allowsDraw; }
}
