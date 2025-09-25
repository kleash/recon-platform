package com.universal.reconciliation.controller;

import com.universal.reconciliation.domain.dto.AddBreakCommentRequest;
import com.universal.reconciliation.domain.dto.BreakItemDto;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateRequest;
import com.universal.reconciliation.domain.dto.BulkBreakUpdateResponse;
import com.universal.reconciliation.domain.dto.UpdateBreakStatusRequest;
import com.universal.reconciliation.service.BreakService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles user actions against specific break records.
 */
@RestController
@RequestMapping("/api/breaks")
public class BreakController {

    private final BreakService breakService;

    public BreakController(BreakService breakService) {
        this.breakService = breakService;
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<BreakItemDto> addComment(
            @PathVariable("id") Long breakId, @Valid @RequestBody AddBreakCommentRequest request) {
        return ResponseEntity.ok(breakService.addComment(breakId, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<BreakItemDto> updateStatus(
            @PathVariable("id") Long breakId, @Valid @RequestBody UpdateBreakStatusRequest request) {
        return ResponseEntity.ok(breakService.updateStatus(breakId, request));
    }

    @PostMapping("/bulk")
    public ResponseEntity<BulkBreakUpdateResponse> bulkUpdate(
            @Valid @RequestBody BulkBreakUpdateRequest request) {
        return ResponseEntity.ok(breakService.bulkUpdate(request));
    }
}
