package com.dosotres.goal;

import com.dosotres.push.PushNotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara los recordatorios de las metas con horario (SCHEDULED). La decisión de
 * si una meta toca ahora vive en GoalService; aquí solo se envía el push y se
 * marca como recordada. Best-effort: un error en una meta no frena el resto.
 */
@Component
@ConditionalOnProperty(name = "app.goal.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class GoalReminderJob {

    private static final Logger log = LoggerFactory.getLogger(GoalReminderJob.class);

    private final GoalRepository goalRepository;
    private final GoalService goalService;
    private final PushNotificationService pushService;
    private final ObjectMapper objectMapper;

    public GoalReminderJob(GoalRepository goalRepository,
                           GoalService goalService,
                           PushNotificationService pushService,
                           ObjectMapper objectMapper) {
        this.goalRepository = goalRepository;
        this.goalService = goalService;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${app.goal.reminder-cron}")
    public void sendDueReminders() {
        for (PrayerGoal goal : goalRepository.findByMode(GoalMode.SCHEDULED)) {
            try {
                if (goalService.isReminderDue(goal)) {
                    pushService.sendToUsers(List.of(goal.getOwnerUserId()), payload(goal));
                    goalService.markRemindedToday(goal);
                    log.info("Goal reminder sent goalId={} userId={}", goal.getId(), goal.getOwnerUserId());
                }
            } catch (Exception e) {
                log.warn("Goal reminder failed goalId={}: {}", goal.getId(), e.getMessage());
            }
        }
    }

    private String payload(PrayerGoal goal) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", "🙏 Tu momento de oración",
                "body", "Tu meta de hoy: " + goal.getDailyMinutes() + " min",
                "url", "/goals"));
    }
}
