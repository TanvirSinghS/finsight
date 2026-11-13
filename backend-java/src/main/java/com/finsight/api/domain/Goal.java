package com.finsight.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goals")
public class Goal {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7)
    private String color = "#10b981";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Goal() {
    }

    public Goal(User user, String name, BigDecimal targetAmount) {
        this.user = user;
        this.name = name;
        this.targetAmount = targetAmount;
    }

    public Goal(User user, String name, BigDecimal targetAmount, BigDecimal currentAmount, LocalDate targetDate, String color) {
        this.user = user;
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.targetDate = targetDate;
        this.color = color;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public void setTargetAmount(BigDecimal targetAmount) { this.targetAmount = targetAmount; }
    public BigDecimal getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(BigDecimal currentAmount) { this.currentAmount = currentAmount; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Instant getCreatedAt() { return createdAt; }
}
