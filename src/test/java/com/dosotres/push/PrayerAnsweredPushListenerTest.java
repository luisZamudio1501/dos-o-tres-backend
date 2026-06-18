package com.dosotres.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.group.GroupMemberRepository;
import com.dosotres.prayer.PrayerAnsweredEvent;
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
class PrayerAnsweredPushListenerTest {

    private static final Long REQUEST = 10L;
    private static final Long GROUP = 5L;
    private static final Long AUTHOR = 1L;

    @Mock
    private PushNotificationService pushService;
    @Mock
    private GroupMemberRepository groupMemberRepository;

    private PrayerAnsweredPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new PrayerAnsweredPushListener(pushService, groupMemberRepository, new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onPrayerAnswered_notifiesAllGroupMembers() {
        when(groupMemberRepository.findUserIdsByGroupId(GROUP)).thenReturn(List.of(1L, 2L, 3L));

        listener.onPrayerAnswered(new PrayerAnsweredEvent(REQUEST, "Salud de mamá", AUTHOR, GROUP));

        ArgumentCaptor<Collection<Long>> recipients = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUsers(recipients.capture(), payload.capture());

        assertThat(recipients.getValue()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(payload.getValue()).contains("Salud de mamá");
        assertThat(payload.getValue()).contains("/prayer/10");
    }

    @Test
    void onPrayerAnswered_noopWhenGroupHasNoMembers() {
        when(groupMemberRepository.findUserIdsByGroupId(GROUP)).thenReturn(List.of());

        listener.onPrayerAnswered(new PrayerAnsweredEvent(REQUEST, "Sin miembros", AUTHOR, GROUP));

        verify(pushService, never()).sendToUsers(any(), anyString());
    }

    @Test
    void onPrayerAnswered_noopWhenPrivatePrayer() {
        listener.onPrayerAnswered(new PrayerAnsweredEvent(REQUEST, "Privado", AUTHOR, null));

        verify(pushService, never()).sendToUsers(any(), anyString());
        verify(groupMemberRepository, never()).findUserIdsByGroupId(any());
    }
}
