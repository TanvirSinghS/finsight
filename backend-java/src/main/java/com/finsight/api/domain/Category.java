package com.finsight.api.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CategoryKind kind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 7)
    private String color = "#6366f1";

    @Column(nullable = false, length = 40)
    private String icon = "tag";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Category() {
    }

    public Category(User user, String name, CategoryKind kind, String color, String icon) {
        this.user = user;
        this.name = name;
        this.kind = kind;
        this.color = color;
        this.icon = icon;
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CategoryKind getKind() { return kind; }
    public void setKind(CategoryKind kind) { this.kind = kind; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Instant getCreatedAt() { return createdAt; }
}
