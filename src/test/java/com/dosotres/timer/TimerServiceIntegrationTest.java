package com.dosotres.timer;

import static org.assertj.core.api.Assertions.assertThat;

import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.adapter.JpaPrayerSessionRepository;
import com.dosotres.timer.dto.SessionResponse;
import com.dosotres.timer.dto.StartSessionRequest;
import com.dosotres.timer.dto.SyncSessionRequest;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.timer.cleanup-enabled=true",
        "app.timer.cleanup-cron=0 0 0 31 12 ?",
        "app.timer.abandon-threshold-minutes=30"
})
@Transactional
@Tag("integration")
class TimerServiceIntegrationTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-27T12:00:00Z");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        }
    }

    @Autowired
    private TimerService timerService;

    @Autowired
    private AbandonedSessionCleanupJob cleanupJob;

    @Autowired
    private JpaPrayerSessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private Long userId;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();

        User user = new User();
        user.setEmail("integration-test-" + System.nanoTime() + "@test.com");
        user.setDisplayName("Test User");
        user.setPasswordHash("hashed");
        user = userRepository.save(user);
        userId = user.getId();
    }

    @Test
    void startSyncStop_fullFlow() {
        SessionResponse started = timerService.start(
                new StartSessionRequest("int-test-1", null, null, null), userId);

        assertThat(started.status()).isEqualTo("ACTIVE");
        assertThat(started.durationSeconds()).isZero();

        SessionResponse synced = timerService.sync("int-test-1", new SyncSessionRequest(120), userId);

        assertThat(synced.durationSeconds()).isEqualTo(120);
        assertThat(synced.status()).isEqualTo("ACTIVE");

        SessionResponse stopped = timerService.stop("int-test-1", userId);

        assertThat(stopped.status()).isEqualTo("COMPLETED");
        assertThat(stopped.durationSeconds()).isEqualTo(120);

        PrayerSession fromDb = sessionRepository.findById("int-test-1").orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(fromDb.getDurationSeconds()).isEqualTo(120);
    }

    @Test
    @DirtiesContext
    void abandonedCleanup_marksStaleSessionsAsAbandoned() {
        PrayerSession stale = new PrayerSession();
        stale.setId("stale-session-1");
        stale.setUser(userRepository.findById(userId).orElseThrow());
        stale.setStartedAt(FIXED_NOW.minus(60, ChronoUnit.MINUTES));
        stale.setDurationSeconds(100);
        stale.setStatus(SessionStatus.ACTIVE);
        stale.setLastSyncAt(FIXED_NOW.minus(45, ChronoUnit.MINUTES));
        sessionRepository.saveAndFlush(stale);

        cleanupJob.cleanupAbandonedSessions();

        sessionRepository.flush();

        PrayerSession fromDb = sessionRepository.findById("stale-session-1").orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(SessionStatus.ABANDONED);
    }
}
