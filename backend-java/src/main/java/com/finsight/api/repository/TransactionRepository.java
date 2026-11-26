package com.finsight.api.repository;

import com.finsight.api.domain.Transaction;
import com.finsight.api.domain.TransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @EntityGraph(attributePaths = {"account", "category"})
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"account", "category"})
    List<Transaction> findTop8ByUserIdOrderByOccurredOnDescCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = {"account", "category"})
    List<Transaction> findByUserIdAndOccurredOnBetweenOrderByOccurredOnAsc(UUID userId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"account", "category"})
    List<Transaction> findByUserIdAndOccurredOnAfterOrderByAmountDesc(UUID userId, LocalDate from);

    @Query("""
            SELECT COALESCE(SUM(t.amountBase), 0) FROM Transaction t
            WHERE t.user.id = :userId AND t.type = :type
              AND t.occurredOn BETWEEN :from AND :to
            """)
    BigDecimal sumByType(@Param("userId") UUID userId, @Param("type") TransactionType type,
                         @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            SELECT COALESCE(SUM(t.amountBase), 0) FROM Transaction t
            WHERE t.user.id = :userId AND t.type = :type AND t.category.id = :categoryId
              AND t.occurredOn BETWEEN :from AND :to
            """)
    BigDecimal sumByCategory(@Param("userId") UUID userId, @Param("type") TransactionType type,
                            @Param("categoryId") UUID categoryId,
                            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Spend per category for a window — replaces a query per budget. */
    @Query("""
            SELECT t.category.id, COALESCE(SUM(t.amountBase), 0) FROM Transaction t
            WHERE t.user.id = :userId AND t.type = :type
              AND t.occurredOn BETWEEN :from AND :to
            GROUP BY t.category.id
            """)
    List<Object[]> sumByCategoryGrouped(@Param("userId") UUID userId, @Param("type") TransactionType type,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** GROUP BY report: spending per category for a date window. */
    @Query(value = """
            SELECT c.id, c.name, c.color, SUM(t.amount_base) AS total, COUNT(*) AS tx_count
            FROM transactions t
                     JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.type = 'EXPENSE'
              AND t.occurred_on BETWEEN :from AND :to
            GROUP BY c.id, c.name, c.color
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> spendingByCategory(@Param("userId") UUID userId,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** GROUP BY report: income vs expense per day, used for the short cashflow ranges. */
    @Query(value = """
            SELECT to_char(t.occurred_on, 'YYYY-MM-DD') AS day,
                   COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_base ELSE 0 END), 0) AS income,
                   COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_base ELSE 0 END), 0) AS expenses
            FROM transactions t
            WHERE t.user_id = :userId AND t.occurred_on BETWEEN :from AND :to
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> dailyCashflow(@Param("userId") UUID userId,
                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** GROUP BY report: income vs expense per month. */
    @Query(value = """
            SELECT to_char(date_trunc('month', t.occurred_on), 'YYYY-MM') AS month,
                   COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_base ELSE 0 END), 0) AS income,
                   COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_base ELSE 0 END), 0) AS expenses
            FROM transactions t
            WHERE t.user_id = :userId AND t.occurred_on >= :from
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> monthlyCashflow(@Param("userId") UUID userId, @Param("from") LocalDate from);

    /** Monthly spend series per category — feeds the Python forecasting service. */
    @Query(value = """
            SELECT c.id, c.name, to_char(date_trunc('month', t.occurred_on), 'YYYY-MM'), SUM(t.amount_base)
            FROM transactions t
                     JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.type = 'EXPENSE'
              AND t.occurred_on >= :from
            GROUP BY c.id, c.name, 3
            ORDER BY 3
            """, nativeQuery = true)
    List<Object[]> monthlySpendByCategory(@Param("userId") UUID userId, @Param("from") LocalDate from);

    @Query(value = """
            SELECT t.id, t.amount_base, t.occurred_on, t.description,
                   COALESCE(c.name, 'Uncategorized'), COALESCE(c.color, '#94a3b8')
            FROM transactions t
                     LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.user_id = :userId AND t.type = 'EXPENSE'
              AND t.occurred_on >= :from
            ORDER BY t.occurred_on DESC
            """, nativeQuery = true)
    List<Object[]> expenseRowsForAnomalies(@Param("userId") UUID userId, @Param("from") LocalDate from);
}
