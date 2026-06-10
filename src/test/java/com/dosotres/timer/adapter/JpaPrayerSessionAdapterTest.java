package com.dosotres.timer.adapter;

import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaPrayerSessionAdapterTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-27T12:00:00Z"), ZONE);

    @Mock
    private JpaPrayerSessionRepository repo;

    private JpaPrayerSessionAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new JpaPrayerSessionAdapter(repo, FIXED_CLOCK);
    }

    @Test
    void save_delegatesToRepo() {
        PrayerSession session = new PrayerSession();
        session.setId("uuid-1");
        when(repo.save(session)).thenReturn(session);

        PrayerSession result = adapter.save(session);

        assertThat(result).isSameAs(session);
        verify(repo).save(session);
    }

    @Test
    void findById_delegatesToRepo() {
        PrayerSession session = new PrayerSession();
        session.setId("uuid-1");
        when(repo.findById("uuid-1")).thenReturn(Optional.of(session));

        Optional<PrayerSession> result = adapter.findById("uuid-1");

        assertThat(result).contains(session);
        verify(repo).findById("uuid-1");
    }

    @Test
    void findActiveByUserId_delegatesWithActiveStatus() {
        PrayerSession session = new PrayerSession();
        when(repo.findByUserIdAndStatus(1L, SessionStatus.ACTIVE))
                .thenReturn(Optional.of(session));

        Optional<PrayerSession> result = adapter.findActiveByUserId(1L);

        assertThat(result).contains(session);
        verify(repo).findByUserIdAndStatus(1L, SessionStatus.ACTIVE);
    }

    @Test
    void findByUserAndDateRange_convertsLocalDateToInstant() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        when(repo.findByUserIdAndStartedAtBetween(1L, fromInstant, toInstant))
                .thenReturn(List.of());

        List<PrayerSession> result = adapter.findByUserAndDateRange(1L, from, to);

        assertThat(result).isEmpty();
        verify(repo).findByUserIdAndStartedAtBetween(1L, fromInstant, toInstant);
    }

    @Test
    void totalSeconds_convertsLocalDateToInstant() {
        LocalDate from = LocalDate.of(2026, 5, 1);
        LocalDate to = LocalDate.of(2026, 5, 31);
        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        when(repo.sumDurationByUserAndRange(1L, fromInstant, toInstant)).thenReturn(3600L);

        long result = adapter.totalSeconds(1L, from, to);

        assertThat(result).isEqualTo(3600L);
        verify(repo).sumDurationByUserAndRange(1L, fromInstant, toInstant);
    }

    @Test
    void findAbandonedBefore_delegatesWithActiveStatus() {
        Instant threshold = Instant.parse("2026-05-27T10:00:00Z");
        when(repo.findByStatusAndLastSyncAtBefore(SessionStatus.ACTIVE, threshold))
                .thenReturn(List.of());

        List<PrayerSession> result = adapter.findAbandonedBefore(threshold);

        assertThat(result).isEmpty();
        verify(repo).findByStatusAndLastSyncAtBefore(SessionStatus.ACTIVE, threshold);
    }

    @Test
    void updateStatusBatch_delegatesToRepo() {
        List<String> ids = List.of("uuid-1", "uuid-2");
        when(repo.updateStatusBatch(ids, SessionStatus.ABANDONED)).thenReturn(2);

        int result = adapter.updateStatusBatch(ids, SessionStatus.ABANDONED);

        assertThat(result).isEqualTo(2);
        verify(repo).updateStatusBatch(ids, SessionStatus.ABANDONED);
    }
}
