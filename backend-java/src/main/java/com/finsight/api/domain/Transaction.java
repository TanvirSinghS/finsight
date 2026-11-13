package com.finsight.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "recurring_id")
    private UUID recurringId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionType type;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "exchange_rate", nullable = false, precision = 14, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "amount_base", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountBase;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(length = 120)
    private String merchant;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Transaction() {
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public UUID getRecurringId() { return recurringId; }
    public void setRecurringId(UUID recurringId) { this.recurringId = recurringId; }
    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public BigDecimal getAmountBase() { return amountBase; }
    public void setAmountBase(BigDecimal amountBase) { this.amountBase = amountBase; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMerchant() { return merchant; }
    public void setMerchant(String merchant) { this.merchant = merchant; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public void setOccurredOn(LocalDate occurredOn) { this.occurredOn = occurredOn; }
    public Instant getCreatedAt() { return createdAt; }
}
