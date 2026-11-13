package com.finsight.api.controller;

import com.finsight.api.dto.RecurringDtos.RecurringRequest;
import com.finsight.api.dto.RecurringDtos.RecurringResponse;
import com.finsight.api.service.RecurringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring")
@Tag(name = "Recurring", description = "Scheduled recurring income and bills")
public class RecurringController {
    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    @Operation(summary = "List recurring transactions")
    public List<RecurringResponse> list(@AuthenticationPrincipal UUID userId) {
        return recurringService.list(userId);
    }

    @PostMapping
    @Operation(summary = "Create a recurring transaction")
    public ResponseEntity<RecurringResponse> create(@AuthenticationPrincipal UUID userId,
                                                    @Valid @RequestBody RecurringRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a recurring transaction")
    public RecurringResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                    @Valid @RequestBody RecurringRequest request) {
        return recurringService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a recurring transaction")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        recurringService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/run-due")
    @Operation(summary = "Post due recurring transactions now",
            description = "The same job runs automatically every day at 06:00 UTC.")
    public Map<String, Integer> runDue(@AuthenticationPrincipal UUID userId) {
        return Map.of("created", recurringService.runDueForUser(userId));
    }
}
