package com.dosotres.prayer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PrayerCommitmentRepository extends JpaRepository<PrayerCommitment, Long> {

    List<PrayerCommitment> findByPrayerRequestIdAndUserId(Long requestId, Long userId);

    long countByPrayerRequestId(Long requestId);

    /** Conteo agregado para páginas de pedidos — evita N+1 (fix 3.4). */
    @Query("select c.prayerRequest.id, count(c) from PrayerCommitment c "
            + "where c.prayerRequest.id in :requestIds group by c.prayerRequest.id")
    List<Object[]> countGroupedByPrayerRequestIds(@Param("requestIds") List<Long> requestIds);

    List<PrayerCommitment> findByUserIdAndCommittedDate(Long userId, LocalDate date);

    List<PrayerCommitment> findByPrayerRequestId(Long requestId);

    Optional<PrayerCommitment> findByPrayerRequestIdAndUserIdAndCommittedDate(
            Long requestId, Long userId, LocalDate date);

    List<PrayerCommitment> findByUserIdAndPrayerRequestGroupIdAndFulfilledFalse(Long userId, Long groupId);

    void deleteByPrayerRequestId(Long requestId);
}
