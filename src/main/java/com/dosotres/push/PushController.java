package com.dosotres.push;

import com.dosotres.push.dto.SubscribeRequest;
import com.dosotres.push.dto.VapidKeyResponse;
import com.dosotres.security.annotations.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Suscripciones Web Push del usuario. Vive bajo /api/push/** → excluido de
 * X-Group-Id en GroupContextFilter (son del usuario, no dependen de grupo).
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushNotificationService pushService;

    public PushController(PushNotificationService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/vapid-public-key")
    public VapidKeyResponse vapidPublicKey() {
        return new VapidKeyResponse(pushService.publicKey());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthUser Long userId, @Valid @RequestBody SubscribeRequest req) {
        pushService.subscribe(userId, req);
    }

    @DeleteMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthUser Long userId, @RequestParam String endpoint) {
        pushService.unsubscribe(userId, endpoint);
    }
}
