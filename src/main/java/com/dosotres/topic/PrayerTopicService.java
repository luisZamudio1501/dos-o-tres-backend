package com.dosotres.topic;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
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
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Temas de oración personales (Fase 8, ADR-014): recurrentes, no se responden,
 * con recordatorio opcional por horario. El recordatorio reusa el patrón de las
 * metas (GoalService) pero sin período: los temas no expiran.
 */
@Service
@Transactional
public class PrayerTopicService {

    private static final String DEFAULT_TZ = "America/Argentina/Buenos_Aires";

    /** Catálogo default sembrado una sola vez por usuario. */
    static final List<String> DEFAULT_TOPICS =
            List.of("Familia", "Hijos", "Trabajo", "Salud", "Iglesia", "Países/Misiones");

    private final PrayerTopicRepository topicRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public PrayerTopicService(PrayerTopicRepository topicRepository,
                              UserRepository userRepository,
                              Clock clock) {
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** Lista los temas del usuario; siembra el catálogo default en el primer acceso. */
    public List<TopicResponse> list(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (!user.isPrayerTopicsSeeded()) {
            String tz = user.getTimezone() != null ? user.getTimezone() : DEFAULT_TZ;
            for (String name : DEFAULT_TOPICS) {
                PrayerTopic topic = new PrayerTopic();
                topic.setOwnerUserId(userId);
                topic.setName(name);
                topic.setReminderEnabled(false);
                topic.setTimezone(tz);
                topicRepository.save(topic);
            }
            user.setPrayerTopicsSeeded(true);
            userRepository.save(user);
        }

        return topicRepository.findByOwnerUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TopicResponse create(Long userId, CreateTopicRequest req) {
        if (req.reminderEnabled() && req.reminderTime() == null) {
            throw new ValidationException("Un recordatorio necesita una hora");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PrayerTopic topic = new PrayerTopic();
        topic.setOwnerUserId(userId);
        topic.setName(req.name().trim());
        topic.setReminderEnabled(req.reminderEnabled());
        topic.setReminderTime(req.reminderEnabled() ? req.reminderTime() : null);
        topic.setTimezone(user.getTimezone() != null ? user.getTimezone() : DEFAULT_TZ);
        topicRepository.save(topic);

        return toResponse(topic);
    }

    public TopicResponse update(Long id, Long userId, UpdateTopicRequest req) {
        PrayerTopic topic = findOwned(id, userId);

        if (req.name() != null && !req.name().isBlank()) {
            topic.setName(req.name().trim());
        }
        if (req.reminderTime() != null) {
            topic.setReminderTime(req.reminderTime());
        }
        if (req.reminderEnabled() != null) {
            topic.setReminderEnabled(req.reminderEnabled());
            if (!req.reminderEnabled()) {
                topic.setReminderTime(null);
                topic.setLastRemindedOn(null);
            }
        }
        if (topic.isReminderEnabled() && topic.getReminderTime() == null) {
            throw new ValidationException("Un recordatorio necesita una hora");
        }

        topicRepository.save(topic);
        return toResponse(topic);
    }

    public void delete(Long id, Long userId) {
        topicRepository.delete(findOwned(id, userId));
    }

    /**
     * ¿Corresponde recordar este tema ahora? Habilitado, con hora ya pasada en su
     * zona y no recordado hoy. Sin período (los temas no expiran).
     */
    @Transactional(readOnly = true)
    public boolean isReminderDue(PrayerTopic topic) {
        if (!topic.isReminderEnabled() || topic.getReminderTime() == null) {
            return false;
        }
        ZoneId zone = zoneOf(topic);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);
        if (today.equals(topic.getLastRemindedOn())) {
            return false;
        }
        return !LocalTime.ofInstant(now, zone).isBefore(topic.getReminderTime());
    }

    /** Marca el tema como recordado hoy (idempotencia diaria del recordatorio). */
    public void markRemindedToday(PrayerTopic topic) {
        topic.setLastRemindedOn(LocalDate.ofInstant(clock.instant(), zoneOf(topic)));
        topicRepository.save(topic);
    }

    private PrayerTopic findOwned(Long id, Long userId) {
        PrayerTopic topic = topicRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerTopic", "id", id));
        if (!topic.getOwnerUserId().equals(userId)) {
            throw new ForbiddenException("Este tema no es tuyo");
        }
        return topic;
    }

    private ZoneId zoneOf(PrayerTopic topic) {
        try {
            return ZoneId.of(topic.getTimezone());
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private TopicResponse toResponse(PrayerTopic topic) {
        return new TopicResponse(
                topic.getId(),
                topic.getName(),
                topic.isReminderEnabled(),
                topic.getReminderTime() != null ? topic.getReminderTime().toString() : null,
                topic.getCreatedAt() != null ? topic.getCreatedAt().toString() : null);
    }
}
