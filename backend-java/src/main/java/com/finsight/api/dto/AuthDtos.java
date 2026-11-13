package com.finsight.api.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email(message = "A valid email is required") @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO code") String baseCurrency) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record UserResponse(UUID id, String email, String fullName, String baseCurrency, Instant createdAt) {
    }

    public record AuthResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    }
}
