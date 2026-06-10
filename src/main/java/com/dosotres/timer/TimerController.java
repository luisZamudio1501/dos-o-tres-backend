package com.dosotres.timer;

import com.dosotres.security.annotations.AuthUser;
import com.dosotres.timer.dto.SessionResponse;
import com.dosotres.timer.dto.StartSessionRequest;
import com.dosotres.timer.dto.SyncSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/timer")
public class TimerController {

    private final TimerService timerService;

    public TimerController(TimerService timerService) {
        this.timerService = timerService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse start(@Valid @RequestBody StartSessionRequest req,
                                  @AuthUser Long userId) {
        return timerService.start(req, userId);
    }

    @PutMapping("/{id}/sync")
    public SessionResponse sync(@PathVariable String id,
                                 @Valid @RequestBody SyncSessionRequest req,
                                 @AuthUser Long userId) {
        return timerService.sync(id, req, userId);
    }

    @PutMapping("/{id}/stop")
    public SessionResponse stop(@PathVariable String id,
                                 @AuthUser Long userId) {
        return timerService.stop(id, userId);
    }

    @GetMapping("/{id}")
    public SessionResponse getById(@PathVariable String id,
                                    @AuthUser Long userId) {
        return timerService.getById(id, userId);
    }

    @GetMapping("/active")
    public SessionResponse getActive(@AuthUser Long userId) {
        return timerService.getActive(userId).orElse(null);
    }
}
