package com.dosotres.topic;

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
 * Dispara los recordatorios de los temas con horario. La decisión de si un tema
 * toca ahora vive en PrayerTopicService; aquí solo se envía el push y se marca
 * como recordado. Best-effort: un error en un tema no frena el resto.
 */
@Component
@ConditionalOnProperty(name = "app.prayer-topic.reminders-enabled", havingValue = "true", matchIfMissing = true)
public class PrayerTopicReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PrayerTopicReminderJob.class);

    private final PrayerTopicRepository topicRepository;
    private final PrayerTopicService topicService;
    private final PushNotificationService pushService;
    private final ObjectMapper objectMapper;

    public PrayerTopicReminderJob(PrayerTopicRepository topicRepository,
                                  PrayerTopicService topicService,
                                  PushNotificationService pushService,
                                  ObjectMapper objectMapper) {
        this.topicRepository = topicRepository;
        this.topicService = topicService;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${app.prayer-topic.reminder-cron}")
    public void sendDueReminders() {
        for (PrayerTopic topic : topicRepository.findByReminderEnabledTrue()) {
            try {
                if (topicService.isReminderDue(topic)) {
                    pushService.sendToUsers(List.of(topic.getOwnerUserId()), payload(topic));
                    topicService.markRemindedToday(topic);
                    log.info("Topic reminder sent topicId={} userId={}", topic.getId(), topic.getOwnerUserId());
                }
            } catch (Exception e) {
                log.warn("Topic reminder failed topicId={}: {}", topic.getId(), e.getMessage());
            }
        }
    }

    private String payload(PrayerTopic topic) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", "🙏 Tema de oración",
                "body", "Acordate de orar por: " + topic.getName(),
                "url", "/temas"));
    }
}
