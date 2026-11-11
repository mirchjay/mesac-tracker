package com.mesac.service;

import com.mesac.model.ActivityEntry;
import com.mesac.repository.ActivityRepository;
import com.mesac.web.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Writes "who did what" to the activity log. Called from every service that changes data. */
@Service
@Transactional
public class ActivityService {

    private final ActivityRepository log;

    public ActivityService(ActivityRepository log) {
        this.log = log;
    }

    /** Records an action by whoever is signed in. Does nothing for anonymous or start-up work. */
    public void record(String action, Long matchId) {
        Actor.SignedIn who = Actor.current();
        if (who == null || action == null) return;
        String text = action.length() > 400 ? action.substring(0, 400) : action;
        log.save(new ActivityEntry(who.id(), who.name(), text, matchId));
    }

    public void record(String action) {
        record(action, null);
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> recent(Long staffId) {
        return staffId == null ? log.findTop300ByOrderByIdDesc() : log.findTop300ByStaffIdOrderByIdDesc(staffId);
    }
}
