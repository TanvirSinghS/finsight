package com.finsight.api.controller;

import com.finsight.api.dto.CategoryDtos.CategoryRequest;
import com.finsight.api.dto.CategoryDtos.CategoryResponse;
import com.finsight.api.service.CategoryService;
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
@RequestMapping("/api/categories")
@Tag(name = "Categories", description = "Income and expense categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List categories")
    public List<CategoryResponse> list(@AuthenticationPrincipal UUID userId) {
        return categoryService.list(userId);
    }

    @PostMapping
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@AuthenticationPrincipal UUID userId,
                                                   @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(userId, request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    public CategoryResponse update(@AuthenticationPrincipal UUID userId, @PathVariable UUID id,
                                   @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category", description = "Transactions keep their history and become uncategorized.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UUID userId, @PathVariable UUID id) {
        categoryService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
