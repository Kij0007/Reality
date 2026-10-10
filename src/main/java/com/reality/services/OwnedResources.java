package com.reality.services;

import com.reality.entity.Activity;
import com.reality.entity.Session;
import com.reality.exception.ResourceNotFoundException;
import com.reality.repository.ActivityRepository;
import com.reality.repository.SessionRepository;
import com.reality.security.CurrentUser;
import org.springframework.stereotype.Component;

/** Resource existence and ownership are checked together, so foreign IDs return the same 404. */
@Component
public class OwnedResources {
    private final ActivityRepository activities;
    private final SessionRepository sessions;
    private final CurrentUser currentUser;

    public OwnedResources(ActivityRepository activities, SessionRepository sessions, CurrentUser currentUser) {
        this.activities = activities;
        this.sessions = sessions;
        this.currentUser = currentUser;
    }

    public Long ownerId() {
        return currentUser.id();
    }

    public Activity activeActivity(Long id) {
        return activities.findByIdAndOwnerIdAndActiveTrue(id, ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Active activity not found with id: " + id));
    }

    public Activity activity(Long id) {
        return activities.findByIdAndOwnerId(id, ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found with id: " + id));
    }

    public Session session(Long id) {
        return sessions.findByIdAndActivityOwnerId(id, ownerId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with id: " + id));
    }
}
