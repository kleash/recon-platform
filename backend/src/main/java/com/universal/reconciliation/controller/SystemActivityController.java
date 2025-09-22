package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.SystemActivityDto;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.SystemActivityService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exposes the system activity feed for the UI dashboard.
 */
@RestController
@RequestMapping("/api/activity")
public class SystemActivityController {

    private final SystemActivityService systemActivityService;
    private final UserContext userContext;

    public SystemActivityController(SystemActivityService systemActivityService, UserContext userContext) {
        this.systemActivityService = systemActivityService;
        this.userContext = userContext;
    }

    @GetMapping
    public ResponseEntity<List<SystemActivityDto>> recentActivity() {
        if (userContext.getGroups().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "User must belong to at least one security group");
        }
        return ResponseEntity.ok(systemActivityService.fetchRecent());
    }
}

