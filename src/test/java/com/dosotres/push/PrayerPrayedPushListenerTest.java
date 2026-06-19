package com.dosotres.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.prayer.PrayerPrayedEvent;
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
class PrayerPrayedPushListenerTest {

    private static final Long REQUEST = 10L;
    private static final Long AUTHOR = 1L;
    private static final Long PRAYER = 2L;

    @Mock
    private PushNotificationService pushService;
    @Mock
    private UserRepository userRepository;

    private PrayerPrayedPushListener listener;

    @BeforeEach
    void setUp() {
        listener = new PrayerPrayedPushListener(pushService, userRepository, new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void onPrayerPrayed_notifiesAuthorWithPrayerName() {
        when(userRepository.findIdByIdInAndNotifyOnPrayedTrue(List.of(AUTHOR))).thenReturn(List.of(AUTHOR));

        listener.onPrayerPrayed(new PrayerPrayedEvent(REQUEST, "Salud de mamá", AUTHOR, PRAYER, "Ana", false));

        ArgumentCaptor<Collection<Long>> recipients = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUsers(recipients.capture(), payload.capture());

        assertThat(recipients.getValue()).containsExactly(AUTHOR);
        assertThat(payload.getValue()).contains("Ana");
        assertThat(payload.getValue()).contains("Salud de mamá");
    }

    @Test
    void onPrayerPrayed_masksNameWhenPrayingPrivately() {
        when(userRepository.findIdByIdInAndNotifyOnPrayedTrue(List.of(AUTHOR))).thenReturn(List.of(AUTHOR));

        listener.onPrayerPrayed(new PrayerPrayedEvent(REQUEST, "Salud de mamá", AUTHOR, PRAYER, "Ana", true));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUsers(any(), payload.capture());

        assertThat(payload.getValue()).contains("Alguien");
        assertThat(payload.getValue()).doesNotContain("Ana");
    }

    @Test
    void onPrayerPrayed_noopWhenAuthorPrayedForOwnRequest() {
        listener.onPrayerPrayed(new PrayerPrayedEvent(REQUEST, "Propio", AUTHOR, AUTHOR, "Luis", false));

        verify(pushService, never()).sendToUsers(any(), anyString());
        verify(userRepository, never()).findIdByIdInAndNotifyOnPrayedTrue(any());
    }

    @Test
    void onPrayerPrayed_noopWhenAuthorOptedOut() {
        when(userRepository.findIdByIdInAndNotifyOnPrayedTrue(List.of(AUTHOR))).thenReturn(List.of());

        listener.onPrayerPrayed(new PrayerPrayedEvent(REQUEST, "Salud de mamá", AUTHOR, PRAYER, "Ana", false));

        verify(pushService, never()).sendToUsers(any(), anyString());
    }
}
