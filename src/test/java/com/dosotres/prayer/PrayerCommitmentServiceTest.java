package com.dosotres.prayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.prayer.dto.CommitmentResponse;
import com.dosotres.prayer.dto.CreateCommitmentRequest;
import com.dosotres.prayer.dto.FulfilCommitmentRequest;
import com.dosotres.timer.PrayerSession;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.port.PrayerSessionPort;
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

@ExtendWith(MockitoExtension.class)
class PrayerCommitmentServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-27T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock
    private PrayerCommitmentRepository commitmentRepository;
    @Mock
    private PrayerRequestRepository prayerRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PrayerSessionPort sessionPort;
    @Mock
    private ActivityService activityService;

    private PrayerCommitmentService service;

    @BeforeEach
    void setUp() {
        service = new PrayerCommitmentService(commitmentRepository, prayerRequestRepository, userRepository, sessionPort, activityService, FIXED_CLOCK);
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

    private PrayerRequest makePrayerRequest(Long id, Group group) {
        PrayerRequest pr = new PrayerRequest();
        pr.setId(id);
        pr.setGroup(group);
        pr.setTitle("Test prayer");
        pr.setStatus(PrayerRequestStatus.ACTIVE);
        return pr;
    }

    @Test
    void create_savesCommitment() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(10L, 1L, LocalDate.of(2026, 5, 27)))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commitmentRepository.save(any(PrayerCommitment.class))).thenAnswer(inv -> {
            PrayerCommitment c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });

        CommitmentResponse response = service.create(
                new CreateCommitmentRequest(10L, "2026-05-27"), 1L, 1L);

        assertThat(response.prayerRequestId()).isEqualTo(10L);
        assertThat(response.prayerRequestTitle()).isEqualTo("Test prayer");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.committedDate()).isEqualTo("2026-05-27");
        assertThat(response.fulfilled()).isFalse();
    }

    @Test
    void create_conflictWhenDuplicate() {
        Group group = makeGroup(1L);
        PrayerRequest pr = makePrayerRequest(10L, group);
        PrayerCommitment existing = new PrayerCommitment();

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(10L, 1L, LocalDate.of(2026, 5, 27)))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(
                new CreateCommitmentRequest(10L, "2026-05-27"), 1L, 1L))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_notFoundWhenPrayerRequestNotInGroup() {
        Group group = makeGroup(1L);
        PrayerRequest pr = makePrayerRequest(10L, group);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.create(
                new CreateCommitmentRequest(10L, "2026-05-27"), 99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listByDate_filtersbyGroup() {
        Group group1 = makeGroup(1L);
        Group group2 = makeGroup(2L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr1 = makePrayerRequest(10L, group1);
        PrayerRequest pr2 = makePrayerRequest(20L, group2);

        PrayerCommitment c1 = new PrayerCommitment();
        c1.setId(100L);
        c1.setPrayerRequest(pr1);
        c1.setUser(user);
        c1.setCommittedDate(LocalDate.of(2026, 5, 27));

        PrayerCommitment c2 = new PrayerCommitment();
        c2.setId(101L);
        c2.setPrayerRequest(pr2);
        c2.setUser(user);
        c2.setCommittedDate(LocalDate.of(2026, 5, 27));

        when(commitmentRepository.findByUserIdAndCommittedDate(1L, LocalDate.of(2026, 5, 27)))
                .thenReturn(List.of(c1, c2));

        List<CommitmentResponse> result = service.listByDate(1L, 1L, LocalDate.of(2026, 5, 27));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).prayerRequestId()).isEqualTo(10L);
    }

    @Test
    void listByRequest_returnsAllCommitments() {
        Group group = makeGroup(1L);
        User user1 = makeUser(1L, "Luis");
        User user2 = makeUser(2L, "Ana");
        PrayerRequest pr = makePrayerRequest(10L, group);

        PrayerCommitment c1 = new PrayerCommitment();
        c1.setId(100L);
        c1.setPrayerRequest(pr);
        c1.setUser(user1);
        c1.setCommittedDate(LocalDate.of(2026, 5, 27));

        PrayerCommitment c2 = new PrayerCommitment();
        c2.setId(101L);
        c2.setPrayerRequest(pr);
        c2.setUser(user2);
        c2.setCommittedDate(LocalDate.of(2026, 5, 27));

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));
        when(commitmentRepository.findByPrayerRequestId(10L)).thenReturn(List.of(c1, c2));

        List<CommitmentResponse> result = service.listByRequest(10L, 1L);

        assertThat(result).hasSize(2);
    }

    @Test
    void listByRequest_notFoundWhenRequestNotInGroup() {
        Group group = makeGroup(1L);
        PrayerRequest pr = makePrayerRequest(10L, group);

        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.listByRequest(10L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PrayerSession makeSession(String id, User user, SessionStatus status) {
        PrayerSession s = new PrayerSession();
        s.setId(id);
        s.setUser(user);
        s.setStatus(status);
        s.setStartedAt(FIXED_NOW);
        s.setLastSyncAt(FIXED_NOW);
        return s;
    }

    private PrayerCommitment makeCommitment(Long id, User user, PrayerRequest pr, boolean fulfilled) {
        PrayerCommitment c = new PrayerCommitment();
        c.setId(id);
        c.setUser(user);
        c.setPrayerRequest(pr);
        c.setCommittedDate(LocalDate.of(2026, 5, 27));
        c.setFulfilled(fulfilled);
        return c;
    }

    @Test
    void fulfil_successWithActiveSession() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group);
        PrayerCommitment commitment = makeCommitment(100L, user, pr, false);
        PrayerSession session = makeSession("session-1", user, SessionStatus.ACTIVE);

        when(commitmentRepository.findById(100L)).thenReturn(Optional.of(commitment));
        when(sessionPort.findById("session-1")).thenReturn(Optional.of(session));
        when(commitmentRepository.save(any(PrayerCommitment.class))).thenAnswer(inv -> inv.getArgument(0));

        CommitmentResponse response = service.fulfil(100L, new FulfilCommitmentRequest("session-1", false), 1L);

        assertThat(response.fulfilled()).isTrue();
        assertThat(response.fulfilledAt()).isNotNull();
        assertThat(response.sessionId()).isEqualTo("session-1");
    }

    @Test
    void fulfil_conflictWhenAlreadyFulfilled() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group);
        PrayerCommitment commitment = makeCommitment(100L, user, pr, true);

        when(commitmentRepository.findById(100L)).thenReturn(Optional.of(commitment));

        assertThatThrownBy(() -> service.fulfil(100L, new FulfilCommitmentRequest("session-1", false), 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already fulfilled");
    }

    @Test
    void fulfil_validationExceptionWhenSessionNotActive() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        PrayerRequest pr = makePrayerRequest(10L, group);
        PrayerCommitment commitment = makeCommitment(100L, user, pr, false);
        PrayerSession session = makeSession("session-1", user, SessionStatus.COMPLETED);

        when(commitmentRepository.findById(100L)).thenReturn(Optional.of(commitment));
        when(sessionPort.findById("session-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.fulfil(100L, new FulfilCommitmentRequest("session-1", false), 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cronómetro activo");
    }

    @Test
    void fulfil_forbiddenWhenSessionBelongsToOtherUser() {
        Group group = makeGroup(1L);
        User user = makeUser(1L, "Luis");
        User otherUser = makeUser(99L, "Otro");
        PrayerRequest pr = makePrayerRequest(10L, group);
        PrayerCommitment commitment = makeCommitment(100L, user, pr, false);
        PrayerSession session = makeSession("session-1", otherUser, SessionStatus.ACTIVE);

        when(commitmentRepository.findById(100L)).thenReturn(Optional.of(commitment));
        when(sessionPort.findById("session-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.fulfil(100L, new FulfilCommitmentRequest("session-1", false), 1L))
                .isInstanceOf(ForbiddenException.class);
    }
}
