package com.dosotres.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.moderation.dto.CreateReportRequest;
import com.dosotres.moderation.dto.ReportResponse;
import com.dosotres.user.GlobalRole;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ModerationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneId.of("UTC"));

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ModerationAccess moderationAccess;

    private ModerationService service;

    @BeforeEach
    void setUp() {
        service = new ModerationService(reportRepository, userBlockRepository, userRepository,
                moderationAccess, FIXED_CLOCK);
    }

    private User makeUser(Long id, String name, GlobalRole role) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        u.setGlobalRole(role);
        return u;
    }

    private Report makeReport(Long id, User reporter, ReportStatus status) {
        Report r = new Report();
        r.setId(id);
        r.setReporter(reporter);
        r.setTargetType(ReportTargetType.PUBLIC_REQUEST);
        r.setTargetId(99L);
        r.setStatus(status);
        return r;
    }

    @Test
    void createReport_savesAndReturns() {
        User reporter = makeUser(1L, "Ana", GlobalRole.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse res = service.createReport(1L,
                new CreateReportRequest(ReportTargetType.PUBLIC_REQUEST, 99L, "spam"));

        assertThat(res.reporterId()).isEqualTo(1L);
        assertThat(res.targetType()).isEqualTo("PUBLIC_REQUEST");
        assertThat(res.status()).isEqualTo("OPEN");
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    void listReports_byNonModerator_throwsForbidden() {
        when(moderationAccess.requireModerator(1L)).thenThrow(new ForbiddenException("no mod"));

        assertThatThrownBy(() -> service.listReports(1L, ReportStatus.OPEN))
                .isInstanceOf(ForbiddenException.class);
        verify(reportRepository, never()).findByStatusOrderByCreatedAtAsc(any());
    }

    @Test
    void listReports_byModerator_returnsReports() {
        User reporter = makeUser(1L, "Ana", GlobalRole.USER);
        when(reportRepository.findByStatusOrderByCreatedAtAsc(ReportStatus.OPEN))
                .thenReturn(List.of(makeReport(10L, reporter, ReportStatus.OPEN)));

        List<ReportResponse> res = service.listReports(5L, ReportStatus.OPEN);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).id()).isEqualTo(10L);
    }

    @Test
    void resolveReport_byModerator_setsResolvedAndModerator() {
        User mod = makeUser(5L, "Luis", GlobalRole.MODERATOR);
        User reporter = makeUser(1L, "Ana", GlobalRole.USER);
        Report report = makeReport(10L, reporter, ReportStatus.OPEN);
        when(moderationAccess.requireModerator(5L)).thenReturn(mod);
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        ReportResponse res = service.resolveReport(5L, 10L, ReportStatus.RESOLVED);

        assertThat(res.status()).isEqualTo("RESOLVED");
        assertThat(res.resolvedById()).isEqualTo(5L);
        assertThat(report.getResolvedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void resolveReport_byNonModerator_throwsForbidden() {
        when(moderationAccess.requireModerator(1L)).thenThrow(new ForbiddenException("no mod"));

        assertThatThrownBy(() -> service.resolveReport(1L, 10L, ReportStatus.RESOLVED))
                .isInstanceOf(ForbiddenException.class);
        verify(reportRepository, never()).findById(any());
    }

    @Test
    void resolveReport_toOpen_throwsValidation() {
        when(moderationAccess.requireModerator(5L)).thenReturn(makeUser(5L, "Luis", GlobalRole.MODERATOR));

        assertThatThrownBy(() -> service.resolveReport(5L, 10L, ReportStatus.OPEN))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void blockUser_savesBlock() {
        User blocker = makeUser(1L, "Ana", GlobalRole.USER);
        User blocked = makeUser(2L, "Bruno", GlobalRole.USER);
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(blocker));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));

        service.blockUser(1L, 2L);

        verify(userBlockRepository).save(any(UserBlock.class));
    }

    @Test
    void blockUser_self_throwsValidation() {
        assertThatThrownBy(() -> service.blockUser(1L, 1L))
                .isInstanceOf(ValidationException.class);
        verify(userBlockRepository, never()).save(any(UserBlock.class));
    }

    @Test
    void blockUser_duplicate_isIdempotent() {
        when(userBlockRepository.existsByBlockerIdAndBlockedId(1L, 2L)).thenReturn(true);

        service.blockUser(1L, 2L);

        verify(userBlockRepository, never()).save(any(UserBlock.class));
    }

    @Test
    void unblockUser_deletesIfPresent() {
        UserBlock block = new UserBlock();
        when(userBlockRepository.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(block));

        service.unblockUser(1L, 2L);

        verify(userBlockRepository).delete(block);
    }
}
