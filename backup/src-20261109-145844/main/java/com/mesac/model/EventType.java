package com.mesac.model;

/** Kinds of entries in a match's timeline. */
public enum EventType {
    SCORE,      // +/- points for one side
    CORRECTION, // score typed in directly
    UNDO,       // a previous SCORE/CORRECTION was reversed
    STATUS,     // started, final, reopened
    PERIOD,     // next/previous quarter, half, set...
    NOTE        // free text from the admin
}
