package com.mesac.dto;

import com.mesac.model.Division;
import com.mesac.model.MatchStatus;
import com.mesac.model.Side;
import com.mesac.model.StaffRole;

import java.time.LocalDateTime;

/** JSON bodies the admin page sends to the API. */
public final class Requests {

    private Requests() {}

    public record MatchRequest(Long sportId, Division division, Long homeTeamId, Long awayTeamId,
                               String venue, LocalDateTime scheduledAt, MatchStatus status) {}

    /** delta can be negative to take points away. */
    public record ScoreRequest(Side side, Integer delta) {}

    public record SetScoreRequest(Integer home, Integer away) {}

    /** +1 = next period, -1 = previous period. */
    public record PeriodRequest(Integer direction) {}

    public record NoteRequest(String message) {}

    public record VoteRequest(Side side, Integer tokens, String voter) {}

    public record TeamRequest(String name, String shortName, String city, String color) {}

    public record SportRequest(String name, String icon, String color, String scoreIncrements,
                               String periodName, Integer periodCount, Boolean allowsDraw, Integer matchMinutes) {}

    public record RecordRequest(Long sportId, Division division, String title, String holder,
                                String school, String recordValue, Integer yearSet) {}

    public record LoginRequest(String username, String password) {}

    public record PasswordChangeRequest(String current, String next) {}

    public record PasswordResetRequest(String password) {}

    public record StaffRequest(String name, String username, String password, StaffRole role, Boolean active) {}
}
