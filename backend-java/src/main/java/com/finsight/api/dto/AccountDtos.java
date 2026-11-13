package com.finsight.api.dto;

import com.finsight.api.domain.AccountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public final class AccountDtos {
    private AccountDtos() {
    }

    public record AccountRequest(
            @NotBlank @Size(max = 80) String name,
            @NotNull AccountType type,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @DecimalMin(value = "-99999999.99") @Digits(integer = 8, fraction = 2) BigDecimal initialBalance) {
    }

    public record AccountResponse(UUID id, String name, AccountType type, String currency,
                                  BigDecimal initialBalance, BigDecimal balance, boolean archived) {
    }
}
