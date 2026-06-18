package com.dosotres.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.push.dto.SubscribeRequest;
import com.dosotres.push.dto.SubscribeRequest.Keys;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    private static final Long USER = 1L;
    private static final String ENDPOINT = "https://push.example.com/abc";

    @Mock
    private PushSubscriptionRepository repo;

    private PushNotificationService service;

    @BeforeEach
    void setUp() {
        service = new PushNotificationService(repo, "vapid-pub", "vapid-priv", "mailto:x@y.z");
    }

    private SubscribeRequest request() {
        return new SubscribeRequest(ENDPOINT, new Keys("p256dh-key", "auth-secret"));
    }

    @Test
    void subscribe_createsNewWhenEndpointUnknown() {
        when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

        service.subscribe(USER, request());

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repo).save(captor.capture());
        PushSubscription saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER);
        assertThat(saved.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(saved.getP256dh()).isEqualTo("p256dh-key");
        assertThat(saved.getAuth()).isEqualTo("auth-secret");
    }

    @Test
    void subscribe_updatesExistingByEndpoint() {
        PushSubscription existing = new PushSubscription();
        existing.setId(99L);
        existing.setUserId(USER);
        existing.setEndpoint(ENDPOINT);
        existing.setP256dh("old");
        existing.setAuth("old");
        when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));

        service.subscribe(USER, request());

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(99L); // mismo registro, no uno nuevo
        assertThat(captor.getValue().getP256dh()).isEqualTo("p256dh-key");
    }

    @Test
    void unsubscribe_deletesWhenOwner() {
        PushSubscription sub = new PushSubscription();
        sub.setUserId(USER);
        sub.setEndpoint(ENDPOINT);
        when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(sub));

        service.unsubscribe(USER, ENDPOINT);

        verify(repo).delete(sub);
    }

    @Test
    void unsubscribe_ignoresWhenNotOwner() {
        PushSubscription sub = new PushSubscription();
        sub.setUserId(USER);
        sub.setEndpoint(ENDPOINT);
        when(repo.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(sub));

        service.unsubscribe(999L, ENDPOINT);

        verify(repo, never()).delete(sub);
    }

    @Test
    void sendToUsers_noopWhenNoSubscribers() {
        // Sin usuarios no se consulta el repo ni se envía nada.
        service.sendToUsers(java.util.List.of(), "{}");
        verify(repo, never()).findByUserIdIn(java.util.List.of());
    }

    @Test
    void publicKey_returnsConfiguredValue() {
        assertThat(service.publicKey()).isEqualTo("vapid-pub");
    }
}
