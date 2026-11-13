package com.finsight.api.repository;

import com.finsight.api.domain.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, UUID> {
    List<RecurringTransaction> findByUserIdOrderByNextRunDateAsc(UUID userId);

    Optional<RecurringTransaction> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            SELECT r FROM RecurringTransaction r
            JOIN FETCH r.user JOIN FETCH r.account
            LEFT JOIN FETCH r.category
            WHERE r.active = true AND r.nextRunDate <= :today
            """)
    List<RecurringTransaction> findDue(@Param("today") LocalDate today);
}
