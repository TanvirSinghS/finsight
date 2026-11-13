package com.finsight.api.service;

import com.finsight.api.domain.Category;
import com.finsight.api.domain.User;
import com.finsight.api.dto.CategoryDtos.CategoryRequest;
import com.finsight.api.dto.CategoryDtos.CategoryResponse;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserService userService;

    public CategoryService(CategoryRepository categoryRepository, UserService userService) {
        this.categoryRepository = categoryRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId) {
        return categoryRepository.findByUserIdOrderByKindAscNameAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        User user = userService.get(userId);
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndKind(userId, name, request.kind())) {
            throw ApiException.conflict("A " + request.kind().name().toLowerCase() + " category named '"
                    + name + "' already exists.");
        }
        Category category = new Category(user, name, request.kind(),
                request.color() == null ? "#6366f1" : request.color(),
                request.icon() == null || request.icon().isBlank() ? "tag" : request.icon().trim());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID categoryId, CategoryRequest request) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("Category not found."));
        String name = request.name().trim();
        boolean duplicate = categoryRepository.findByUserIdOrderByKindAscNameAsc(userId).stream()
                .anyMatch(c -> !c.getId().equals(categoryId) && c.getName().equalsIgnoreCase(name)
                        && c.getKind() == request.kind());
        if (duplicate) {
            throw ApiException.conflict("A category with that name already exists.");
        }
        category.setName(name);
        category.setKind(request.kind());
        if (request.color() != null) {
            category.setColor(request.color());
        }
        if (request.icon() != null && !request.icon().isBlank()) {
            category.setIcon(request.icon().trim());
        }
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        Category category = categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> ApiException.notFound("Category not found."));
        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getKind(),
                category.getColor(), category.getIcon());
    }
}
