package com.dosotres.prayer;

import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.prayer.dto.ShareRequest;
import com.dosotres.security.annotations.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espacio personal de pedidos: opera sobre el usuario autenticado, sin contexto
 * de grupo (excluido de X-Group-Id en GroupContextFilter).
 */
@RestController
@RequestMapping("/api/me/prayer-requests")
public class MePrayerRequestController {

    private final PrayerRequestService prayerRequestService;

    public MePrayerRequestController(PrayerRequestService prayerRequestService) {
        this.prayerRequestService = prayerRequestService;
    }

    @GetMapping
    public Page<PrayerRequestResponse> listMine(
            @AuthUser Long userId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return prayerRequestService.listMine(userId, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrayerRequestResponse create(@Valid @RequestBody CreatePrayerRequest req,
                                        @AuthUser Long userId) {
        return prayerRequestService.createPersonal(req, userId);
    }

    @PostMapping("/{id}/share")
    public PrayerRequestResponse share(@PathVariable Long id,
                                       @Valid @RequestBody ShareRequest req,
                                       @AuthUser Long userId) {
        return prayerRequestService.share(id, userId, req.groupId());
    }

    /** Pedidos orables para una sesión unificada (privados + opcionalmente mis grupos). */
    @GetMapping("/prayable")
    public List<PrayerRequestResponse> prayable(@AuthUser Long userId,
                                                @RequestParam(defaultValue = "false") boolean includeGroups) {
        return prayerRequestService.prayable(userId, includeGroups);
    }
}
