package com.dosotres.activity;

import com.dosotres.activity.dto.ActivityEventResponse;
import com.dosotres.group.Group;
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
    private final ObjectMapper objectMapper;

    public ActivityService(ActivityEventRepository activityEventRepository,
                           ObjectMapper objectMapper) {
        this.activityEventRepository = activityEventRepository;
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
