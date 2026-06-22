package com.dosotres.topic;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerTopicRepository extends JpaRepository<PrayerTopic, Long> {

    /** Temas del usuario en orden de creación (catálogo en orden de siembra). */
    List<PrayerTopic> findByOwnerUserIdOrderByCreatedAtAsc(Long ownerUserId);

    /** Barrido del job de recordatorios. */
    List<PrayerTopic> findByReminderEnabledTrue();
}
