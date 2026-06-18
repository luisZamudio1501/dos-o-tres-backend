package com.dosotres.goal;

import com.dosotres.goal.dto.CreateGoalRequest;
import com.dosotres.goal.dto.GoalResponse;
import com.dosotres.goal.dto.ReminderStatusResponse;
import com.dosotres.goal.dto.UpdateGoalRequest;
import com.dosotres.security.annotations.AuthUser;
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

/**
 * Metas de oración del usuario. Vive bajo /api/me/** → excluido de X-Group-Id
 * (metas personales; las grupales llegan en una etapa posterior).
 */
@RestController
@RequestMapping("/api/me/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public List<GoalResponse> list(@AuthUser Long userId) {
        return goalService.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalResponse create(@AuthUser Long userId, @Valid @RequestBody CreateGoalRequest req) {
        return goalService.create(userId, req);
    }

    @GetMapping("/{id}")
    public GoalResponse get(@PathVariable Long id, @AuthUser Long userId) {
        return goalService.get(id, userId);
    }

    @PatchMapping("/{id}")
    public GoalResponse update(@PathVariable Long id, @AuthUser Long userId,
                               @Valid @RequestBody UpdateGoalRequest req) {
        return goalService.update(id, userId, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthUser Long userId) {
        goalService.delete(id, userId);
    }

    @GetMapping("/{id}/reminder-status")
    public ReminderStatusResponse reminderStatus(@PathVariable Long id, @AuthUser Long userId) {
        return goalService.reminderStatus(id, userId);
    }
}
