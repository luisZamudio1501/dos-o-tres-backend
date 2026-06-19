package com.dosotres.publicwall;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicPrayerRequestRepository extends JpaRepository<PublicPrayerRequest, Long> {

    /** Feed público: solo lo visible, más reciente primero. */
    Page<PublicPrayerRequest> findByModerationStatusOrderByCreatedAtDesc(
            ModerationStatus moderationStatus, Pageable pageable);
}
