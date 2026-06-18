package com.dosotres.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.dosotres.prayer.PrayerRequestStatus;
import com.dosotres.stats.StatsRepository.PrayedRequestView;
import com.dosotres.stats.dto.GroupStatsResponse;
import com.dosotres.stats.dto.MeStatsResponse;
import com.dosotres.stats.dto.MeStatsResponse.Milestone;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    // 12:00 hora AR del 2026-06-16 (15:00 UTC).
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-16T15:00:00Z"), AR);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 16);
    private static final Long USER = 1L;
    private static final Long GROUP = 10L;

    @Mock
    private StatsRepository statsRepository;

    private StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(statsRepository, FIXED_CLOCK);
    }

    private PrayerSession session(LocalDate day, int durationSeconds) {
        PrayerSession s = new PrayerSession();
        s.setStartedAt(day.atTime(12, 0).atZone(AR).toInstant());
        s.setDurationSeconds(durationSeconds);
        s.setStatus(SessionStatus.COMPLETED);
        return s;
    }

    private void stub(List<PrayerSession> sessions, List<PrayedRequestView> prayed) {
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED)).thenReturn(sessions);
        when(statsRepository.findPrayedRequestsByUser(USER)).thenReturn(prayed);
    }

    private PrayedRequestView prayedRow(Long id, PrayerRequestStatus status) {
        return new PrayedRequestView() {
            @Override
            public Long getRequestId() {
                return id;
            }

            @Override
            public PrayerRequestStatus getStatus() {
                return status;
            }
        };
    }

    @Test
    void currentStreak_countsConsecutiveDaysEndingToday() {
        stub(List.of(
                session(TODAY, 600),
                session(TODAY.minusDays(1), 600),
                session(TODAY.minusDays(2), 600)
        ), List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.currentStreak()).isEqualTo(3);
        assertThat(stats.longestStreak()).isEqualTo(3);
    }

    @Test
    void currentStreak_staysAliveWhenTodayNotPrayedButYesterdayWas() {
        stub(List.of(
                session(TODAY.minusDays(1), 600),
                session(TODAY.minusDays(2), 600)
        ), List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.currentStreak()).isEqualTo(2);
    }

    @Test
    void currentStreak_isZeroWhenLastActiveDayBeforeYesterday() {
        stub(List.of(session(TODAY.minusDays(3), 600)), List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.currentStreak()).isZero();
        assertThat(stats.longestStreak()).isEqualTo(1);
    }

    @Test
    void shortSession_doesNotCountAsActiveDay() {
        stub(List.of(
                session(TODAY, 30),                 // < 60s → no cuenta para la racha
                session(TODAY.minusDays(1), 600)
        ), List.of());

        MeStatsResponse stats = service.meStats(USER);

        // Hoy no es día activo: la racha vive desde ayer.
        assertThat(stats.currentStreak()).isEqualTo(1);
    }

    @Test
    void minutes_splitsThisMonthFromTotal() {
        stub(List.of(
                session(LocalDate.of(2026, 6, 10), 600),   // este mes: 10 min
                session(LocalDate.of(2026, 5, 10), 600)    // mes anterior
        ), List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.minutesThisMonth()).isEqualTo(10);
        assertThat(stats.totalMinutes()).isEqualTo(20);
    }

    @Test
    void prayedRequests_countTotalAndAnswered() {
        stub(List.of(session(TODAY, 600)), List.of(
                prayedRow(10L, PrayerRequestStatus.ACTIVE),
                prayedRow(11L, PrayerRequestStatus.ANSWERED),
                prayedRow(12L, PrayerRequestStatus.ON_HOLD)
        ));

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.requestsPrayedFor()).isEqualTo(3);
        assertThat(stats.requestsAnswered()).isEqualTo(1);
    }

    @Test
    void heatmap_listsOnlyDaysWithMinutes() {
        stub(List.of(
                session(TODAY, 600),
                session(TODAY.minusDays(1), 30)   // < 1 min → 0 minutos → excluido
        ), List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.heatmap()).hasSize(1);
        assertThat(stats.heatmap().get(0).date()).isEqualTo(TODAY.toString());
        assertThat(stats.heatmap().get(0).minutes()).isEqualTo(10);
    }

    @Test
    void milestones_unlockByLongestStreakAndTotalMinutes() {
        java.util.List<PrayerSession> week = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            week.add(session(TODAY.minusDays(i), 900));   // 7 días × 15 min = 105 min
        }
        stub(week, List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(milestone(stats, "STREAK_7").achieved()).isTrue();
        assertThat(milestone(stats, "STREAK_30").achieved()).isFalse();
        assertThat(milestone(stats, "MINUTES_100").achieved()).isTrue();
    }

    @Test
    void emptyStats_areAllZeroAndMilestonesLocked() {
        stub(List.of(), List.of());

        MeStatsResponse stats = service.meStats(USER);

        assertThat(stats.currentStreak()).isZero();
        assertThat(stats.longestStreak()).isZero();
        assertThat(stats.totalMinutes()).isZero();
        assertThat(stats.requestsPrayedFor()).isZero();
        assertThat(stats.heatmap()).isEmpty();
        assertThat(stats.milestones()).allMatch(m -> !m.achieved());
    }

    private Milestone milestone(MeStatsResponse stats, String code) {
        return stats.milestones().stream()
                .filter(m -> m.code().equals(code))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void groupStats_aggregatesMinutesAnsweredAndActiveMembers() {
        when(statsRepository.sumGroupSeconds(GROUP)).thenReturn(3600L); // 60 min
        when(statsRepository.countAnsweredByGroup(GROUP)).thenReturn(4L);
        when(statsRepository.countActiveMembersSince(eq(GROUP), any(Instant.class))).thenReturn(3L);

        GroupStatsResponse stats = service.groupStats(GROUP);

        assertThat(stats.totalMinutes()).isEqualTo(60);
        assertThat(stats.answeredRequests()).isEqualTo(4);
        assertThat(stats.activeMembersThisWeek()).isEqualTo(3);
    }

    @Test
    void groupStats_usesSevenDayWindowFromClock() {
        when(statsRepository.sumGroupSeconds(GROUP)).thenReturn(0L);
        when(statsRepository.countAnsweredByGroup(GROUP)).thenReturn(0L);
        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        when(statsRepository.countActiveMembersSince(eq(GROUP), since.capture())).thenReturn(0L);

        service.groupStats(GROUP);

        // FIXED_CLOCK = 2026-06-16T15:00:00Z → exactamente 7 días antes.
        assertThat(since.getValue()).isEqualTo(Instant.parse("2026-06-09T15:00:00Z"));
    }
}
