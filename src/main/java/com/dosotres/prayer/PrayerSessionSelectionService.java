package com.dosotres.prayer;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.port.PrayerSessionPort;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vincula pedidos seleccionados a una sesión de oración (antes de iniciarla)
 * y genera los cumplimientos granulares al completarla.
 * Reglas: B5 (selección previa bloqueada) y B7 (un cumplimiento por pedido).
 */
@Service
@Transactional
public class PrayerSessionSelectionService {

    private static final Logger log = LoggerFactory.getLogger(PrayerSessionSelectionService.class);

    private final SessionPrayerRequestRepository sessionRequestRepository;
    private final PrayerRequestRepository prayerRequestRepository;
    private final PrayerCommitmentRepository commitmentRepository;
    private final UserRepository userRepository;
    private final PrayerSessionPort sessionPort;
    private final ActivityService activityService;
    private final Clock clock;

    public PrayerSessionSelectionService(SessionPrayerRequestRepository sessionRequestRepository,
                                         PrayerRequestRepository prayerRequestRepository,
                                         PrayerCommitmentRepository commitmentRepository,
                                         UserRepository userRepository,
                                         PrayerSessionPort sessionPort,
                                         ActivityService activityService,
                                         Clock clock) {
        this.sessionRequestRepository = sessionRequestRepository;
        this.prayerRequestRepository = prayerRequestRepository;
        this.commitmentRepository = commitmentRepository;
        this.userRepository = userRepository;
        this.sessionPort = sessionPort;
        this.activityService = activityService;
        this.clock = clock;
    }

    public void attach(String sessionId, List<Long> prayerRequestIds, Long groupId, boolean isPrivate) {
        Set<Long> distinctIds = new LinkedHashSet<>(prayerRequestIds);
        for (Long requestId : distinctIds) {
            PrayerRequest pr = prayerRequestRepository.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", requestId));
            if (!pr.getGroup().getId().equals(groupId)) {
                throw new ResourceNotFoundException("PrayerRequest", "id+groupId", requestId + "+" + groupId);
            }
            if (pr.getStatus() != PrayerRequestStatus.ACTIVE) {
                throw new ValidationException("Solo se pueden seleccionar pedidos activos");
            }
            SessionPrayerRequest link = new SessionPrayerRequest();
            link.setSessionId(sessionId);
            link.setPrayerRequest(pr);
            link.setPrivate(isPrivate);
            sessionRequestRepository.save(link);
        }
    }

    public int fulfilForSession(String sessionId, Long userId) {
        List<SessionPrayerRequest> selections = sessionRequestRepository.findBySessionId(sessionId);
        if (selections.isEmpty()) {
            return 0;
        }

        PrayerSession session = sessionPort.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerSession", "id", sessionId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Instant now = Instant.now(clock);
        // Fix 3.5: "hoy" en la zona del usuario, no UTC — si no, de noche en
        // UTC-3 el cumplimiento automático no matchea el compromiso manual
        // del día y se duplica con otra fecha.
        LocalDate today = LocalDate.ofInstant(now, userZone(user));
        int fulfilled = 0;

        for (SessionPrayerRequest selection : selections) {
            PrayerRequest pr = selection.getPrayerRequest();

            PrayerCommitment commitment = commitmentRepository
                    .findByPrayerRequestIdAndUserIdAndCommittedDate(pr.getId(), userId, today)
                    .orElseGet(() -> {
                        PrayerCommitment c = new PrayerCommitment();
                        c.setPrayerRequest(pr);
                        c.setUser(user);
                        c.setCommittedDate(today);
                        return c;
                    });

            if (commitment.isFulfilled()) {
                continue;
            }

            commitment.setFulfilled(true);
            commitment.setFulfilledAt(now);
            commitment.setPrivate(selection.isPrivate());
            commitment.setSession(session);
            commitmentRepository.save(commitment);

            activityService.record(pr.getGroup(), user,
                    ActivityEventType.COMMITMENT_FULFILLED, selection.isPrivate(),
                    Map.of("prayerRequestId", pr.getId(),
                            "prayerTitle", pr.getTitle()));
            fulfilled++;
        }

        log.info("Session fulfilment: sessionId={}, userId={}, fulfilled={}", sessionId, userId, fulfilled);
        return fulfilled;
    }

    private ZoneId userZone(User user) {
        try {
            return user.getTimezone() != null ? ZoneId.of(user.getTimezone()) : ZoneOffset.UTC;
        } catch (DateTimeException e) {
            log.warn("Invalid timezone '{}' for userId={}, falling back to UTC",
                    user.getTimezone(), user.getId());
            return ZoneOffset.UTC;
        }
    }
}
