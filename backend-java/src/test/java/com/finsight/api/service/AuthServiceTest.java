package com.finsight.api.service;

import com.finsight.api.config.JwtService;
import com.finsight.api.domain.Account;
import com.finsight.api.domain.Category;
import com.finsight.api.domain.User;
import com.finsight.api.dto.AuthDtos.AuthResponse;
import com.finsight.api.dto.AuthDtos.LoginRequest;
import com.finsight.api.dto.AuthDtos.RegisterRequest;
import com.finsight.api.exception.ApiException;
import com.finsight.api.repository.AccountRepository;
import com.finsight.api.repository.CategoryRepository;
import com.finsight.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, categoryRepository, accountRepository,
                passwordEncoder, jwtService);
    }

    @Test
    void registerSeedsDefaultCategoriesAndCheckingAccount() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(604800L);

        AuthResponse response = authService.register(
                new RegisterRequest("New@Example.com", "Password1", "New Person", "EUR"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.user().email()).isEqualTo("new@example.com");
        assertThat(response.user().baseCurrency()).isEqualTo("EUR");

        verify(categoryRepository, times(13)).save(any(Category.class));
        verify(accountRepository).save(any(Account.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("taken@example.com", "Password1", "Someone", "USD")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("alex@example.com", "stored-hash", "Alex", "USD");
        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alex@example.com", "wrong")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = new User("alex@example.com", "stored-hash", "Alex", "USD");
        when(userRepository.findByEmailIgnoreCase("alex@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Demo@1234", "stored-hash")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(new LoginRequest("alex@example.com", "Demo@1234"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        verify(passwordEncoder, never()).encode(anyString());
    }
}
