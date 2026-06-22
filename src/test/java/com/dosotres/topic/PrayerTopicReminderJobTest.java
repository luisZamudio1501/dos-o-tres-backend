package com.dosotres.topic;

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
class PrayerTopicReminderJobTest {

    private static final Long OWNER = 5L;

    @Mock
    private PrayerTopicRepository topicRepository;
    @Mock
    private PrayerTopicService topicService;
    @Mock
    private PushNotificationService pushService;

    private PrayerTopicReminderJob job;

    @BeforeEach
    void setUp() {
        job = new PrayerTopicReminderJob(topicRepository, topicService, pushService, new ObjectMapper());
    }

    private PrayerTopic enabledTopic() {
        PrayerTopic t = new PrayerTopic();
        t.setId(1L);
        t.setOwnerUserId(OWNER);
        t.setName("Familia");
        t.setReminderEnabled(true);
        return t;
    }

    @Test
    void sendsReminderToOwnerWhenDue() {
        PrayerTopic topic = enabledTopic();
        when(topicRepository.findByReminderEnabledTrue()).thenReturn(List.of(topic));
        when(topicService.isReminderDue(topic)).thenReturn(true);

        job.sendDueReminders();

        verify(pushService).sendToUsers(eq(List.of(OWNER)), anyString());
        verify(topicService).markRemindedToday(topic);
    }

    @Test
    void skipsWhenNotDue() {
        PrayerTopic topic = enabledTopic();
        when(topicRepository.findByReminderEnabledTrue()).thenReturn(List.of(topic));
        when(topicService.isReminderDue(topic)).thenReturn(false);

        job.sendDueReminders();

        verify(pushService, never()).sendToUsers(any(), anyString());
        verify(topicService, never()).markRemindedToday(any());
    }
}
