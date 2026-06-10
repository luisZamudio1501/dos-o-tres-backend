package com.dosotres.timer.port;

import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PrayerSessionPort {
    PrayerSession save(PrayerSession session);
    Optional<PrayerSession> findById(String id);
    Optional<PrayerSession> findActiveByUserId(Long userId);
    List<PrayerSession> findByUserAndDateRange(Long userId, LocalDate from, LocalDate to);
    long totalSeconds(Long userId, LocalDate from, LocalDate to);
    List<PrayerSession> findAbandonedBefore(Instant threshold);
    int updateStatusBatch(List<String> ids, SessionStatus status);
}
