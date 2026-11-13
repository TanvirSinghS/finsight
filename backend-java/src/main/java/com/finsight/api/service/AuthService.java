package com.finsight.api.service;

import com.finsight.api.config.JwtService;
import com.finsight.api.domain.*;
import com.finsight.api.dto.AuthDtos.*;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.AccountRepository;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, CategoryRepository categoryRepository,
                       AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    private record DefaultCategory(String name, CategoryKind kind, String color, String icon) {
    }

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("Groceries", CategoryKind.EXPENSE, "#22c55e", "shopping-cart"),
            new DefaultCategory("Restaurants", CategoryKind.EXPENSE, "#f97316", "utensils"),
            new DefaultCategory("Coffee", CategoryKind.EXPENSE, "#a16207", "coffee"),
            new DefaultCategory("Rent", CategoryKind.EXPENSE, "#ef4444", "home"),
            new DefaultCategory("Utilities", CategoryKind.EXPENSE, "#0ea5e9", "zap"),
            new DefaultCategory("Transport", CategoryKind.EXPENSE, "#14b8a6", "bus"),
            new DefaultCategory("Shopping", CategoryKind.EXPENSE, "#ec4899", "shopping-bag"),
            new DefaultCategory("Entertainment", CategoryKind.EXPENSE, "#8b5cf6", "film"),
            new DefaultCategory("Health", CategoryKind.EXPENSE, "#10b981", "heart-pulse"),
            new DefaultCategory("Subscriptions", CategoryKind.EXPENSE, "#f43f5e", "repeat"),
            new DefaultCategory("Salary", CategoryKind.INCOME, "#059669", "banknote"),
            new DefaultCategory("Freelance", CategoryKind.INCOME, "#0d9488", "laptop"),
            new DefaultCategory("Other Income", CategoryKind.INCOME, "#6366f1", "plus-circle")
    );

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with that email already exists.");
        }
        String currency = request.baseCurrency() == null || request.baseCurrency().isBlank()
                ? "USD" : request.baseCurrency().toUpperCase(Locale.ROOT);
        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), currency));

        for (DefaultCategory category : DEFAULT_CATEGORIES) {
            categoryRepository.save(new Category(user, category.name(), category.kind(),
                    category.color(), category.icon()));
        }
        accountRepository.save(new Account(user, "Main Checking", AccountType.CHECKING, currency, BigDecimal.ZERO));

        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password."));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized("Invalid email or password.");
        }
        return authResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Session no longer valid. Please sign in again."));
        return toUserResponse(user);
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.getExpirationSeconds(),
                toUserResponse(user));
    }

    public static UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getBaseCurrency(),
                user.getCreatedAt());
    }
}
