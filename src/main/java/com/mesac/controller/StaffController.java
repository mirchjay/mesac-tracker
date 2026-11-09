package com.mesac.controller;

import com.mesac.dto.Requests.PasswordResetRequest;
import com.mesac.dto.Requests.StaffRequest;
import com.mesac.dto.Views.StaffView;
import com.mesac.model.ActivityEntry;
import com.mesac.service.ActivityService;
import com.mesac.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Admin only (checked by AuthInterceptor): coach/admin accounts and the activity log. */
@RestController
@RequestMapping("/api")
public class StaffController {

    private final AuthService auth;
    private final ActivityService activity;

    public StaffController(AuthService auth, ActivityService activity) {
        this.auth = auth;
        this.activity = activity;
    }

    @GetMapping("/staff")
    public List<StaffView> list() {
        return auth.list();
    }

    @PostMapping("/staff")
    public StaffView create(@RequestBody StaffRequest req) {
        return auth.create(req.name(), req.username(), req.password(), req.role());
    }

    @PutMapping("/staff/{id}")
    public StaffView update(@PathVariable("id") Long id, @RequestBody StaffRequest req) {
        return auth.update(id, req.name(), req.role(), req.active());
    }

    @PostMapping("/staff/{id}/password")
    public Map<String, Object> resetPassword(@PathVariable("id") Long id, @RequestBody PasswordResetRequest req) {
        auth.resetPassword(id, req.password());
        return Map.of("reset", true);
    }

    /** Newest first. Pass staffId to see one person's actions. */
    @GetMapping("/activity")
    public List<ActivityEntry> activity(@RequestParam(value = "staffId", required = false) Long staffId) {
        return activity.recent(staffId);
    }
}
