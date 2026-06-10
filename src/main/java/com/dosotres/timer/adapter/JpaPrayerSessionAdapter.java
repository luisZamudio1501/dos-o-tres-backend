package com.dosotres.timer.adapter;

import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.port.PrayerSessionPort;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
public class JpaPrayerSessionAdapter implements PrayerSessionPort {

    private final JpaPrayerSessionRepository repo;
    private final Clock clock;

    public JpaPrayerSessionAdapter(JpaPrayerSessionRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    public PrayerSession save(PrayerSession session) {
        return repo.save(session);
    }

    @Override
    public Optional<PrayerSession> findById(String id) {
        return repo.findById(id);
    }

    @Override
    public Optional<PrayerSession> findActiveByUserId(Long userId) {
        return repo.findByUserIdAndStatus(userId, SessionStatus.ACTIVE);
    }

    @Override
    public List<PrayerSession> findByUserAndDateRange(Long userId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(clock.getZone()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return repo.findByUserIdAndStartedAtBetween(userId, fromInstant, toInstant);
    }

    @Override
    public long totalSeconds(Long userId, LocalDate from, LocalDate to) {
        Instant fromInstant = from.atStartOfDay(clock.getZone()).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return repo.sumDurationByUserAndRange(userId, fromInstant, toInstant);
    }

    @Override
    public List<PrayerSession> findAbandonedBefore(Instant threshold) {
        return repo.findByStatusAndLastSyncAtBefore(SessionStatus.ACTIVE, threshold);
    }

    @Override
    public int updateStatusBatch(List<String> ids, SessionStatus status) {
        return repo.updateStatusBatch(ids, status);
    }
}
