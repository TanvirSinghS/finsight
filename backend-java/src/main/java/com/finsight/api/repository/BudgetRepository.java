package com.finsight.api.repository;

import com.finsight.api.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByUserIdAndPeriodStartOrderByCategoryNameAsc(UUID userId, LocalDate periodStart);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);

    Optional<Budget> findByUserIdAndCategoryIdAndPeriodStart(UUID userId, UUID categoryId, LocalDate periodStart);

    boolean existsByUserIdAndCategoryIdAndPeriodStart(UUID userId, UUID categoryId, LocalDate periodStart);

    List<Budget> findByUserIdAndPeriodStart(UUID userId, LocalDate periodStart);
}
