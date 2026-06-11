package com.dosotres.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ConflictException;
import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.group.Group;
import com.dosotres.group.GroupRepository;
import com.dosotres.timer.PrayerSession.SessionStatus;
import com.dosotres.timer.dto.SessionResponse;
import com.dosotres.timer.dto.StartSessionRequest;
import com.dosotres.timer.dto.SyncSessionRequest;
import com.dosotres.timer.port.PrayerSessionPort;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimerServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-27T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @Mock
    private PrayerSessionPort sessionPort;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GroupRepository groupRepository;

    private TimerService service;

    @BeforeEach
    void setUp() {
        service = new TimerService(sessionPort, userRepository, groupRepository, FIXED_CLOCK);
    }

    private User makeUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setDisplayName("Test User");
        return u;
    }

    private Group makeGroup(Long id) {
        Group g = new Group();
        g.setId(id);
        return g;
    }

    private PrayerSession makeSession(String id, User user, SessionStatus status) {
        PrayerSession s = new PrayerSession();
        s.setId(id);
        s.setUser(user);
        s.setStartedAt(FIXED_NOW);
        s.setDurationSeconds(0);
        s.setStatus(status);
        s.setLastSyncAt(FIXED_NOW);
        return s;
    }

    @Test
    void start_createsActiveSession() {
        User user = makeUser(1L);
        Group group = makeGroup(10L);

        when(sessionPort.findActiveByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(sessionPort.save(any(PrayerSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = service.start(new StartSessionRequest("uuid-1", 10L, null, null), 1L);

        assertThat(response.id()).isEqualTo("uuid-1");
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.groupId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.durationSeconds()).isZero();
    }

    @Test
    void start_withoutGroup_createsSessionWithNullGroupId() {
        User user = makeUser(1L);

        when(sessionPort.findActiveByUserId(1L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sessionPort.save(any(PrayerSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = service.start(new StartSessionRequest("uuid-2", null, null, null), 1L);

        assertThat(response.groupId()).isNull();
    }

    @Test
    void start_conflictsWhenActiveSessionExists() {
        User user = makeUser(1L);
        PrayerSession existing = makeSession("existing", user, SessionStatus.ACTIVE);

        when(sessionPort.findActiveByUserId(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.start(new StartSessionRequest("uuid-3", null, null, null), 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("active prayer session");
    }

    @Test
    void sync_updatesDurationAndLastSync() {
        User user = makeUser(1L);
        PrayerSession session = makeSession("uuid-1", user, SessionStatus.ACTIVE);

        when(sessionPort.findById("uuid-1")).thenReturn(Optional.of(session));
        when(sessionPort.save(any(PrayerSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = service.sync("uuid-1", new SyncSessionRequest(300), 1L);

        assertThat(response.durationSeconds()).isEqualTo(300);
        assertThat(response.lastSyncAt()).isEqualTo(FIXED_NOW.toString());
    }

    @Test
    void sync_forbiddenWhenNotOwner() {
        User user = makeUser(1L);
        PrayerSession session = makeSession("uuid-1", user, SessionStatus.ACTIVE);

        when(sessionPort.findById("uuid-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.sync("uuid-1", new SyncSessionRequest(300), 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void sync_conflictsWhenSessionNotActive() {
        User user = makeUser(1L);
        PrayerSession session = makeSession("uuid-1", user, SessionStatus.COMPLETED);

        when(sessionPort.findById("uuid-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.sync("uuid-1", new SyncSessionRequest(300), 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void stop_changesStatusToCompleted() {
        User user = makeUser(1L);
        PrayerSession session = makeSession("uuid-1", user, SessionStatus.ACTIVE);

        when(sessionPort.findById("uuid-1")).thenReturn(Optional.of(session));
        when(sessionPort.save(any(PrayerSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResponse response = service.stop("uuid-1", 1L);

        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void getById_notFoundThrows() {
        when(sessionPort.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById("nonexistent", 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getActive_returnsEmptyWhenNoActiveSession() {
        when(sessionPort.findActiveByUserId(1L)).thenReturn(Optional.empty());

        Optional<SessionResponse> result = service.getActive(1L);

        assertThat(result).isEmpty();
    }
}
