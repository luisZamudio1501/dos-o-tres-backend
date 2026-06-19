package com.dosotres.prayer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.activity.ActivityEventType;
import com.dosotres.activity.ActivityService;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.timer.PrayerSession;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PrayerSessionSelectionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-11T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 11);

    @Mock
    private SessionPrayerRequestRepository sessionRequestRepository;
    @Mock
    private PrayerRequestRepository prayerRequestRepository;
    @Mock
    private PrayerCommitmentRepository commitmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private PrayerSessionPort sessionPort;
    @Mock
    private ActivityService activityService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PrayerSessionSelectionService service;

    @BeforeEach
    void setUp() {
        service = new PrayerSessionSelectionService(sessionRequestRepository, prayerRequestRepository,
                commitmentRepository, userRepository, groupMemberRepository, sessionPort, activityService,
                eventPublisher, FIXED_CLOCK);
    }

    private Group makeGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setDisplayName("Luis");
        return u;
    }

    private PrayerRequest makeRequest(Long id, Group group, PrayerRequestStatus status) {
        PrayerRequest pr = new PrayerRequest();
        pr.setId(id);
        pr.setGroup(group);
        pr.setAuthor(makeUser(2L));
        pr.setTitle("Pedido " + id);
        pr.setStatus(status);
        return pr;
    }

    @Test
    void attach_savesOneLinkPerDistinctRequest() {
        Group group = makeGroup(1L);
        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(makeRequest(10L, group, PrayerRequestStatus.ACTIVE)));
        when(prayerRequestRepository.findById(20L)).thenReturn(Optional.of(makeRequest(20L, group, PrayerRequestStatus.ACTIVE)));
        when(groupMemberRepository.existsByGroupIdAndUserId(1L, 1L)).thenReturn(true);

        service.attach("session-1", List.of(10L, 20L, 10L), 1L, false);

        verify(sessionRequestRepository, times(2)).save(any(SessionPrayerRequest.class));
    }

    @Test
    void attach_rejectsAnsweredRequest() {
        // ON_HOLD ahora es orable; solo ANSWERED se rechaza.
        Group group = makeGroup(1L);
        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(makeRequest(10L, group, PrayerRequestStatus.ANSWERED)));
        when(groupMemberRepository.existsByGroupIdAndUserId(1L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.attach("session-1", List.of(10L), 1L, false))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void attach_rejectsGroupRequestUserIsNotMemberOf() {
        // No-fuga entre grupos: sin membresía, el pedido se reporta inexistente.
        Group otherGroup = makeGroup(99L);
        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(makeRequest(10L, otherGroup, PrayerRequestStatus.ACTIVE)));

        assertThatThrownBy(() -> service.attach("session-1", List.of(10L), 1L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void attach_allowsOwnPrivateRequest() {
        PrayerRequest pr = new PrayerRequest();
        pr.setId(10L);
        pr.setAuthor(makeUser(1L));
        pr.setTitle("Privado");
        pr.setStatus(PrayerRequestStatus.ACTIVE);
        pr.setVisibility(PrayerVisibility.PRIVATE);
        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        service.attach("session-1", List.of(10L), 1L, false);

        verify(sessionRequestRepository).save(any(SessionPrayerRequest.class));
    }

    @Test
    void attach_rejectsPrivateRequestOfAnotherUser() {
        PrayerRequest pr = new PrayerRequest();
        pr.setId(10L);
        pr.setAuthor(makeUser(2L));
        pr.setTitle("Privado ajeno");
        pr.setStatus(PrayerRequestStatus.ACTIVE);
        pr.setVisibility(PrayerVisibility.PRIVATE);
        when(prayerRequestRepository.findById(10L)).thenReturn(Optional.of(pr));

        assertThatThrownBy(() -> service.attach("session-1", List.of(10L), 1L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fulfilForSession_createsCommitmentPerRequest() {
        Group group = makeGroup(1L);
        User user = makeUser(1L);
        PrayerRequest pr1 = makeRequest(10L, group, PrayerRequestStatus.ACTIVE);
        PrayerRequest pr2 = makeRequest(20L, group, PrayerRequestStatus.ACTIVE);

        SessionPrayerRequest link1 = new SessionPrayerRequest();
        link1.setSessionId("session-1");
        link1.setPrayerRequest(pr1);
        SessionPrayerRequest link2 = new SessionPrayerRequest();
        link2.setSessionId("session-1");
        link2.setPrayerRequest(pr2);
        link2.setPrivate(true);

        PrayerSession session = new PrayerSession();
        session.setId("session-1");
        session.setUser(user);

        when(sessionRequestRepository.findBySessionId("session-1")).thenReturn(List.of(link1, link2));
        when(sessionPort.findById("session-1")).thenReturn(Optional.of(session));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(any(), any(), eq(TODAY)))
                .thenReturn(Optional.empty());
        when(commitmentRepository.save(any(PrayerCommitment.class))).thenAnswer(inv -> inv.getArgument(0));

        int fulfilled = service.fulfilForSession("session-1", 1L);

        assertThat(fulfilled).isEqualTo(2);
        verify(commitmentRepository, times(2)).save(any(PrayerCommitment.class));
        verify(activityService).record(eq(group), eq(user), eq(ActivityEventType.COMMITMENT_FULFILLED), eq(false), anyMap());
        verify(activityService).record(eq(group), eq(user), eq(ActivityEventType.COMMITMENT_FULFILLED), eq(true), anyMap());
        verify(eventPublisher, times(2)).publishEvent(any(PrayerPrayedEvent.class));
    }

    @Test
    void fulfilForSession_skipsAlreadyFulfilledCommitment() {
        Group group = makeGroup(1L);
        User user = makeUser(1L);
        PrayerRequest pr = makeRequest(10L, group, PrayerRequestStatus.ACTIVE);

        SessionPrayerRequest link = new SessionPrayerRequest();
        link.setSessionId("session-1");
        link.setPrayerRequest(pr);

        PrayerSession session = new PrayerSession();
        session.setId("session-1");
        session.setUser(user);

        PrayerCommitment existing = new PrayerCommitment();
        existing.setFulfilled(true);

        when(sessionRequestRepository.findBySessionId("session-1")).thenReturn(List.of(link));
        when(sessionPort.findById("session-1")).thenReturn(Optional.of(session));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commitmentRepository.findByPrayerRequestIdAndUserIdAndCommittedDate(10L, 1L, TODAY))
                .thenReturn(Optional.of(existing));

        int fulfilled = service.fulfilForSession("session-1", 1L);

        assertThat(fulfilled).isZero();
        verify(commitmentRepository, never()).save(any(PrayerCommitment.class));
        verify(activityService, never()).record(any(), any(), any(), anyBoolean(), anyMap());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void fulfilForSession_returnsZeroWhenNoSelections() {
        when(sessionRequestRepository.findBySessionId("session-1")).thenReturn(List.of());

        int fulfilled = service.fulfilForSession("session-1", 1L);

        assertThat(fulfilled).isZero();
    }
}
