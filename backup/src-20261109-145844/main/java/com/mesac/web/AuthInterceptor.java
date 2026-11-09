package com.mesac.web;

import com.mesac.model.Staff;
import com.mesac.model.StaffRole;
import com.mesac.repository.StaffRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * The server-side lock. Runs before every /api request:
 * - Anyone can READ (GET): that is the User view.
 * - Anyone can sign in, sign out, and make a "who will win" pick.
 * - Every other change needs a signed-in admin or coach.
 * - Account management (/api/staff, /api/activity) needs an admin.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final StaffRepository staff;

    public AuthInterceptor(StaffRepository staff) {
        this.staff = staff;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!path.startsWith("/api/")) return true;

        boolean adminOnly = path.startsWith("/api/staff") || path.startsWith("/api/activity");
        boolean write = !(method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS"));

        if (!adminOnly && !write) return true;
        if (path.startsWith("/api/auth/")) return true;
        if (method.equals("POST") && path.matches("^/api/matches/\\d+/poll/vote$")) return true;

        Actor.SignedIn who = Actor.from(request);
        if (who == null) return deny(response, 401, "Log in to do that.");

        Staff account = staff.findById(who.id()).orElse(null);
        if (account == null || !account.isActive()) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            return deny(response, 401, "This account has been turned off. Ask an admin.");
        }
        if (adminOnly && account.getRole() != StaffRole.ADMIN) {
            return deny(response, 403, "Only admins can manage accounts.");
        }
        return true;
    }

    private boolean deny(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
        return false;
    }
}
