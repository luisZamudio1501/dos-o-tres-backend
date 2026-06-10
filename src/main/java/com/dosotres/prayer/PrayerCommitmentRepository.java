package com.dosotres.prayer;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PrayerCommitmentRepository extends JpaRepository<PrayerCommitment, Long> {

    List<PrayerCommitment> findByPrayerRequestIdAndUserId(Long requestId, Long userId);

    List<PrayerCommitment> findByUserIdAndCommittedDate(Long userId, LocalDate date);

    List<PrayerCommitment> findByPrayerRequestId(Long requestId);

    Optional<PrayerCommitment> findByPrayerRequestIdAndUserIdAndCommittedDate(
            Long requestId, Long userId, LocalDate date);
}
