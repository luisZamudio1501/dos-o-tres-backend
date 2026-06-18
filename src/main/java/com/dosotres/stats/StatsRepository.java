package com.dosotres.stats;

import com.dosotres.prayer.PrayerRequestStatus;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-model de estadísticas personales. Lectura analítica desacoplada del
 * Port del módulo timer (ADR-002): aquí no hay lifecycle de sesión, solo
 * agregación on-the-fly sobre datos que ya existen. Sin tablas nuevas.
 */
public interface StatsRepository extends Repository<PrayerSession, String> {

    /**
     * Todas las sesiones COMPLETED del usuario (cross-group). Solo se leen
     * startedAt + durationSeconds; las relaciones lazy no se tocan (sin N+1).
     * El volumen por usuario es bajo (≈ una sesión por día), así que la racha,
     * el heatmap y los minutos se agregan en Java respetando la zona horaria.
     */
    List<PrayerSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    /**
     * Pedidos distintos por los que el usuario oró (adjuntados a sus sesiones),
     * con su estado. Una sola query con subselect sobre las sesiones del usuario
     * para evitar N+1. El estado permite contar cuántos terminaron ANSWERED.
     */
    @Query("SELECT DISTINCT pr.id AS requestId, pr.status AS status "
            + "FROM SessionPrayerRequest spr JOIN spr.prayerRequest pr "
            + "WHERE spr.sessionId IN "
            + "(SELECT ps.id FROM PrayerSession ps WHERE ps.user.id = :userId)")
    List<PrayedRequestView> findPrayedRequestsByUser(@Param("userId") Long userId);

    /** Proyección liviana (id + estado) de un pedido por el que se oró. */
    interface PrayedRequestView {
        Long getRequestId();

        PrayerRequestStatus getStatus();
    }

    // ── Stats de grupo (requieren X-Group-Id; validado en GroupContextFilter) ──
    // El filtro `ps.group.id = :groupId` excluye por construcción las sesiones
    // personales (group_id NULL) y las de otros grupos: invariante de privacidad.

    @Query("SELECT COALESCE(SUM(ps.durationSeconds), 0) FROM PrayerSession ps "
            + "WHERE ps.group.id = :groupId AND ps.status = 'COMPLETED'")
    long sumGroupSeconds(@Param("groupId") Long groupId);

    @Query("SELECT COUNT(pr) FROM PrayerRequest pr "
            + "WHERE pr.group.id = :groupId AND pr.status = 'ANSWERED'")
    long countAnsweredByGroup(@Param("groupId") Long groupId);

    @Query("SELECT COUNT(DISTINCT ps.user.id) FROM PrayerSession ps "
            + "WHERE ps.group.id = :groupId AND ps.status = 'COMPLETED' "
            + "AND ps.startedAt >= :since")
    long countActiveMembersSince(@Param("groupId") Long groupId, @Param("since") Instant since);
}
