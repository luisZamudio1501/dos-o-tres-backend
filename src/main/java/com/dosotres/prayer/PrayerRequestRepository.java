package com.dosotres.prayer;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrayerRequestRepository extends JpaRepository<PrayerRequest, Long> {

    Page<PrayerRequest> findByGroupIdAndStatus(Long groupId, PrayerRequestStatus status, Pageable pageable);

    Page<PrayerRequest> findByGroupId(Long groupId, Pageable pageable);

    long countByGroupIdAndStatus(Long groupId, PrayerRequestStatus status);

    List<PrayerRequest> findByAuthorIdAndGroupIdAndStatus(Long authorId, Long groupId, PrayerRequestStatus status);

    // Espacio personal: pedidos privados del usuario.
    Page<PrayerRequest> findByAuthorIdAndVisibility(Long authorId, PrayerVisibility visibility, Pageable pageable);

    List<PrayerRequest> findByAuthorIdAndVisibilityAndStatusNot(
            Long authorId, PrayerVisibility visibility, PrayerRequestStatus status);

    // Sesión unificada: pedidos orables de un conjunto de grupos.
    List<PrayerRequest> findByGroupIdInAndStatusIn(
            Collection<Long> groupIds, Collection<PrayerRequestStatus> statuses);
}
