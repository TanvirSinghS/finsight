package com.finsight.api.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Budget() {
    }

    public Budget(User user, Category category, LocalDate periodStart, BigDecimal amount) {
        this.user = user;
        this.category = category;
        this.periodStart = periodStart;
        this.amount = amount;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Instant getCreatedAt() { return createdAt; }
}
