package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.SavedViewDto;
import com.universal.reconciliation.domain.dto.SavedViewRequest;
import com.universal.reconciliation.security.UserContext;
import com.universal.reconciliation.service.AnalystSavedViewService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * CRUD endpoints for analyst saved views.
 */
@RestController
@RequestMapping("/api")
public class SavedViewController {

    private final AnalystSavedViewService savedViewService;
    private final UserContext userContext;

    public SavedViewController(AnalystSavedViewService savedViewService, UserContext userContext) {
        this.savedViewService = savedViewService;
        this.userContext = userContext;
    }

    @GetMapping("/reconciliations/{id}/saved-views")
    public ResponseEntity<List<SavedViewDto>> list(@PathVariable("id") Long reconciliationId) {
        return ResponseEntity.ok(savedViewService.listViews(reconciliationId, userContext.getUsername()));
    }

    @PostMapping("/reconciliations/{id}/saved-views")
    public ResponseEntity<SavedViewDto> create(
            @PathVariable("id") Long reconciliationId,
            @Valid @RequestBody SavedViewRequest request) {
        SavedViewDto dto = savedViewService.create(
                reconciliationId, userContext.getUsername(), request, userContext.getGroups());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{viewId}")
                .buildAndExpand(dto.id())
                .toUri();
        return ResponseEntity.created(location).body(dto);
    }

    @PutMapping("/reconciliations/{id}/saved-views/{viewId}")
    public ResponseEntity<SavedViewDto> update(
            @PathVariable("id") Long reconciliationId,
            @PathVariable Long viewId,
            @Valid @RequestBody SavedViewRequest request) {
        SavedViewDto dto = savedViewService.update(
                reconciliationId, viewId, userContext.getUsername(), request, userContext.getGroups());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/reconciliations/{id}/saved-views/{viewId}")
    public ResponseEntity<Void> delete(
            @PathVariable("id") Long reconciliationId, @PathVariable Long viewId) {
        savedViewService.delete(reconciliationId, viewId, userContext.getUsername(), userContext.getGroups());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reconciliations/{id}/saved-views/{viewId}/default")
    public ResponseEntity<Void> setDefault(
            @PathVariable("id") Long reconciliationId, @PathVariable Long viewId) {
        savedViewService.setDefault(reconciliationId, viewId, userContext.getUsername(), userContext.getGroups());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/saved-views/shared/{token}")
    public ResponseEntity<SavedViewDto> resolveShared(@PathVariable String token) {
        return savedViewService.findShared(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

