package com.dosotres.activity;

import com.dosotres.activity.dto.ActivityEventResponse;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityService {

    private final ActivityEventRepository activityEventRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ObjectMapper objectMapper;

    public ActivityService(ActivityEventRepository activityEventRepository,
                           GroupMemberRepository groupMemberRepository,
                           ObjectMapper objectMapper) {
        this.activityEventRepository = activityEventRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.objectMapper = objectMapper;
    }

    public void record(Group group, User actor, ActivityEventType type,
                       boolean isPrivate, Map<String, Object> payload) {
        ActivityEvent event = new ActivityEvent();
        event.setGroup(group);
        event.setActor(actor);
        event.setType(type);
        event.setPrivate(isPrivate);
        event.setPayload(serialize(payload));
        activityEventRepository.save(event);
    }

    public void delete(Long eventId, Long groupId, Long userId) {
        ActivityEvent event = activityEventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("ActivityEvent", "id", eventId));
        if (!event.getGroup().getId().equals(groupId)) {
            throw new ResourceNotFoundException("ActivityEvent", "id+groupId", eventId + "+" + groupId);
        }
        boolean isAuthor = event.getActor().getId().equals(userId);
        boolean isAdmin = groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .map(m -> m.getRole() == GroupRole.ADMIN)
                .orElse(false);
        if (!isAuthor && !isAdmin) {
            throw new ForbiddenException("Only the author or a group admin can delete this activity event");
        }
        activityEventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public Page<ActivityEventResponse> feed(Long groupId, Pageable pageable) {
        return activityEventRepository
                .findByGroupIdOrderByCreatedAtDesc(groupId, pageable)
                .map(this::toResponse);
    }

    private ActivityEventResponse toResponse(ActivityEvent event) {
        boolean masked = event.isPrivate();
        return new ActivityEventResponse(
                event.getId(),
                event.getType().name(),
                masked ? null : event.getActor().getId(),
                masked ? null : event.getActor().getDisplayName(),
                // País del perfil (S5): también se enmascara en eventos privados.
                masked ? null : event.getActor().getCountry(),
                deserialize(event.getPayload()),
                event.getCreatedAt() != null ? event.getCreatedAt().toString() : null
        );
    }

    private String serialize(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize activity payload", e);
        }
    }

    private Map<String, Object> deserialize(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
