package com.dosotres.prayer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
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

    /**
     * Cuántas veces oró el usuario actual por cada pedido + última vez, agregado
     * por pedido — evita N+1 al pintar listados (F.2).
     */
    @Query("select c.prayerRequest.id, count(c), max(c.fulfilledAt) from PrayerCommitment c "
            + "where c.user.id = :userId and c.prayerRequest.id in :requestIds and c.fulfilled = true "
            + "group by c.prayerRequest.id")
    List<Object[]> findMyPrayerStatsGroupedByPrayerRequestIds(
            @Param("userId") Long userId, @Param("requestIds") List<Long> requestIds);

    /** Agenda de oración del usuario: todo pedido por el que oró, más reciente primero. */
    @Query(value = "select c.prayerRequest as request, count(c) as cnt, max(c.fulfilledAt) as lastPrayedAt "
            + "from PrayerCommitment c where c.user.id = :userId and c.fulfilled = true "
            + "group by c.prayerRequest order by max(c.fulfilledAt) desc",
            countQuery = "select count(distinct c.prayerRequest) from PrayerCommitment c "
                    + "where c.user.id = :userId and c.fulfilled = true")
    Page<PrayerHistoryRow> findPrayerHistoryByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * Para el re-engagement (F.3): por usuario, cuántos pedidos ACTIVE/ON_HOLD
     * comprometió y no oró (fulfilled=true) en los últimos N días. Solo aparecen
     * usuarios con al menos un pedido en espera.
     */
    @Query("select c.user.id, count(distinct c.prayerRequest.id) from PrayerCommitment c "
            + "where c.prayerRequest.status in :statuses "
            + "and not exists ("
            + "  select 1 from PrayerCommitment c2 "
            + "  where c2.user = c.user and c2.prayerRequest = c.prayerRequest "
            + "  and c2.fulfilled = true and c2.fulfilledAt >= :cutoff"
            + ") "
            + "group by c.user.id")
    List<Object[]> findStaleRequestCountsByUser(
            @Param("statuses") List<PrayerRequestStatus> statuses, @Param("cutoff") Instant cutoff);
}
