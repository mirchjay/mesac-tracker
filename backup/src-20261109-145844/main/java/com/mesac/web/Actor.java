package com.mesac.web;

import com.mesac.model.StaffRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;

/**
 * Who is signed in for the current request (stored in the login session).
 * Used to stamp names on the match timeline and the activity log.
 */
public final class Actor {

    public static final String SESSION_KEY = "mesac.staff";

    /** What we keep in the session after a successful login. */
    public record SignedIn(Long id, String name, String username, StaffRole role) implements Serializable {}

    private Actor() {}

    public static SignedIn current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        return from(attrs.getRequest());
    }

    public static SignedIn from(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        return session.getAttribute(SESSION_KEY) instanceof SignedIn who ? who : null;
    }

    public static String name() {
        SignedIn who = current();
        return who == null ? null : who.name();
    }

    public static Long id() {
        SignedIn who = current();
        return who == null ? null : who.id();
    }
}
