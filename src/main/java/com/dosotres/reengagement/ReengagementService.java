package com.dosotres.reengagement;

import com.dosotres.prayer.PrayerCommitmentRepository;
import com.dosotres.prayer.PrayerRequestStatus;
import com.dosotres.push.PushNotificationService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agenda viva de oración: si un usuario comprometió pedidos ACTIVE/ON_HOLD y no
 * volvió a orar por ellos en N días, se lo recuerda con un push. Personal y
 * best-effort: un error con un usuario no frena al resto; nunca cruza datos
 * entre usuarios.
 */
@Service
public class ReengagementService {

    private static final Logger log = LoggerFactory.getLogger(ReengagementService.class);

    private final PrayerCommitmentRepository commitmentRepository;
    private final UserRepository userRepository;
    private final PushNotificationService pushService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int staleDays;

    public ReengagementService(PrayerCommitmentRepository commitmentRepository,
                               UserRepository userRepository,
                               PushNotificationService pushService,
                               ObjectMapper objectMapper,
                               Clock clock,
                               @Value("${app.reengagement.stale-days:3}") int staleDays) {
        this.commitmentRepository = commitmentRepository;
        this.userRepository = userRepository;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.staleDays = staleDays;
    }

    @Transactional
    public void sendDueReengagements() {
        Instant cutoff = clock.instant().minus(staleDays, ChronoUnit.DAYS);
        List<Object[]> rows = commitmentRepository.findStaleRequestCountsByUser(
                List.of(PrayerRequestStatus.ACTIVE, PrayerRequestStatus.ON_HOLD), cutoff);
        if (rows.isEmpty()) {
            return;
        }

        Map<Long, Long> staleCounts = new HashMap<>();
        for (Object[] row : rows) {
            staleCounts.put((Long) row[0], (Long) row[1]);
        }

        for (User user : userRepository.findAllById(staleCounts.keySet())) {
            try {
                LocalDate today = LocalDate.ofInstant(clock.instant(), zoneOf(user));
                if (today.equals(user.getLastReengagedOn())) {
                    continue; // ya se le avisó hoy (zona del usuario)
                }

                long staleCount = staleCounts.get(user.getId());
                pushService.sendToUsers(List.of(user.getId()), payload(staleCount));
                user.setLastReengagedOn(today);
                userRepository.save(user);
                log.info("Reengagement push sent userId={} staleCount={}", user.getId(), staleCount);
            } catch (Exception e) {
                log.warn("Reengagement failed userId={}: {}", user.getId(), e.getMessage());
            }
        }
    }

    private ZoneId zoneOf(User user) {
        try {
            return user.getTimezone() != null ? ZoneId.of(user.getTimezone()) : ZoneOffset.UTC;
        } catch (Exception e) {
            return ZoneOffset.UTC;
        }
    }

    private String payload(long staleCount) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", "🙏 Pedidos esperando tu oración",
                "body", "Tenés " + staleCount + (staleCount == 1 ? " pedido" : " pedidos")
                        + " esperando tu oración",
                "url", "/prayer-history"));
    }
}
