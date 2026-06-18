package com.dosotres.goal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<PrayerGoal, Long> {

    List<PrayerGoal> findByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<PrayerGoal> findByMode(GoalMode mode);
}
