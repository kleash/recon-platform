package com.universal.reconciliation.controller.harness;

import com.universal.reconciliation.domain.entity.AccessControlEntry;
import com.universal.reconciliation.domain.entity.BreakItem;
import com.universal.reconciliation.domain.entity.ReconciliationDefinition;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.UpdateBreakStatusRequest;
import com.universal.reconciliation.domain.enums.BreakStatus;
import com.universal.reconciliation.repository.BreakItemRepository;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.BreakAccessService;
import com.universal.reconciliation.service.BreakService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/**
 * Harness-only endpoints to help debug maker/checker permissions during automated tests.
 */
@RestController
@Profile("example-harness")
@RequestMapping("/api/harness")
public class HarnessDebugController {

    private final BreakItemRepository breakItemRepository;
    private final BreakAccessService breakAccessService;
    private final UserContext userContext;
    private final BreakService breakService;

    public HarnessDebugController(
            BreakItemRepository breakItemRepository,
            BreakAccessService breakAccessService,
            UserContext userContext,
            BreakService breakService) {
        this.breakItemRepository = breakItemRepository;
        this.breakAccessService = breakAccessService;
        this.userContext = userContext;
        this.breakService = breakService;
    }

    @GetMapping("/breaks/{id}/entries")
    public Map<String, Object> inspectBreakEntries(@PathVariable("id") Long breakId) {
        BreakItem item = breakItemRepository.findById(breakId)
                .orElseThrow(() -> new IllegalArgumentException("Break not found"));
        ReconciliationDefinition definition = item.getRun().getDefinition();
        List<String> groups = userContext.getGroups();

        Map<String, Object> response = new HashMap<>();
        response.put("groups", groups);
        response.put("product", item.getProduct());
        response.put("subProduct", item.getSubProduct());
        response.put("entity", item.getEntityName());
        response.put("status", item.getStatus());

        try {
            List<AccessControlEntry> entries = breakAccessService.findEntries(definition, groups);
            List<AccessControlEntry> scoped = breakAccessService.scopedEntries(item, entries);
            response.put("entries", entries.stream().map(this::describeEntry).collect(Collectors.toList()));
            response.put("scopedEntries", scoped.stream().map(this::describeEntry).collect(Collectors.toList()));
            List<String> allowed = breakAccessService.allowedStatuses(item, definition, entries).stream()
                    .map(Enum::name)
                    .toList();
            boolean maker = scoped.stream().anyMatch(entry -> entry.getRole() != null && entry.getRole().name().equals("MAKER"));
            boolean checker = scoped.stream().anyMatch(entry -> entry.getRole() != null && entry.getRole().name().equals("CHECKER"));
            response.put("allowedStatuses", allowed);
            response.put("makerRole", maker);
            response.put("checkerRole", checker);
        } catch (AccessDeniedException ex) {
            response.put("error", ex.getMessage());
        }

        return response;
    }

    @PostMapping("/breaks/{id}/status")
    public Map<String, Object> attemptStatusUpdate(
            @PathVariable("id") Long breakId, @RequestBody HarnessStatusRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            BreakStatus status = BreakStatus.valueOf(request.status().toUpperCase());
            BreakItemDto dto = breakService.updateStatus(
                    breakId, new UpdateBreakStatusRequest(status, request.comment(), request.correlationId()));
            response.put("result", dto);
        } catch (Exception ex) {
            response.put("error", ex.getClass().getName());
            response.put("message", ex.getMessage());
        }
        return response;
    }

    private Map<String, Object> describeEntry(AccessControlEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("group", entry.getLdapGroupDn());
        map.put("role", entry.getRole());
        map.put("product", entry.getProduct());
        map.put("subProduct", entry.getSubProduct());
        map.put("entity", entry.getEntityName());
        return map;
    }

    private record HarnessStatusRequest(String status, String comment, String correlationId) {}
}
