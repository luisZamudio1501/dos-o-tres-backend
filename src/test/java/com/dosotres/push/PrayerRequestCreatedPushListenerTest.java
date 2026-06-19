package com.dosotres.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.group.GroupMemberRepository;
import com.dosotres.prayer.PrayerRequestCreatedEvent;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrayerRequestCreatedPushListenerTest {

    private static final Long REQUEST = 10L;
    private static final Long GROUP = 5L;
    private static final Long AUTHOR = 1L;

    @Mock
    private PushNotificationService pushService;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;

    private PrayerRequestCreatedPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new PrayerRequestCreatedPushListener(pushService, groupMemberRepository, userRepository,
                new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onPrayerRequestCreated_notifiesGroupExceptAuthor() {
        when(groupMemberRepository.findUserIdsByGroupId(GROUP)).thenReturn(List.of(AUTHOR, 2L, 3L));
        when(userRepository.findIdByIdInAndNotifyOnRequestCreatedTrue(List.of(2L, 3L)))
                .thenReturn(List.of(2L, 3L));

        listener.onPrayerRequestCreated(new PrayerRequestCreatedEvent(REQUEST, "Salud de mamá", AUTHOR, GROUP));

        ArgumentCaptor<Collection<Long>> recipients = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUsers(recipients.capture(), payload.capture());

        assertThat(recipients.getValue()).containsExactlyInAnyOrder(2L, 3L);
        assertThat(payload.getValue()).contains("Salud de mamá");
    }

    @Test
    void onPrayerRequestCreated_noopWhenOnlyAuthorInGroup() {
        when(groupMemberRepository.findUserIdsByGroupId(GROUP)).thenReturn(List.of(AUTHOR));

        listener.onPrayerRequestCreated(new PrayerRequestCreatedEvent(REQUEST, "Solo yo", AUTHOR, GROUP));

        verify(pushService, never()).sendToUsers(any(), anyString());
    }

    @Test
    void onPrayerRequestCreated_noopWhenNoMemberOptedIn() {
        when(groupMemberRepository.findUserIdsByGroupId(GROUP)).thenReturn(List.of(AUTHOR, 2L));
        when(userRepository.findIdByIdInAndNotifyOnRequestCreatedTrue(List.of(2L))).thenReturn(List.of());

        listener.onPrayerRequestCreated(new PrayerRequestCreatedEvent(REQUEST, "Nadie quiere avisos", AUTHOR, GROUP));

        verify(pushService, never()).sendToUsers(any(), anyString());
    }
}
