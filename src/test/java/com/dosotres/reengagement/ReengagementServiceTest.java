package com.dosotres.reengagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.prayer.PrayerCommitmentRepository;
import com.dosotres.prayer.PrayerRequestStatus;
import com.dosotres.push.PushNotificationService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReengagementServiceTest {

    private static final Long USER_ID = 7L;
    private static final List<PrayerRequestStatus> WAITING_STATUSES =
            List.of(PrayerRequestStatus.ACTIVE, PrayerRequestStatus.ON_HOLD);

    @Mock
    private PrayerCommitmentRepository commitmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushNotificationService pushService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-18T12:00:00Z"), ZoneId.of("UTC"));

    private ReengagementService service;

    @BeforeEach
    void setUp() {
        service = new ReengagementService(commitmentRepository, userRepository, pushService,
                new ObjectMapper(), fixedClock, 3);
    }

    private User makeUser(Long id, LocalDate lastReengagedOn) {
        User u = new User();
        u.setId(id);
        u.setTimezone("UTC");
        u.setLastReengagedOn(lastReengagedOn);
        return u;
    }

    @Test
    void userWithStaleRequest_getsPushAndMarked() {
        when(commitmentRepository.findStaleRequestCountsByUser(eq(WAITING_STATUSES), any()))
                .thenReturn(List.<Object[]>of(new Object[]{USER_ID, 1L}));
        User user = makeUser(USER_ID, null);
        when(userRepository.findAllById(anySet())).thenReturn(List.of(user));

        service.sendDueReengagements();

        verify(pushService).sendToUsers(eq(List.of(USER_ID)), anyString());
        assertThat(user.getLastReengagedOn()).isEqualTo(LocalDate.parse("2026-06-18"));
        verify(userRepository).save(user);
    }

    @Test
    void userAlreadyReengagedToday_isSkipped() {
        when(commitmentRepository.findStaleRequestCountsByUser(eq(WAITING_STATUSES), any()))
                .thenReturn(List.<Object[]>of(new Object[]{USER_ID, 1L}));
        User user = makeUser(USER_ID, LocalDate.parse("2026-06-18"));
        when(userRepository.findAllById(anySet())).thenReturn(List.of(user));

        service.sendDueReengagements();

        verify(pushService, never()).sendToUsers(any(), anyString());
        verify(userRepository, never()).save(user);
    }

    @Test
    void noStaleUsers_doesNothing() {
        when(commitmentRepository.findStaleRequestCountsByUser(eq(WAITING_STATUSES), any()))
                .thenReturn(List.of());

        service.sendDueReengagements();

        verify(pushService, never()).sendToUsers(any(), anyString());
        verify(userRepository, never()).findAllById(anySet());
    }
}
