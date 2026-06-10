package com.dosotres.timer;

import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.port.PrayerSessionPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.timer.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class AbandonedSessionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AbandonedSessionCleanupJob.class);

    private final PrayerSessionPort sessionPort;
    private final Clock clock;
    private final int abandonThresholdMinutes;

    public AbandonedSessionCleanupJob(PrayerSessionPort sessionPort,
                                       Clock clock,
                                       @Value("${app.timer.abandon-threshold-minutes:30}") int abandonThresholdMinutes) {
        this.sessionPort = sessionPort;
        this.clock = clock;
        this.abandonThresholdMinutes = abandonThresholdMinutes;
    }

    @Scheduled(cron = "${app.timer.cleanup-cron}")
    @Transactional
    public void cleanupAbandonedSessions() {
        Instant threshold = Instant.now(clock)
                .minus(abandonThresholdMinutes, ChronoUnit.MINUTES);
        List<PrayerSession> abandoned = sessionPort.findAbandonedBefore(threshold);
        if (abandoned.isEmpty()) {
            log.info("Cleanup run: 0 sessions marked as ABANDONED");
            return;
        }
        List<String> ids = abandoned.stream().map(PrayerSession::getId).toList();
        int updated = sessionPort.updateStatusBatch(ids, SessionStatus.ABANDONED);
        log.info("Cleanup run: marked {} sessions as ABANDONED (threshold: {} min)", updated, abandonThresholdMinutes);
    }
}
