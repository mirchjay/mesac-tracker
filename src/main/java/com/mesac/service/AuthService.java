package com.mesac.service;

import com.mesac.dto.Views.Me;
import com.mesac.dto.Views.StaffView;
import com.mesac.model.ActivityEntry;
import com.mesac.model.Staff;
import com.mesac.model.StaffRole;
import com.mesac.repository.ActivityRepository;
import com.mesac.repository.StaffRepository;
import com.mesac.repository.TeamRepository;
import com.mesac.web.Actor;
import com.mesac.web.ApiException;
import com.mesac.web.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/** Logging in and out, and managing admin/coach accounts. */
@Service
@Transactional
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");
    private static final int MIN_PASSWORD = 8;

    private final StaffRepository staff;
    private final ActivityRepository activityLog;
    private final ActivityService activity;
    private final TeamRepository teams;

    public AuthService(StaffRepository staff, ActivityRepository activityLog, ActivityService activity, TeamRepository teams) {
        this.staff = staff;
        this.activityLog = activityLog;
        this.activity = activity;
        this.teams = teams;
    }

    // ------------------------------------------------------------------ signing in

    public Me login(String username, String password, HttpServletRequest request) {
        String user = username == null ? "" : username.trim().toLowerCase();
        Staff account = staff.findByUsernameIgnoreCase(user).orElse(null);
        if (account == null || !PasswordHasher.matches(password, account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Wrong username or password.");
        }
        if (!account.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "This account has been turned off. Ask an admin.");
        }
        // Fresh session id on every login (stops session-fixation tricks).
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(Actor.SESSION_KEY,
                new Actor.SignedIn(account.getId(), account.getName(), account.getUsername(), account.getRole()));

        account.setLastLoginAt(LocalDateTime.now());
        staff.save(account);
        activity.record("Logged in");
        return toMe(account);
    }

    public void logout(HttpServletRequest request) {
        activity.record("Logged out");
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    /** The signed-in account, or null for the User view. */
    @Transactional(readOnly = true)
    public Me me(HttpServletRequest request) {
        Actor.SignedIn who = Actor.from(request);
        if (who == null) return null;
        Staff account = staff.findById(who.id()).orElse(null);
        if (account == null || !account.isActive()) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            return null;
        }
        return toMe(account);
    }

    public void changeOwnPassword(String current, String next, HttpServletRequest request) {
        Actor.SignedIn who = Actor.from(request);
        if (who == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "Log in first.");
        Staff account = find(who.id());
        if (!PasswordHasher.matches(current, account.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is not right.");
        }
        account.setPasswordHash(PasswordHasher.hash(checkPassword(next)));
        staff.save(account);
        activity.record("Changed own password");
    }

    // ------------------------------------------------------------------ accounts (admin only)

    @Transactional(readOnly = true)
    public List<StaffView> list() {
        return staff.findAllByOrderByRoleAscNameAsc().stream().map(s -> {
            LocalDateTime last = activityLog.findFirstByStaffIdOrderByIdDesc(s.getId()).map(ActivityEntry::getCreatedAt).orElse(null);
            return new StaffView(s.getId(), s.getName(), s.getUsername(), s.getRole().name(), s.isActive(),
                    s.getCreatedAt(), s.getLastLoginAt(), activityLog.countByStaffId(s.getId()), last, s.getTeamId());
        }).toList();
    }

    public StaffView create(String name, String username, String password, StaffRole role, Long teamId) {
        String user = checkUsername(username);
        if (staff.existsByUsernameIgnoreCase(user)) throw ApiException.conflict("The username " + user + " is taken.");
        Staff account = new Staff(checkName(name), user, PasswordHasher.hash(checkPassword(password)),
                role == null ? StaffRole.COACH : role);
        account.setTeamId(checkTeam(teamId));
        staff.save(account);
        activity.record("Created " + label(account) + " account for " + account.getName() + " (" + user + ")");
        return view(account);
    }

    public StaffView update(Long id, String name, StaffRole role, Boolean active, Long teamId) {
        Staff account = find(id);
        Actor.SignedIn me = Actor.current();
        boolean isMe = me != null && me.id().equals(id);
        StaffRole newRole = role == null ? account.getRole() : role;
        boolean newActive = active == null ? account.isActive() : active;

        if (isMe && (!newActive || newRole != StaffRole.ADMIN)) {
            throw ApiException.conflict("You can't turn off or demote your own account.");
        }
        boolean losingAdmin = account.getRole() == StaffRole.ADMIN && account.isActive()
                && (newRole != StaffRole.ADMIN || !newActive);
        if (losingAdmin && staff.countByRoleAndActiveTrue(StaffRole.ADMIN) <= 1) {
            throw ApiException.conflict("There must always be at least one active admin.");
        }

        StringBuilder what = new StringBuilder();
        if (name != null && !name.isBlank() && !name.trim().equals(account.getName())) {
            what.append(", renamed from ").append(account.getName());
            account.setName(checkName(name));
        }
        if (newRole != account.getRole()) {
            what.append(", role ").append(account.getRole().name().toLowerCase()).append(" to ").append(newRole.name().toLowerCase());
            account.setRole(newRole);
        }
        Long newTeam = checkTeam(teamId);
        if (!java.util.Objects.equals(newTeam, account.getTeamId())) {
            what.append(newTeam == null ? ", school cleared" : ", school set to " + teams.findById(newTeam).map(t -> t.getShortName()).orElse("?"));
            account.setTeamId(newTeam);
        }
        if (newActive != account.isActive()) {
            what.append(newActive ? ", turned on" : ", turned off");
            account.setActive(newActive);
        }
        staff.save(account);
        if (what.length() > 0) activity.record("Updated account " + account.getName() + what);
        return view(account);
    }

    public void resetPassword(Long id, String password) {
        Staff account = find(id);
        account.setPasswordHash(PasswordHasher.hash(checkPassword(password)));
        staff.save(account);
        activity.record("Reset the password for " + account.getName());
    }

    /** Creates the very first admin so someone can log in. Only used when there are no accounts. */
    public boolean createFirstAdmin(String name, String username, String password) {
        if (staff.count() > 0) return false;
        staff.save(new Staff(name, username, PasswordHasher.hash(password), StaffRole.ADMIN));
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private Staff find(Long id) {
        return staff.findById(id).orElseThrow(() -> ApiException.notFound("Account"));
    }

    private static Me toMe(Staff s) {
        return new Me(s.getId(), s.getName(), s.getUsername(), s.getRole().name(), s.getTeamId());
    }

    private StaffView view(Staff s) {
        return new StaffView(s.getId(), s.getName(), s.getUsername(), s.getRole().name(), s.isActive(),
                s.getCreatedAt(), s.getLastLoginAt(), activityLog.countByStaffId(s.getId()), null, s.getTeamId());
    }

    /** null is fine (no school); otherwise the team must exist. */
    private Long checkTeam(Long teamId) {
        if (teamId == null) return null;
        if (!teams.existsById(teamId)) throw ApiException.badRequest("That school doesn't exist any more. Pick another.");
        return teamId;
    }

    private static String label(Staff s) {
        return s.getRole() == StaffRole.ADMIN ? "an admin" : "a coach";
    }

    private static String checkName(String name) {
        if (name == null || name.isBlank()) throw ApiException.badRequest("Enter the person's name.");
        String n = name.trim();
        if (n.length() > 60) throw ApiException.badRequest("Name can be at most 60 characters.");
        return n;
    }

    private static String checkUsername(String username) {
        String u = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME.matcher(u).matches()) {
            throw ApiException.badRequest("Username must be 3 to 30 characters: letters, numbers, dot, dash or underscore.");
        }
        return u;
    }

    private static String checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Password must be at least " + MIN_PASSWORD + " characters.");
        }
        if (password.length() > 100) throw ApiException.badRequest("Password can be at most 100 characters.");
        return password;
    }
}
