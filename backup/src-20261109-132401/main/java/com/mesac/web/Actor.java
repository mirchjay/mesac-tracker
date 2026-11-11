package com.mesac.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is using the console right now: "Admin" or "Coach".
 * The page sends it in the X-Mesac-Role header (from the switch at the top of the screen).
 * There is no sign-in yet, so this is a label for the timeline, not security.
 */
public final class Actor {

    public static final String HEADER = "X-Mesac-Role";

    private Actor() {}

    public static String current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest request = attrs.getRequest();
        String role = request.getHeader(HEADER);
        if (role == null || role.isBlank()) return null;
        return role.trim().equalsIgnoreCase("COACH") ? "Coach" : "Admin";
    }
}
