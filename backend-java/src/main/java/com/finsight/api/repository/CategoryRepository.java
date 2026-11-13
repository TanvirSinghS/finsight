package com.finsight.api.repository;

import com.finsight.api.domain.Category;
import com.finsight.api.domain.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByUserIdOrderByKindAscNameAsc(UUID userId);

    Optional<Category> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCaseAndKind(UUID userId, String name, CategoryKind kind);

    long countByUserId(UUID userId);
}
