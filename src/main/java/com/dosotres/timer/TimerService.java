package com.dosotres.timer;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.dto.SessionResponse;
import com.dosotres.timer.dto.StartSessionRequest;
import com.dosotres.timer.dto.SyncSessionRequest;
import com.dosotres.timer.port.PrayerSessionPort;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.dosotres.group.Group;
import com.dosotres.group.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@Transactional
public class TimerService {

    private static final Logger log = LoggerFactory.getLogger(TimerService.class);

    private final PrayerSessionPort sessionPort;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final Clock clock;

    public TimerService(PrayerSessionPort sessionPort,
                        UserRepository userRepository,
                        GroupRepository groupRepository,
                        Clock clock) {
        this.sessionPort = sessionPort;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.clock = clock;
    }

    public SessionResponse start(StartSessionRequest req, Long userId) {
        sessionPort.findActiveByUserId(userId).ifPresent(s -> {
            throw new ConflictException("User already has an active prayer session");
        });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Instant now = Instant.now(clock);

        PrayerSession session = new PrayerSession();
        session.setId(req.id());
        session.setUser(user);
        session.setStartedAt(now);
        session.setDurationSeconds(0);
        session.setStatus(SessionStatus.ACTIVE);
        session.setLastSyncAt(now);

        if (req.groupId() != null) {
            Group group = groupRepository.findById(req.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group", "id", req.groupId()));
            session.setGroup(group);
        }

        PrayerSession saved = sessionPort.save(session);
        log.info("Prayer session started: userId={}, sessionId={}", userId, saved.getId());
        return toResponse(saved);
    }

    public SessionResponse sync(String sessionId, SyncSessionRequest req, Long userId) {
        PrayerSession session = findAndValidateOwnership(sessionId, userId);
        validateActive(session);

        session.setDurationSeconds(req.durationSeconds());
        session.setLastSyncAt(Instant.now(clock));

        PrayerSession saved = sessionPort.save(session);
        return toResponse(saved);
    }

    public SessionResponse stop(String sessionId, Long userId) {
        PrayerSession session = findAndValidateOwnership(sessionId, userId);
        validateActive(session);

        session.setStatus(SessionStatus.COMPLETED);
        session.setLastSyncAt(Instant.now(clock));

        PrayerSession saved = sessionPort.save(session);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SessionResponse getById(String sessionId, Long userId) {
        PrayerSession session = findAndValidateOwnership(sessionId, userId);
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public Optional<SessionResponse> getActive(Long userId) {
        return sessionPort.findActiveByUserId(userId).map(this::toResponse);
    }

    private PrayerSession findAndValidateOwnership(String sessionId, Long userId) {
        PrayerSession session = sessionPort.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerSession", "id", sessionId));
        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Prayer session does not belong to the user");
        }
        return session;
    }

    private void validateActive(PrayerSession session) {
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ConflictException("Prayer session is not active");
        }
    }

    private SessionResponse toResponse(PrayerSession session) {
        return new SessionResponse(
                session.getId(),
                session.getUser().getId(),
                session.getGroup() != null ? session.getGroup().getId() : null,
                session.getStartedAt().toString(),
                session.getDurationSeconds(),
                session.getStatus().name(),
                session.getLastSyncAt().toString()
        );
    }
}
