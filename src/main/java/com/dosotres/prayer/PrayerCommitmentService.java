package com.dosotres.prayer;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.prayer.dto.CommitmentResponse;
import com.dosotres.prayer.dto.CreateCommitmentRequest;
import com.dosotres.prayer.dto.FulfilCommitmentRequest;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.port.PrayerSessionPort;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class PrayerCommitmentService {

    private final PrayerCommitmentRepository commitmentRepository;
    private final PrayerRequestRepository prayerRequestRepository;
    private final UserRepository userRepository;
    private final PrayerSessionPort sessionPort;
    private final ActivityService activityService;
    private final Clock clock;

    public PrayerCommitmentService(PrayerCommitmentRepository commitmentRepository,
                                    PrayerRequestRepository prayerRequestRepository,
                                    UserRepository userRepository,
                                    PrayerSessionPort sessionPort,
                                    ActivityService activityService,
                                    Clock clock) {
        this.commitmentRepository = commitmentRepository;
        this.prayerRequestRepository = prayerRequestRepository;
        this.userRepository = userRepository;
        this.sessionPort = sessionPort;
        this.activityService = activityService;
        this.clock = clock;
    }

    public CommitmentResponse create(CreateCommitmentRequest req, Long groupId, Long userId) {
        PrayerRequest prayerRequest = prayerRequestRepository.findById(req.prayerRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", req.prayerRequestId()));

        if (!prayerRequest.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerRequest", "id+groupId", req.prayerRequestId() + "+" + groupId);
        }

        // Se puede asumir un compromiso sobre pedidos nuevos (ACTIVE) o en espera
        // (ON_HOLD), nunca sobre uno ya respondido.
        if (prayerRequest.getStatus() == PrayerRequestStatus.ANSWERED) {
            throw new ValidationException("No se puede asumir un compromiso sobre un pedido ya respondido");
        }

        // Fix 3.6: fecha malformada era 500; ahora 400 con mensaje claro.
        LocalDate date;
        try {
            date = LocalDate.parse(req.committedDate());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Fecha inválida: " + req.committedDate());
        }

        commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(
                req.prayerRequestId(), userId, date
        ).ifPresent(c -> {
            throw new ConflictException("Commitment already exists for this prayer request, user and date");
        });

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PrayerCommitment commitment = new PrayerCommitment();
        commitment.setPrayerRequest(prayerRequest);
        commitment.setUser(user);
        commitment.setCommittedDate(date);
        commitment.setFulfilled(false);

        commitmentRepository.save(commitment);
        return toResponse(commitment);
    }

    @Transactional(readOnly = true)
    public List<CommitmentResponse> listByDate(Long userId, Long groupId, LocalDate date) {
        return commitmentRepository.findByUserIdAndCommittedDate(userId, date).stream()
                .filter(c -> c.getPrayerRequest().getGroup().getId().equals(groupId))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommitmentResponse> listByRequest(Long prayerRequestId, Long groupId) {
        PrayerRequest pr = prayerRequestRepository.findById(prayerRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", prayerRequestId));

        if (!pr.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerRequest", "id+groupId", prayerRequestId + "+" + groupId);
        }

        return commitmentRepository.findByPrayerRequestId(prayerRequestId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CommitmentResponse fulfil(Long commitmentId, FulfilCommitmentRequest req, Long userId) {
        PrayerCommitment commitment = commitmentRepository.findById(commitmentId)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerCommitment", "id", commitmentId));

        if (!commitment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Commitment does not belong to the user");
        }

        if (commitment.isFulfilled()) {
            throw new ConflictException("Commitment is already fulfilled");
        }

        PrayerSession session = sessionPort.findById(req.sessionId())
                .orElseThrow(() -> new ResourceNotFoundException("PrayerSession", "id", req.sessionId()));

        if (!session.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Prayer session does not belong to the user");
        }

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new ValidationException("Necesitás tener el cronómetro activo para marcar el cumplimiento");
        }

        boolean isPrivate = Boolean.TRUE.equals(req.isPrivate());

        commitment.setFulfilled(true);
        commitment.setFulfilledAt(Instant.now(clock));
        commitment.setPrivate(isPrivate);
        commitment.setSession(session);

        commitmentRepository.save(commitment);

        PrayerRequest prayerRequest = commitment.getPrayerRequest();
        activityService.record(prayerRequest.getGroup(), commitment.getUser(),
                ActivityEventType.COMMITMENT_FULFILLED, isPrivate,
                Map.of("prayerRequestId", prayerRequest.getId(),
                        "prayerTitle", prayerRequest.getTitle()));

        return toResponse(commitment);
    }

    private CommitmentResponse toResponse(PrayerCommitment c) {
        return new CommitmentResponse(
                c.getId(),
                c.getPrayerRequest().getId(),
                c.getPrayerRequest().getTitle(),
                c.getUser().getId(),
                c.getUser().getDisplayName(),
                c.getCommittedDate().toString(),
                c.isFulfilled(),
                c.getFulfilledAt() != null ? c.getFulfilledAt().toString() : null,
                c.getSession() != null ? c.getSession().getId() : null
        );
    }
}
