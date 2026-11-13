package com.finsight.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type = AccountType.CHECKING;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "initial_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal initialBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Account() {
    }

    public Account(User user, String name, AccountType type, String currency, BigDecimal initialBalance) {
        this.user = user;
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.initialBalance = initialBalance;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AccountType getType() { return type; }
    public void setType(AccountType type) { this.type = type; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getInitialBalance() { return initialBalance; }
    public void setInitialBalance(BigDecimal initialBalance) { this.initialBalance = initialBalance; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
}
