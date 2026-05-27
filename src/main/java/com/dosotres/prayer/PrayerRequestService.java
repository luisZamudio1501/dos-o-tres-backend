package com.dosotres.prayer;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
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
    private final Clock clock;

    public PrayerRequestService(PrayerRequestRepository prayerRequestRepository,
                                 GroupRepository groupRepository,
                                 GroupMemberRepository groupMemberRepository,
                                 UserRepository userRepository,
                                 Clock clock) {
        this.prayerRequestRepository = prayerRequestRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
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
        pr.setStatus(PrayerRequestStatus.PENDING);
        prayerRequestRepository.save(pr);

        return toResponse(pr);
    }

    @Transactional(readOnly = true)
    public Page<PrayerRequestResponse> listByGroup(Long groupId, PrayerRequestStatus status, Pageable pageable) {
        Page<PrayerRequest> page;
        if (status != null) {
            page = prayerRequestRepository.findByGroupIdAndStatus(groupId, status, pageable);
        } else {
            page = prayerRequestRepository.findByGroupId(groupId, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PrayerRequestResponse getById(Long id, Long groupId) {
        PrayerRequest pr = prayerRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", id));
        if (!pr.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerRequest", "id+groupId", id + "+" + groupId);
        }
        return toResponse(pr);
    }

    public PrayerRequestResponse markAsAnswered(Long id, Long groupId, Long userId) {
        PrayerRequest pr = prayerRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PrayerRequest", "id", id));
        if (!pr.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("PrayerRequest", "id+groupId", id + "+" + groupId);
        }
        if (pr.getStatus() == PrayerRequestStatus.ANSWERED) {
            throw new ConflictException("Prayer request is already answered");
        }

        boolean isAuthor = pr.getAuthor().getId().equals(userId);
        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);

        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("Only the author or a group admin can mark a prayer request as answered");
        }

        pr.setStatus(PrayerRequestStatus.ANSWERED);
        pr.setAnsweredAt(Instant.now(clock));
        prayerRequestRepository.save(pr);

        return toResponse(pr);
    }

    private PrayerRequestResponse toResponse(PrayerRequest pr) {
        return new PrayerRequestResponse(
                pr.getId(),
                pr.getAuthor().getId(),
                pr.getAuthor().getDisplayName(),
                pr.getTitle(),
                pr.getDescription(),
                pr.getStatus().name(),
                pr.getAnsweredAt() != null ? pr.getAnsweredAt().toString() : null,
                pr.getCreatedAt() != null ? pr.getCreatedAt().toString() : null,
                0
        );
    }
}
