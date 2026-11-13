package com.finsight.api.repository;

import com.finsight.api.domain.Account;
import com.finsight.api.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByUserIdAndArchivedFalseOrderByName(UUID userId);

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type = :income THEN t.amountBase ELSE -t.amountBase END), 0)
            FROM Transaction t WHERE t.account.id = :accountId
            """)
    BigDecimal netForAccount(@Param("accountId") UUID accountId, @Param("income") TransactionType income);
}
