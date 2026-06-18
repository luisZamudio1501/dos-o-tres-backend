package com.dosotres.goal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.push.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalReminderJobTest {

    private static final Long OWNER = 5L;

    @Mock
    private GoalRepository goalRepository;
    @Mock
    private GoalService goalService;
    @Mock
    private PushNotificationService pushService;

    private GoalReminderJob job;

    @BeforeEach
    void setUp() {
        job = new GoalReminderJob(goalRepository, goalService, pushService, new ObjectMapper());
    }

    private PrayerGoal scheduledGoal() {
        PrayerGoal g = new PrayerGoal();
        g.setId(1L);
        g.setOwnerUserId(OWNER);
        g.setDailyMinutes(10);
        g.setMode(GoalMode.SCHEDULED);
        return g;
    }

    @Test
    void sendsReminderToOwnerWhenDue() {
        PrayerGoal goal = scheduledGoal();
        when(goalRepository.findByMode(GoalMode.SCHEDULED)).thenReturn(List.of(goal));
        when(goalService.isReminderDue(goal)).thenReturn(true);

        job.sendDueReminders();

        verify(pushService).sendToUsers(eq(List.of(OWNER)), anyString());
        verify(goalService).markRemindedToday(goal);
    }

    @Test
    void skipsWhenNotDue() {
        PrayerGoal goal = scheduledGoal();
        when(goalRepository.findByMode(GoalMode.SCHEDULED)).thenReturn(List.of(goal));
        when(goalService.isReminderDue(goal)).thenReturn(false);

        job.sendDueReminders();

        verify(pushService, never()).sendToUsers(any(), anyString());
        verify(goalService, never()).markRemindedToday(any());
    }
}
