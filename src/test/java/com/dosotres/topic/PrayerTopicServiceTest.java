package com.dosotres.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.topic.dto.CreateTopicRequest;
import com.dosotres.topic.dto.TopicResponse;
import com.dosotres.topic.dto.UpdateTopicRequest;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrayerTopicServiceTest {

    @Mock
    private PrayerTopicRepository topicRepository;
    @Mock
    private UserRepository userRepository;

    // Hoy = 2026-06-21, 12:00 UTC.
    private final Instant now = Instant.parse("2026-06-21T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneId.of("UTC"));

    private PrayerTopicService service;

    @BeforeEach
    void setUp() {
        service = new PrayerTopicService(topicRepository, userRepository, clock);
    }

    private User makeUser(Long id, boolean seeded) {
        User u = new User();
        u.setId(id);
        u.setTimezone("UTC");
        u.setPrayerTopicsSeeded(seeded);
        return u;
    }

    private PrayerTopic makeTopic(Long id, Long ownerId) {
        PrayerTopic t = new PrayerTopic();
        t.setId(id);
        t.setOwnerUserId(ownerId);
        t.setName("Familia");
        t.setTimezone("UTC");
        return t;
    }

    @Test
    void list_firstTime_seedsDefaultCatalog() {
        User user = makeUser(1L, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(topicRepository.findByOwnerUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        service.list(1L);

        verify(topicRepository, times(PrayerTopicService.DEFAULT_TOPICS.size())).save(any(PrayerTopic.class));
        assertThat(user.isPrayerTopicsSeeded()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    void list_secondTime_doesNotReseed() {
        User user = makeUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(topicRepository.findByOwnerUserIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(makeTopic(10L, 1L)));

        List<TopicResponse> res = service.list(1L);

        verify(topicRepository, never()).save(any(PrayerTopic.class));
        assertThat(res).hasSize(1);
    }

    @Test
    void create_savesTopic() {
        User user = makeUser(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(topicRepository.save(any(PrayerTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        TopicResponse res = service.create(1L, new CreateTopicRequest("Misiones", false, null));

        assertThat(res.name()).isEqualTo("Misiones");
        assertThat(res.reminderEnabled()).isFalse();
    }

    @Test
    void create_reminderEnabledWithoutTime_throws() {
        assertThatThrownBy(() -> service.create(1L, new CreateTopicRequest("X", true, null)))
                .isInstanceOf(ValidationException.class);
        verify(topicRepository, never()).save(any());
    }

    @Test
    void update_byOwner_changesNameAndReminder() {
        PrayerTopic topic = makeTopic(10L, 1L);
        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(topicRepository.save(any(PrayerTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        TopicResponse res = service.update(10L, 1L,
                new UpdateTopicRequest("Familia extendida", true, LocalTime.of(8, 0)));

        assertThat(res.name()).isEqualTo("Familia extendida");
        assertThat(res.reminderEnabled()).isTrue();
        assertThat(res.reminderTime()).isEqualTo("08:00");
    }

    @Test
    void update_disablingReminder_clearsTime() {
        PrayerTopic topic = makeTopic(10L, 1L);
        topic.setReminderEnabled(true);
        topic.setReminderTime(LocalTime.of(8, 0));
        topic.setLastRemindedOn(LocalDate.of(2026, 6, 20));
        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));
        when(topicRepository.save(any(PrayerTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        TopicResponse res = service.update(10L, 1L, new UpdateTopicRequest(null, false, null));

        assertThat(res.reminderEnabled()).isFalse();
        assertThat(res.reminderTime()).isNull();
        assertThat(topic.getLastRemindedOn()).isNull();
    }

    @Test
    void update_byNonOwner_throwsForbidden() {
        PrayerTopic topic = makeTopic(10L, 1L);
        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.update(10L, 99L, new UpdateTopicRequest("X", null, null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void delete_byNonOwner_throwsForbidden() {
        PrayerTopic topic = makeTopic(10L, 1L);
        when(topicRepository.findById(10L)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> service.delete(10L, 99L))
                .isInstanceOf(ForbiddenException.class);
        verify(topicRepository, never()).delete(any());
    }

    @Test
    void isReminderDue_enabledTimePassedNotRemindedToday_true() {
        PrayerTopic topic = makeTopic(10L, 1L);
        topic.setReminderEnabled(true);
        topic.setReminderTime(LocalTime.of(9, 0)); // antes de las 12 de hoy

        assertThat(service.isReminderDue(topic)).isTrue();
    }

    @Test
    void isReminderDue_timeNotYetReached_false() {
        PrayerTopic topic = makeTopic(10L, 1L);
        topic.setReminderEnabled(true);
        topic.setReminderTime(LocalTime.of(18, 0)); // después de las 12

        assertThat(service.isReminderDue(topic)).isFalse();
    }

    @Test
    void isReminderDue_alreadyRemindedToday_false() {
        PrayerTopic topic = makeTopic(10L, 1L);
        topic.setReminderEnabled(true);
        topic.setReminderTime(LocalTime.of(9, 0));
        topic.setLastRemindedOn(LocalDate.of(2026, 6, 21));

        assertThat(service.isReminderDue(topic)).isFalse();
    }

    @Test
    void isReminderDue_disabled_false() {
        PrayerTopic topic = makeTopic(10L, 1L);
        topic.setReminderEnabled(false);
        topic.setReminderTime(LocalTime.of(9, 0));

        assertThat(service.isReminderDue(topic)).isFalse();
    }

    @Test
    void markRemindedToday_setsToday() {
        PrayerTopic topic = makeTopic(10L, 1L);
        when(topicRepository.save(any(PrayerTopic.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markRemindedToday(topic);

        assertThat(topic.getLastRemindedOn()).isEqualTo(LocalDate.of(2026, 6, 21));
    }
}
