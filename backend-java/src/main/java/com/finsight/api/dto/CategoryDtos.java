package com.finsight.api.dto;

import com.finsight.api.domain.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CategoryDtos {
    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull CategoryKind kind,
            @Pattern(regexp = "^#([0-9a-fA-F]{6})$", message = "Color must be a hex value like #6366f1") String color,
            @Size(max = 40) String icon) {
    }

    public record CategoryResponse(UUID id, String name, CategoryKind kind, String color, String icon) {
    }
}
