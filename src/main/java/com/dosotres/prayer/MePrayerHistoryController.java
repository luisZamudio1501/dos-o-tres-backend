package com.dosotres.prayer;

import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.security.annotations.AuthUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agenda de oración del usuario: todo pedido por el que oró alguna vez,
 * cross-group. Vive bajo /api/me/** → excluido de X-Group-Id (F.2).
 */
@RestController
@RequestMapping("/api/me/prayer-history")
public class MePrayerHistoryController {

    private final PrayerRequestService prayerRequestService;

    public MePrayerHistoryController(PrayerRequestService prayerRequestService) {
        this.prayerRequestService = prayerRequestService;
    }

    @GetMapping
    public Page<PrayerRequestResponse> history(
            @AuthUser Long userId,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return prayerRequestService.prayerHistory(userId, pageable);
    }
}
