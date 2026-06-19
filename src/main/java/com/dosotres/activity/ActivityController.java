package com.dosotres.activity;

import com.dosotres.activity.dto.ActivityEventResponse;
import com.dosotres.security.annotations.AuthUser;
import com.dosotres.security.annotations.CurrentGroupId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public Page<ActivityEventResponse> feed(@CurrentGroupId Long groupId,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return activityService.feed(groupId, pageable);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @CurrentGroupId Long groupId,
                       @AuthUser Long userId) {
        activityService.delete(id, groupId, userId);
    }
}
