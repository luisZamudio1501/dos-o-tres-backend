package com.dosotres.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ValidationException;
import com.dosotres.email.EmailService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-17T12:00:00Z"), ZoneOffset.UTC);

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;

    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, userRepository, passwordEncoder, emailService, FIXED);
    }

    private User user(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setDisplayName("Test");
        return u;
    }

    @Test
    void requestReset_savesTokenAndSendsEmail() {
        User u = user(1L, "test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(u));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestReset("test@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getToken()).isNotBlank().hasSize(32);
        assertThat(saved.getExpiresAt()).isAfter(Instant.now(FIXED));
        verify(emailService).sendPasswordReset(eq("test@example.com"), anyString());
    }

    @Test
    void requestReset_silentWhenEmailNotFound() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.requestReset("nobody@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(any(), any());
    }

    @Test
    void resetPassword_updatesPasswordAndMarksUsed() {
        User u = user(1L, "test@example.com");
        PasswordResetToken prt = validToken(u);
        when(tokenRepository.findByToken("abc123")).thenReturn(Optional.of(prt));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("newpass123")).thenReturn("hashed");

        service.resetPassword("abc123", "newpass123");

        assertThat(u.getPasswordHash()).isEqualTo("hashed");
        assertThat(prt.getUsedAt()).isNotNull();
    }

    @Test
    void resetPassword_failsWhenTokenNotFound() {
        when(tokenRepository.findByToken("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword("bad", "newpass123"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resetPassword_failsWhenTokenExpired() {
        User u = user(1L, "test@example.com");
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(u);
        prt.setToken("expired");
        prt.setExpiresAt(Instant.now(FIXED).minusSeconds(1));
        when(tokenRepository.findByToken("expired")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.resetPassword("expired", "newpass123"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resetPassword_failsWhenTokenAlreadyUsed() {
        User u = user(1L, "test@example.com");
        PasswordResetToken prt = validToken(u);
        prt.setUsedAt(Instant.now(FIXED).minusSeconds(60));
        when(tokenRepository.findByToken("used")).thenReturn(Optional.of(prt));

        assertThatThrownBy(() -> service.resetPassword("used", "newpass123"))
                .isInstanceOf(ValidationException.class);
    }

    private PasswordResetToken validToken(User user) {
        PasswordResetToken prt = new PasswordResetToken();
        prt.setUser(user);
        prt.setToken("abc123");
        prt.setExpiresAt(Instant.now(FIXED).plusSeconds(3600));
        return prt;
    }
}
