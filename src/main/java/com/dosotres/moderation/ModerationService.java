package com.dosotres.moderation;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.moderation.dto.BlockedUserResponse;
import com.dosotres.moderation.dto.CreateReportRequest;
import com.dosotres.moderation.dto.ReportResponse;
import com.dosotres.user.GlobalRole;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModerationService {

    private final ReportRepository reportRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public ModerationService(ReportRepository reportRepository,
                             UserBlockRepository userBlockRepository,
                             UserRepository userRepository,
                             Clock clock) {
        this.reportRepository = reportRepository;
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // ── Reportes ────────────────────────────────────────────────────────────

    /** Cualquier usuario autenticado puede reportar contenido. */
    public ReportResponse createReport(Long reporterId, CreateReportRequest req) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reporterId));

        Report report = new Report();
        report.setReporter(reporter);
        report.setTargetType(req.targetType());
        report.setTargetId(req.targetId());
        report.setReason(req.reason());
        report.setStatus(ReportStatus.OPEN);
        reportRepository.save(report);

        return toResponse(report);
    }

    /** Solo moderador global: cola de reportes por estado. */
    @Transactional(readOnly = true)
    public List<ReportResponse> listReports(Long requesterId, ReportStatus status) {
        requireModerator(requesterId);
        return reportRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Solo moderador global: resolver o descartar un reporte. */
    public ReportResponse resolveReport(Long requesterId, Long reportId, ReportStatus newStatus) {
        User moderator = requireModerator(requesterId);
        if (newStatus == ReportStatus.OPEN) {
            throw new ValidationException("Un reporte se resuelve como RESOLVED o DISMISSED");
        }
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", "id", reportId));

        report.setStatus(newStatus);
        report.setResolvedBy(moderator);
        report.setResolvedAt(clock.instant());

        return toResponse(report);
    }

    // ── Bloqueos ────────────────────────────────────────────────────────────

    /** Bloquea a otro usuario. Idempotente: bloquear de nuevo no falla. */
    public void blockUser(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ValidationException("No podés bloquearte a vos mismo");
        }
        if (userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return;
        }
        User blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", blockerId));
        User blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", blockedId));

        UserBlock block = new UserBlock();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        userBlockRepository.save(block);
    }

    /** Desbloquea. Idempotente: si no existía el bloqueo, no falla. */
    public void unblockUser(Long blockerId, Long blockedId) {
        userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlocks(Long blockerId) {
        return userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId).stream()
                .map(b -> new BlockedUserResponse(b.getBlocked().getId(), b.getBlocked().getDisplayName()))
                .toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private User requireModerator(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getGlobalRole() != GlobalRole.MODERATOR) {
            throw new ForbiddenException("Requiere rol de moderador global");
        }
        return user;
    }

    private ReportResponse toResponse(Report r) {
        return new ReportResponse(
                r.getId(),
                r.getReporter().getId(),
                r.getReporter().getDisplayName(),
                r.getTargetType().name(),
                r.getTargetId(),
                r.getReason(),
                r.getStatus().name(),
                r.getCreatedAt() != null ? r.getCreatedAt().toString() : null,
                r.getResolvedBy() != null ? r.getResolvedBy().getId() : null,
                r.getResolvedAt() != null ? r.getResolvedAt().toString() : null
        );
    }
}
