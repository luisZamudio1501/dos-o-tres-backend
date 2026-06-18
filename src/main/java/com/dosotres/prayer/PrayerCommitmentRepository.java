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

    /** Personas distintas que efectivamente oraron por un pedido. */
    @Query("select count(distinct c.user.id) from PrayerCommitment c "
            + "where c.prayerRequest.id = :requestId and c.fulfilled = true")
    long countDistinctUsersByPrayerRequestId(@Param("requestId") Long requestId);

    /** Personas distintas que oraron, agregado por pedido — evita N+1 en la lista. */
    @Query("select c.prayerRequest.id, count(distinct c.user.id) from PrayerCommitment c "
            + "where c.prayerRequest.id in :requestIds and c.fulfilled = true "
            + "group by c.prayerRequest.id")
    List<Object[]> countDistinctUsersGroupedByPrayerRequestIds(@Param("requestIds") List<Long> requestIds);

    /** Historial "quién oró": solo cumplimientos reales, más reciente primero. */
    List<PrayerCommitment> findByPrayerRequestIdAndFulfilledTrueOrderByFulfilledAtDesc(Long requestId);

    /** Ids de los usuarios que efectivamente oraron por un pedido (para notificar). */
    @Query("select distinct c.user.id from PrayerCommitment c "
            + "where c.prayerRequest.id = :requestId and c.fulfilled = true")
    List<Long> findDistinctUserIdsByPrayerRequestIdAndFulfilledTrue(@Param("requestId") Long requestId);

    List<PrayerCommitment> findByUserIdAndCommittedDate(Long userId, LocalDate date);

    List<PrayerCommitment> findByPrayerRequestId(Long requestId);

    Optional<PrayerCommitment> findByPrayerRequestIdAndUserIdAndCommittedDate(
            Long requestId, Long userId, LocalDate date);

    List<PrayerCommitment> findByUserIdAndPrayerRequestGroupIdAndFulfilledFalse(Long userId, Long groupId);

    void deleteByPrayerRequestId(Long requestId);
}
