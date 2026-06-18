package com.dosotres.prayer;

import com.dosotres.prayer.dto.ChangeStatusRequest;
import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayRequest;
import com.dosotres.prayer.dto.PrayerLogResponse;
import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.security.annotations.AuthUser;
import com.dosotres.security.annotations.CurrentGroupId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prayer-requests")
public class PrayerRequestController {

    private final PrayerRequestService prayerRequestService;

    public PrayerRequestController(PrayerRequestService prayerRequestService) {
        this.prayerRequestService = prayerRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrayerRequestResponse create(@Valid @RequestBody CreatePrayerRequest req,
                                         @CurrentGroupId Long groupId,
                                         @AuthUser Long userId) {
        return prayerRequestService.create(req, groupId, userId);
    }

    @GetMapping
    public Page<PrayerRequestResponse> list(@CurrentGroupId Long groupId,
                                             @RequestParam(required = false) PrayerRequestStatus status,
                                             @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                             @AuthUser Long userId) {
        return prayerRequestService.listByGroup(groupId, status, pageable, userId);
    }

    @GetMapping("/{id}")
    public PrayerRequestResponse getById(@PathVariable Long id,
                                          @CurrentGroupId Long groupId,
                                          @AuthUser Long userId) {
        return prayerRequestService.getById(id, groupId, userId);
    }

    @GetMapping("/{id}/prayers")
    public List<PrayerLogResponse> listPrayers(@PathVariable Long id,
                                               @CurrentGroupId Long groupId) {
        return prayerRequestService.listPrayers(id, groupId);
    }

    @PostMapping("/{id}/pray")
    public PrayerRequestResponse pray(@PathVariable Long id,
                                      @RequestBody(required = false) PrayRequest req,
                                      @CurrentGroupId Long groupId,
                                      @AuthUser Long userId) {
        boolean isPrivate = req != null && req.isPrivate();
        return prayerRequestService.pray(id, groupId, userId, isPrivate);
    }

    @PatchMapping("/{id}/answer")
    public PrayerRequestResponse markAsAnswered(@PathVariable Long id,
                                                 @CurrentGroupId Long groupId,
                                                 @AuthUser Long userId) {
        return prayerRequestService.markAsAnswered(id, groupId, userId);
    }

    @PatchMapping("/{id}/status")
    public PrayerRequestResponse changeStatus(@PathVariable Long id,
                                               @Valid @RequestBody ChangeStatusRequest req,
                                               @CurrentGroupId Long groupId,
                                               @AuthUser Long userId) {
        return prayerRequestService.changeStatus(id, groupId, userId, req.status(), req.testimony());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id,
                       @CurrentGroupId Long groupId,
                       @AuthUser Long userId) {
        prayerRequestService.delete(id, groupId, userId);
    }
}
