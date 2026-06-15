package com.dosotres.prayer;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PrayerRequestService {

    private final PrayerRequestRepository prayerRequestRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final PrayerCommitmentRepository commitmentRepository;
    private final ActivityService activityService;
    private final Clock clock;

    public PrayerRequestService(PrayerRequestRepository prayerRequestRepository,
                                 GroupRepository groupRepository,
                                 GroupMemberRepository groupMemberRepository,
                                 UserRepository userRepository,
                                 PrayerCommitmentRepository commitmentRepository,
                                 ActivityService activityService,
                                 Clock clock) {
        this.prayerRequestRepository = prayerRequestRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.commitmentRepository = commitmentRepository;
        this.activityService = activityService;
        this.clock = clock;
    }

    public PrayerRequestResponse create(CreatePrayerRequest req, Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group", "id", groupId));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        PrayerRequest pr = new PrayerRequest();
        pr.setGroup(group);
        pr.setAuthor(user);
        pr.setTitle(req.title());
        pr.setDescription(req.description());
        pr.setStatus(PrayerRequestStatus.ACTIVE);
        prayerRequestRepository.save(pr);

        activityService.record(group, user, ActivityEventType.REQUEST_CREATED, false,
                Map.of("prayerRequestId", pr.getId(), "prayerTitle", pr.getTitle()));

        return toResponse(pr, 0); // recién creado: sin compromisos todavía
    }

    @Transactional(readOnly = true)
    public Page<PrayerRequestResponse> listByGroup(Long groupId, PrayerRequestStatus status, Pageable pageable) {
        Page<PrayerRequest> page;
        if (status != null) {
            page = prayerRequestRepository.findByGroupIdAndStatus(groupId, status, pageable);
        } else {
            page = prayerRequestRepository.findByGroupId(groupId, pageable);
        }

        // Conteo de compromisos en UNA query agregada para toda la página (fix 3.4).
        List<Long> ids = page.getContent().stream().map(PrayerRequest::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : commitmentRepository.countGroupedByPrayerRequestIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return page.map(pr -> toResponse(pr, counts.getOrDefault(pr.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public PrayerRequestResponse getById(Long id, Long groupId) {
        PrayerRequest pr = findInGroup(id, groupId);
        return toResponse(pr, (int) commitmentRepository.countByPrayerRequestId(pr.getId()));
    }

    public PrayerRequestResponse markAsAnswered(Long id, Long groupId, Long userId) {
        return changeStatus(id, groupId, userId, PrayerRequestStatus.ANSWERED, null);
    }

    public PrayerRequestResponse changeStatus(Long id, Long groupId, Long userId,
                                              PrayerRequestStatus newStatus, String testimony) {
        PrayerRequest pr = findInGroup(id, groupId);

        if (pr.getStatus() == newStatus) {
            String label = newStatus == PrayerRequestStatus.ANSWERED ? "answered" : newStatus.name();
            throw new ConflictException("Prayer request is already " + label);
        }

        boolean isAuthor = pr.getAuthor().getId().equals(userId);
        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);

        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("Only the author or a group admin can change the status");
        }

        boolean hasTestimony = testimony != null && !testimony.isBlank();
        if (hasTestimony) {
            if (newStatus != PrayerRequestStatus.ANSWERED) {
                throw new ValidationException("El testimonio solo se puede escribir al marcar el pedido como respondido");
            }
            if (!isAuthor) {
                throw new ForbiddenException("Only the author can write a testimony");
            }
        }

        pr.setStatus(newStatus);
        if (newStatus == PrayerRequestStatus.ANSWERED) {
            pr.setAnsweredAt(Instant.now(clock));
            pr.setTestimony(hasTestimony ? testimony.trim() : null);
        } else {
            pr.setAnsweredAt(null);
            pr.setTestimony(null);
        }
        prayerRequestRepository.save(pr);

        User actor = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        activityService.record(pr.getGroup(), actor, eventTypeFor(newStatus), false,
                Map.of("prayerRequestId", pr.getId(),
                        "prayerTitle", pr.getTitle(),
                        "hasTestimony", hasTestimony));

        return toResponse(pr, (int) commitmentRepository.countByPrayerRequestId(pr.getId()));
    }

    private ActivityEventType eventTypeFor(PrayerRequestStatus status) {
        return switch (status) {
            case ANSWERED -> ActivityEventType.REQUEST_ANSWERED;
            case ON_HOLD -> ActivityEventType.REQUEST_ON_HOLD;
            case ACTIVE -> ActivityEventType.REQUEST_REACTIVATED;
        };
    }

    private PrayerRequest findInGroup(Long id, Long groupId) {
        PrayerRequest pr = prayerRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", id));
        if (!pr.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerRequest", "id+groupId", id + "+" + groupId);
        }
        return pr;
    }

    private PrayerRequestResponse toResponse(PrayerRequest pr, int commitmentCount) {
        return new PrayerRequestResponse(
                pr.getId(),
                pr.getAuthor().getId(),
                pr.getAuthor().getDisplayName(),
                pr.getTitle(),
                pr.getDescription(),
                pr.getStatus().name(),
                pr.getAnsweredAt() != null ? pr.getAnsweredAt().toString() : null,
                pr.getTestimony(),
                pr.getCreatedAt() != null ? pr.getCreatedAt().toString() : null,
                commitmentCount
        );
    }
}
