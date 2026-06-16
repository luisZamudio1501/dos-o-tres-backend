package com.dosotres.timer;

import com.dosotres.prayer.PrayerSessionSelectionService;
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
    private final PrayerSessionSelectionService selectionService;

    public TimerController(TimerService timerService,
                           PrayerSessionSelectionService selectionService) {
        this.timerService = timerService;
        this.selectionService = selectionService;
    }

    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse start(@Valid @RequestBody StartSessionRequest req,
                                  @AuthUser Long userId) {
        // La sesión es del usuario (sin grupo fijo): el acceso a cada pedido
        // seleccionado se valida por-pedido en attach (sesión unificada cross-group).
        SessionResponse response = timerService.start(req, null, userId);
        if (req.prayerRequestIds() != null && !req.prayerRequestIds().isEmpty()) {
            selectionService.attach(req.id(), req.prayerRequestIds(), userId,
                    Boolean.TRUE.equals(req.isPrivate()));
        }
        return response;
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
        SessionResponse response = timerService.stop(id, userId);
        selectionService.fulfilForSession(id, userId);
        return response;
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
