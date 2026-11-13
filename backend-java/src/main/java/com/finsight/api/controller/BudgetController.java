package com.finsight.api.controller;

import com.finsight.api.dto.BudgetDtos.BudgetRequest;
import com.finsight.api.dto.BudgetDtos.BudgetResponse;
import com.finsight.api.service.BudgetService;
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
@RequestMapping("/api/budgets")
@Tag(name = "Budgets", description = "Monthly budgets with live progress")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    @Operation(summary = "List budgets for a month (YYYY-MM, defaults to the current month)")
    public List<BudgetResponse> list(@AuthenticationPrincipal UUID userId,
                                     @RequestParam(required = false) String month) {
        return budgetService.list(userId, month);
    }

    @PostMapping
    @Operation(summary = "Create a budget")
    public ResponseEntity<BudgetResponse> create(@AuthenticationPrincipal UUID userId,
                                                 @Valid @RequestBody BudgetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(budgetService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a budget")
    public BudgetResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                 @Valid @RequestBody BudgetRequest request) {
        return budgetService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a budget")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        budgetService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
