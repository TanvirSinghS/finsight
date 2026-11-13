package com.finsight.api.controller;

import com.finsight.api.dto.GoalDtos.ContributionRequest;
import com.finsight.api.dto.GoalDtos.GoalRequest;
import com.finsight.api.dto.GoalDtos.GoalResponse;
import com.finsight.api.service.GoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
@Tag(name = "Goals", description = "Savings goals and contributions")
public class GoalController {
    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    @Operation(summary = "List savings goals")
    public List<GoalResponse> list(@AuthenticationPrincipal UUID userId) {
        return goalService.list(userId);
    }

    @PostMapping
    @Operation(summary = "Create a goal")
    public ResponseEntity<GoalResponse> create(@AuthenticationPrincipal UUID userId,
                                               @Valid @RequestBody GoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(goalService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a goal")
    public GoalResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                               @Valid @RequestBody GoalRequest request) {
        return goalService.update(userId, id, request);
    }

    @PostMapping("/{id}/contribute")
    @Operation(summary = "Add money to a goal")
    public GoalResponse contribute(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                   @Valid @RequestBody ContributionRequest request) {
        return goalService.contribute(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a goal")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        goalService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
