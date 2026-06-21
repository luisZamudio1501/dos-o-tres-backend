package com.dosotres.publicwall;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicPrayerRequestRepository extends JpaRepository<PublicPrayerRequest, Long> {

    /** Feed activo: solo visible, activo y no archivado, más reciente primero. */
    Page<PublicPrayerRequest> findByModerationStatusAndStatusAndArchivedAtIsNullOrderByCreatedAtDesc(
            ModerationStatus moderationStatus, PublicRequestStatus status, Pageable pageable);

    /** Testimonios públicos permanentes: respondidos con testimonio, más reciente primero. */
    @Query("""
            SELECT r FROM PublicPrayerRequest r
            WHERE r.moderationStatus = :moderationStatus
              AND r.status = com.dosotres.publicwall.PublicRequestStatus.ANSWERED
              AND r.testimony IS NOT NULL
            ORDER BY r.answeredAt DESC
            """)
    Page<PublicPrayerRequest> findTestimonies(
            @Param("moderationStatus") ModerationStatus moderationStatus, Pageable pageable);

    /** Archiva por inactividad: pedidos activos, no archivados, sin actividad desde el umbral. */
    @Modifying
    @Query("""
            UPDATE PublicPrayerRequest r
            SET r.archivedAt = :now
            WHERE r.status = com.dosotres.publicwall.PublicRequestStatus.ACTIVE
              AND r.archivedAt IS NULL
              AND r.lastActivityAt < :threshold
            """)
    int archiveStaleActive(@Param("now") Instant now, @Param("threshold") Instant threshold);
}
