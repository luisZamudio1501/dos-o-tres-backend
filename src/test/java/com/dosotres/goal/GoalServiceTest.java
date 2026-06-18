package com.dosotres.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.goal.dto.CreateGoalRequest;
import com.dosotres.goal.dto.GoalResponse;
import com.dosotres.goal.dto.UpdateGoalRequest;
import com.dosotres.stats.StatsRepository;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-17T15:00:00Z"), AR);
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 17);
    private static final Long USER = 1L;
    private static final Long OTHER = 99L;

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private StatsRepository statsRepository;
    @Mock
    private UserRepository userRepository;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository, statsRepository, userRepository, FIXED_CLOCK);
    }

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setTimezone(AR.getId());
        return u;
    }

    private PrayerGoal makeGoal(Long id, Long owner, int dailyMinutes,
                               LocalDate start, LocalDate end, GoalMode mode) {
        PrayerGoal g = new PrayerGoal();
        g.setId(id);
        g.setOwnerUserId(owner);
        g.setDailyMinutes(dailyMinutes);
        g.setPeriodStart(start);
        g.setPeriodEnd(end);
        g.setMode(mode);
        g.setTimezone(AR.getId());
        return g;
    }

    private PrayerSession session(LocalDate day, int durationSeconds) {
        PrayerSession s = new PrayerSession();
        s.setStartedAt(day.atTime(12, 0).atZone(AR).toInstant());
        s.setDurationSeconds(durationSeconds);
        s.setStatus(SessionStatus.COMPLETED);
        return s;
    }

    @Test
    void create_setsFieldsAndTimezoneFromUser() {
        when(userRepository.findById(USER)).thenReturn(Optional.of(makeUser(USER)));
        when(goalRepository.save(any(PrayerGoal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED)).thenReturn(List.of());

        GoalResponse r = service.create(USER,
                new CreateGoalRequest(10, TODAY, TODAY.plusDays(30), GoalMode.FREE, null));

        ArgumentCaptor<PrayerGoal> captor = ArgumentCaptor.forClass(PrayerGoal.class);
        verify(goalRepository).save(captor.capture());
        PrayerGoal saved = captor.getValue();
        assertThat(saved.getOwnerUserId()).isEqualTo(USER);
        assertThat(saved.getDailyMinutes()).isEqualTo(10);
        assertThat(saved.getTimezone()).isEqualTo(AR.getId());
        assertThat(saved.getScheduledTime()).isNull();
        assertThat(r.dailyMinutes()).isEqualTo(10);
        assertThat(r.metToday()).isFalse();
    }

    @Test
    void create_scheduledWithoutTime_throwsValidation() {
        assertThatThrownBy(() -> service.create(USER,
                new CreateGoalRequest(10, TODAY, TODAY.plusDays(7), GoalMode.SCHEDULED, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void create_periodEndBeforeStart_throwsValidation() {
        assertThatThrownBy(() -> service.create(USER,
                new CreateGoalRequest(10, TODAY, TODAY.minusDays(1), GoalMode.FREE, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void list_computesTodayProgressAndStreak() {
        PrayerGoal goal = makeGoal(1L, USER, 10, TODAY.minusDays(5), TODAY.plusDays(5), GoalMode.FREE);
        when(goalRepository.findByOwnerUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(goal));
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED)).thenReturn(List.of(
                session(TODAY, 600),               // 10 min hoy
                session(TODAY.minusDays(1), 720),  // 12 min
                session(TODAY.minusDays(2), 600)   // 10 min
        ));

        GoalResponse r = service.list(USER).get(0);

        assertThat(r.todayMinutes()).isEqualTo(10);
        assertThat(r.metToday()).isTrue();
        assertThat(r.currentStreak()).isEqualTo(3);
        assertThat(r.active()).isTrue();
    }

    @Test
    void list_belowThreshold_notMetAndStreakZero() {
        PrayerGoal goal = makeGoal(1L, USER, 15, TODAY.minusDays(5), TODAY.plusDays(5), GoalMode.FREE);
        when(goalRepository.findByOwnerUserIdOrderByCreatedAtDesc(USER)).thenReturn(List.of(goal));
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED)).thenReturn(List.of(
                session(TODAY, 600)   // 10 min < meta de 15
        ));

        GoalResponse r = service.list(USER).get(0);

        assertThat(r.todayMinutes()).isEqualTo(10);
        assertThat(r.metToday()).isFalse();
        assertThat(r.currentStreak()).isZero();
    }

    @Test
    void get_forbiddenWhenNotOwner() {
        PrayerGoal goal = makeGoal(1L, OTHER, 10, TODAY, TODAY.plusDays(7), GoalMode.FREE);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        assertThatThrownBy(() -> service.get(1L, USER)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void update_changesDailyMinutes() {
        PrayerGoal goal = makeGoal(1L, USER, 10, TODAY, TODAY.plusDays(7), GoalMode.FREE);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(PrayerGoal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED)).thenReturn(List.of());

        GoalResponse r = service.update(1L, USER, new UpdateGoalRequest(20, null, null, null, null));

        assertThat(r.dailyMinutes()).isEqualTo(20);
    }

    @Test
    void delete_removesWhenOwner() {
        PrayerGoal goal = makeGoal(1L, USER, 10, TODAY, TODAY.plusDays(7), GoalMode.FREE);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        service.delete(1L, USER);

        verify(goalRepository).delete(goal);
    }

    private PrayerGoal scheduledGoal(LocalTime at) {
        PrayerGoal g = makeGoal(1L, USER, 10, TODAY.minusDays(2), TODAY.plusDays(2), GoalMode.SCHEDULED);
        g.setScheduledTime(at);
        return g;
    }

    @Test
    void isReminderDue_trueWhenTimePassedAndNotMet() {
        // Clock 12:00 AR; hora 11:00 ya pasó; sin sesiones hoy → no cumplió.
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED)).thenReturn(List.of());

        assertThat(service.isReminderDue(scheduledGoal(LocalTime.of(11, 0)))).isTrue();
    }

    @Test
    void isReminderDue_falseBeforeScheduledTime() {
        // 13:00 todavía no llegó (son las 12:00).
        assertThat(service.isReminderDue(scheduledGoal(LocalTime.of(13, 0)))).isFalse();
    }

    @Test
    void isReminderDue_falseWhenAlreadyMetToday() {
        when(statsRepository.findByUserIdAndStatus(USER, SessionStatus.COMPLETED))
                .thenReturn(List.of(session(TODAY, 600))); // 10 min = meta cumplida

        assertThat(service.isReminderDue(scheduledGoal(LocalTime.of(11, 0)))).isFalse();
    }

    @Test
    void isReminderDue_falseWhenAlreadyRemindedToday() {
        PrayerGoal goal = scheduledGoal(LocalTime.of(11, 0));
        goal.setLastRemindedOn(TODAY);

        assertThat(service.isReminderDue(goal)).isFalse();
    }

    @Test
    void isReminderDue_falseOutsidePeriod() {
        PrayerGoal goal = makeGoal(1L, USER, 10, TODAY.minusDays(10), TODAY.minusDays(1), GoalMode.SCHEDULED);
        goal.setScheduledTime(LocalTime.of(11, 0));

        assertThat(service.isReminderDue(goal)).isFalse();
    }

    @Test
    void isReminderDue_falseForFreeMode() {
        assertThat(service.isReminderDue(makeGoal(1L, USER, 10, TODAY, TODAY.plusDays(2), GoalMode.FREE)))
                .isFalse();
    }

    @Test
    void markRemindedToday_setsTodayAndSaves() {
        PrayerGoal goal = scheduledGoal(LocalTime.of(11, 0));
        when(goalRepository.save(any(PrayerGoal.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markRemindedToday(goal);

        assertThat(goal.getLastRemindedOn()).isEqualTo(TODAY);
        verify(goalRepository).save(goal);
    }
}
