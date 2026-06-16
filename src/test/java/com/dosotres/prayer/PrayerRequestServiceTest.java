package com.dosotres.prayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMember;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.group.GroupRepository;
import com.dosotres.group.GroupRole;
import com.dosotres.prayer.dto.CreatePrayerRequest;
import com.dosotres.prayer.dto.PrayerLogResponse;
import com.dosotres.prayer.dto.PrayerRequestResponse;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PrayerRequestServiceTest {

    @Mock
    private PrayerRequestRepository prayerRequestRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PrayerCommitmentRepository commitmentRepository;
    @Mock
    private SessionPrayerRequestRepository sessionPrayerRequestRepository;
    @Mock
    private ActivityService activityService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-26T12:00:00Z"), ZoneId.of("UTC"));

    private PrayerRequestService service;

    @BeforeEach
    void setUp() {
        service = new PrayerRequestService(prayerRequestRepository, groupRepository, groupMemberRepository,
                userRepository, commitmentRepository, sessionPrayerRequestRepository, activityService, fixedClock);
    }

    private Group makeGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private User makeUser(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        return u;
    }

    private PrayerRequest makePrayerRequest(Long id, Group group, User author, PrayerRequestStatus status) {
        PrayerRequest pr = new PrayerRequest();
        pr.setId(id);
        pr.setGroup(group);
        pr.setAuthor(author);
        pr.setTitle("Test prayer");
        pr.setStatus(status);
        pr.onCreate();
        return pr;
    }

    @Test
    void create_setsActiveStatus() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");

        when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> {
            PrayerRequest pr = inv.getArgument(0);
            pr.setId(10L);
            pr.onCreate();
            return pr;
        });

        PrayerRequestResponse response = service.create(
                new CreatePrayerRequest("Salud para mamá", "Por favor orar"), 1L, 1L);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.title()).isEqualTo("Salud para mamá");
        assertThat(response.authorName()).isEqualTo("Luis");
        assertThat(response.answeredAt()).isNull();
    }

    @Test
    void listByGroup_withStatusFilter_usesFindByGroupIdAndStatus() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, user, PrayerRequestStatus.ACTIVE);

        Pageable pageable = PageRequest.of(0, 10);
        when(prayerRequestRepository.findByGroupIdAndStatus(eq(1L), eq(PrayerRequestStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pr), pageable, 1));

        Page<PrayerRequestResponse> result = service.listByGroup(1L, PrayerRequestStatus.ACTIVE, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("ACTIVE");
    }

    @Test
    void listByGroup_withoutStatus_usesFindByGroupId() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr1 = makePrayerRequest(10L, group, user, PrayerRequestStatus.ACTIVE);
        PrayerRequest pr2 = makePrayerRequest(11L, group, user, PrayerRequestStatus.ANSWERED);

        Pageable pageable = PageRequest.of(0, 10);
        when(prayerRequestRepository.findByGroupId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pr1, pr2), pageable, 2));

        Page<PrayerRequestResponse> result = service.listByGroup(1L, null, pageable);

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void markAsAnswered_authorCanMark() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        PrayerRequestResponse response = service.markAsAnswered(10L, 1L, 1L);

        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.answeredAt()).isNotNull();
    }

    @Test
    void markAsAnswered_adminCanMark() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        User admin = makeUser(2L, "Admin");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        GroupMember adminMember = new GroupMember();
        adminMember.setRole(GroupRole.ADMIN);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 2L)).thenReturn(Optional.of(adminMember));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        PrayerRequestResponse response = service.markAsAnswered(10L, 1L, 2L);

        assertThat(response.status()).isEqualTo("ANSWERED");
    }

    @Test
    void markAsAnswered_nonAuthorMemberThrowsForbidden() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        GroupMember member = new GroupMember();
        member.setRole(GroupRole.MEMBER);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 99L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.markAsAnswered(10L, 1L, 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markAsAnswered_alreadyAnsweredThrowsConflict() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ANSWERED);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.markAsAnswered(10L, 1L, 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already answered");
    }

    @Test
    void changeStatus_manualOnHoldOnActiveThrowsValidation() {
        // V3: el "en espera" se produce al orar, no manualmente.
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.changeStatus(10L, 1L, 1L, PrayerRequestStatus.ON_HOLD, null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void changeStatus_reactivateAnsweredRecalculatesToActiveWithoutPrayers() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ANSWERED);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commitmentRepository.countDistinctUsersByPrayerRequestId(10L)).thenReturn(0L);

        PrayerRequestResponse response = service.changeStatus(10L, 1L, 1L, PrayerRequestStatus.ACTIVE, null);

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.answeredAt()).isNull();
    }

    @Test
    void changeStatus_answeredWithTestimonySavesIt() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        PrayerRequestResponse response = service.changeStatus(
                10L, 1L, 1L, PrayerRequestStatus.ANSWERED, "¡Dios respondió! Consiguió el trabajo.");

        assertThat(response.status()).isEqualTo("ANSWERED");
        assertThat(response.testimony()).isEqualTo("¡Dios respondió! Consiguió el trabajo.");
        assertThat(response.answeredAt()).isNotNull();
    }

    @Test
    void changeStatus_testimonyByNonAuthorThrowsForbidden() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        GroupMember adminMember = new GroupMember();
        adminMember.setRole(GroupRole.ADMIN);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 2L)).thenReturn(Optional.of(adminMember));

        assertThatThrownBy(() -> service.changeStatus(
                10L, 1L, 2L, PrayerRequestStatus.ANSWERED, "Testimonio ajeno"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("author");
    }

    @Test
    void changeStatus_testimonyOnNonAnsweredThrowsValidation() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.changeStatus(
                10L, 1L, 1L, PrayerRequestStatus.ON_HOLD, "No corresponde acá"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void delete_adminRemovesRequestAndChildren() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        GroupMember adminMember = new GroupMember();
        adminMember.setRole(GroupRole.ADMIN);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 2L)).thenReturn(Optional.of(adminMember));

        service.delete(10L, 1L, 2L);

        verify(sessionPrayerRequestRepository).deleteByPrayerRequestId(10L);
        verify(commitmentRepository).deleteByPrayerRequestId(10L);
        verify(prayerRequestRepository).delete(pr);
    }

    @Test
    void delete_nonAdminThrowsForbiddenAndDeletesNothing() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        GroupMember member = new GroupMember();
        member.setRole(GroupRole.MEMBER);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(groupMemberRepository.findByGroupIdAndUserId(1L, 1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.delete(10L, 1L, 1L))
                .isInstanceOf(ForbiddenException.class);
        verify(prayerRequestRepository, never()).delete(any(PrayerRequest.class));
    }

    @Test
    void pray_onActiveRequestMovesItToOnHold() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        User prayer = makeUser(2L, "Ana");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ACTIVE);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(userRepository.findById(2L)).thenReturn(Optional.of(prayer));
        when(commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(eq(10L), eq(2L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(commitmentRepository.save(any(PrayerCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(prayerRequestRepository.save(any(PrayerRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commitmentRepository.countDistinctUsersByPrayerRequestId(10L)).thenReturn(1L);

        PrayerRequestResponse response = service.pray(10L, 1L, 2L, false);

        assertThat(response.status()).isEqualTo("ON_HOLD");
        assertThat(response.prayedByCount()).isEqualTo(1);
        assertThat(pr.getStatus()).isEqualTo(PrayerRequestStatus.ON_HOLD);
        verify(activityService).record(eq(group), eq(prayer),
                eq(ActivityEventType.COMMITMENT_FULFILLED), eq(false), anyMap());
    }

    @Test
    void pray_isIdempotentSameDay() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        User prayer = makeUser(2L, "Ana");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ON_HOLD);

        PrayerCommitment existing = new PrayerCommitment();
        existing.setFulfilled(true);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(userRepository.findById(2L)).thenReturn(Optional.of(prayer));
        when(commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(eq(10L), eq(2L), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));

        service.pray(10L, 1L, 2L, false);

        verify(commitmentRepository, never()).save(any(PrayerCommitment.class));
        verify(activityService, never()).record(any(), any(), any(), anyBoolean(), anyMap());
    }

    @Test
    void pray_onAnsweredThrowsValidation() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ANSWERED);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.pray(10L, 1L, 2L, false))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void listPrayers_masksPrivateOnes() {
        Group group = makeGroup(1L);
        User author = makeUser(1L, "Luis");
        User ana = makeUser(2L, "Ana");
        PrayerRequest pr = makePrayerRequest(10L, group, author, PrayerRequestStatus.ON_HOLD);

        PrayerCommitment publicC = new PrayerCommitment();
        publicC.setUser(ana);
        publicC.setPrayerRequest(pr);
        publicC.setFulfilled(true);
        publicC.setFulfilledAt(Instant.parse("2026-05-26T10:00:00Z"));
        publicC.setPrivate(false);

        PrayerCommitment privateC = new PrayerCommitment();
        privateC.setUser(author);
        privateC.setPrayerRequest(pr);
        privateC.setFulfilled(true);
        privateC.setFulfilledAt(Instant.parse("2026-05-26T09:00:00Z"));
        privateC.setPrivate(true);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(commitmentRepository.findByPrayerRequestIdAndFulfilledTrueOrderByFulfilledAtDesc(10L))
                .thenReturn(List.of(publicC, privateC));

        List<PrayerLogResponse> log = service.listPrayers(10L, 1L);

        assertThat(log).hasSize(2);
        assertThat(log.get(0).displayName()).isEqualTo("Ana");
        assertThat(log.get(1).displayName()).isEqualTo("Alguien");
        assertThat(log.get(1).isPrivate()).isTrue();
    }
}
