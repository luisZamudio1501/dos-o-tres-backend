package com.dosotres.prayer;

import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupRepository;
import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PrayerRequestService {

    private final PrayerRequestRepository prayerRequestRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public PrayerRequestService(PrayerRequestRepository prayerRequestRepository,
                                 GroupRepository groupRepository,
                                 UserRepository userRepository) {
        this.prayerRequestRepository = prayerRequestRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
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
