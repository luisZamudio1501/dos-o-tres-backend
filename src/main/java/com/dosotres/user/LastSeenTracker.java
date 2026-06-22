package com.dosotres.user;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra la actividad "abrió la app" ({@code users.last_seen_at}) desde el
 * filtro de autenticación, con throttle de 1h para no escribir en cada request
 * (Plan Panel de Administración, Fase 1). El throttle vive en el UPDATE en SQL,
 * así que es seguro ante concurrencia y de costo despreciable.
 */
@Component
public class LastSeenTracker {

    private static final Duration THROTTLE = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final Clock clock;

    public LastSeenTracker(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional
    public void touch(Long userId) {
        Instant now = clock.instant();
        userRepository.touchLastSeen(userId, now, now.minus(THROTTLE));
    }
}
