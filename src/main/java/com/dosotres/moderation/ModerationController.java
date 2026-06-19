package com.dosotres.moderation;

import com.dosotres.moderation.dto.BlockUserRequest;
import com.dosotres.moderation.dto.BlockedUserResponse;
import com.dosotres.moderation.dto.CreateReportRequest;
import com.dosotres.moderation.dto.ReportResponse;
import com.dosotres.moderation.dto.ResolveReportRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/moderation")
public class ModerationController {

    private final ModerationService moderationService;

    public ModerationController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    // ── Reportes ────────────────────────────────────────────────────────────

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createReport(@AuthUser Long userId,
                                       @Valid @RequestBody CreateReportRequest req) {
        return moderationService.createReport(userId, req);
    }

    @GetMapping("/reports")
    public List<ReportResponse> listReports(@AuthUser Long userId,
                                            @RequestParam(defaultValue = "OPEN") ReportStatus status) {
        return moderationService.listReports(userId, status);
    }

    @PatchMapping("/reports/{id}")
    public ReportResponse resolveReport(@AuthUser Long userId,
                                        @PathVariable Long id,
                                        @Valid @RequestBody ResolveReportRequest req) {
        return moderationService.resolveReport(userId, id, req.status());
    }

    // ── Bloqueos ────────────────────────────────────────────────────────────

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthUser Long userId, @Valid @RequestBody BlockUserRequest req) {
        moderationService.blockUser(userId, req.userId());
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthUser Long userId, @PathVariable Long blockedUserId) {
        moderationService.unblockUser(userId, blockedUserId);
    }

    @GetMapping("/blocks")
    public List<BlockedUserResponse> listBlocks(@AuthUser Long userId) {
        return moderationService.listBlocks(userId);
    }
}
