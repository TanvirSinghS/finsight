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

    /** Currencies in use, so FX rates can be fetched before the first request needs them. */
    @Query("SELECT DISTINCT a.currency FROM Account a")
    List<String> findDistinctCurrencies();

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.type = :income THEN t.amountBase ELSE -t.amountBase END), 0)
            FROM Transaction t WHERE t.account.id = :accountId
            """)
    BigDecimal netForAccount(@Param("accountId") UUID accountId, @Param("income") TransactionType income);

    /** One query for every account balance instead of one query per account. */
    @Query("""
            SELECT t.account.id, COALESCE(SUM(CASE WHEN t.type = :income THEN t.amountBase ELSE -t.amountBase END), 0)
            FROM Transaction t
            WHERE t.user.id = :userId
            GROUP BY t.account.id
            """)
    List<Object[]> netByAccount(@Param("userId") UUID userId, @Param("income") TransactionType income);
}
