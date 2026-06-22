package com.dosotres.topic;

import com.dosotres.security.annotations.AuthUser;
import com.dosotres.topic.dto.CreateTopicRequest;
import com.dosotres.topic.dto.TopicResponse;
import com.dosotres.topic.dto.UpdateTopicRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Temas de oración personales. Bajo /me/** → eximido del GroupContextFilter. */
@RestController
@RequestMapping("/api/me/topics")
public class PrayerTopicController {

    private final PrayerTopicService topicService;

    public PrayerTopicController(PrayerTopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public List<TopicResponse> list(@AuthUser Long userId) {
        return topicService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TopicResponse create(@AuthUser Long userId, @Valid @RequestBody CreateTopicRequest req) {
        return topicService.create(userId, req);
    }

    @PatchMapping("/{id}")
    public TopicResponse update(@AuthUser Long userId, @PathVariable Long id,
                                @Valid @RequestBody UpdateTopicRequest req) {
        return topicService.update(id, userId, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthUser Long userId, @PathVariable Long id) {
        topicService.delete(id, userId);
    }
}
