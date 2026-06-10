package com.dosotres.timer.adapter;

import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface JpaPrayerSessionRepository extends JpaRepository<PrayerSession, String> {

    Optional<PrayerSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    List<PrayerSession> findByUserIdAndStartedAtBetween(Long userId, Instant from, Instant to);

    @Query("SELECT COALESCE(SUM(ps.durationSeconds), 0) FROM PrayerSession ps " +
           "WHERE ps.user.id = :userId AND ps.startedAt BETWEEN :from AND :to " +
           "AND ps.status = 'COMPLETED'")
    long sumDurationByUserAndRange(@Param("userId") Long userId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    List<PrayerSession> findByStatusAndLastSyncAtBefore(SessionStatus status, Instant threshold);

    @Modifying
    @Query("UPDATE PrayerSession ps SET ps.status = :status WHERE ps.id IN :ids")
    int updateStatusBatch(@Param("ids") List<String> ids, @Param("status") SessionStatus status);
}
