package com.dosotres.timer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.port.PrayerSessionPort;
import com.dosotres.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbandonedSessionCleanupJobTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-27T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock
    private PrayerSessionPort sessionPort;

    private AbandonedSessionCleanupJob job;

    @BeforeEach
    void setUp() {
        job = new AbandonedSessionCleanupJob(sessionPort, FIXED_CLOCK, 30);
    }

    @Test
    void cleanupAbandonedSessions_marksOldActiveSessions() {
        Instant threshold = FIXED_NOW.minusSeconds(30 * 60);

        User user = new User();
        user.setId(1L);

        PrayerSession s1 = new PrayerSession();
        s1.setId("session-1");
        s1.setUser(user);
        s1.setStatus(SessionStatus.ACTIVE);
        s1.setStartedAt(FIXED_NOW.minusSeconds(3600));
        s1.setLastSyncAt(FIXED_NOW.minusSeconds(3600));

        PrayerSession s2 = new PrayerSession();
        s2.setId("session-2");
        s2.setUser(user);
        s2.setStatus(SessionStatus.ACTIVE);
        s2.setStartedAt(FIXED_NOW.minusSeconds(7200));
        s2.setLastSyncAt(FIXED_NOW.minusSeconds(7200));

        when(sessionPort.findAbandonedBefore(threshold)).thenReturn(List.of(s1, s2));
        when(sessionPort.updateStatusBatch(eq(List.of("session-1", "session-2")), eq(SessionStatus.ABANDONED)))
                .thenReturn(2);

        job.cleanupAbandonedSessions();

        verify(sessionPort).findAbandonedBefore(threshold);
        verify(sessionPort).updateStatusBatch(List.of("session-1", "session-2"), SessionStatus.ABANDONED);
    }

    @Test
    void cleanupAbandonedSessions_noOpWhenNoAbandonedSessions() {
        Instant threshold = FIXED_NOW.minusSeconds(30 * 60);

        when(sessionPort.findAbandonedBefore(threshold)).thenReturn(List.of());

        job.cleanupAbandonedSessions();

        verify(sessionPort).findAbandonedBefore(threshold);
        verifyNoMoreInteractions(sessionPort);
    }
}
