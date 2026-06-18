package com.dosotres.security;

import com.dosotres.common.exception.ValidationException;
import com.dosotres.email.EmailService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PasswordResetService {

    private static final Duration TOKEN_EXPIRY = Duration.ofHours(1);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                Clock clock) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.clock = clock;
    }

    /** No revela si el email existe: siempre devuelve sin error. */
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            tokenRepository.deleteAllByUser(user);
            PasswordResetToken prt = new PasswordResetToken();
            prt.setUser(user);
            prt.setToken(UUID.randomUUID().toString().replace("-", ""));
            prt.setExpiresAt(Instant.now(clock).plus(TOKEN_EXPIRY));
            tokenRepository.save(prt);
            emailService.sendPasswordReset(user.getEmail(), prt.getToken());
        });
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new ValidationException("Token inválido o expirado"));
        if (prt.getUsedAt() != null || Instant.now(clock).isAfter(prt.getExpiresAt())) {
            throw new ValidationException("Token inválido o expirado");
        }
        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        prt.setUsedAt(Instant.now(clock));
        tokenRepository.save(prt);
    }
}
