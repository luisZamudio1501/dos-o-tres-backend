package com.dosotres.push;

import com.dosotres.push.dto.SubscribeRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import nl.martijndwars.webpush.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Infraestructura de Web Push: alta/baja de suscripciones y envío via VAPID.
 * El cliente de la librería se construye lazy (solo al enviar), de modo que el
 * contexto arranca aunque las claves VAPID no estén configuradas (dev/test).
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushSubscriptionRepository repo;
    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private nl.martijndwars.webpush.PushService client;

    public PushNotificationService(
            PushSubscriptionRepository repo,
            @Value("${app.push.vapid.public-key:}") String publicKey,
            @Value("${app.push.vapid.private-key:}") String privateKey,
            @Value("${app.push.vapid.subject:mailto:soporte@mateo1819.org}") String subject) {
        this.repo = repo;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
        this.subject = subject;
    }

    /** Clave pública VAPID que el frontend usa para suscribir. */
    public String publicKey() {
        return publicKey;
    }

    /** Alta idempotente: un endpoint repetido actualiza sus claves y dueño. */
    @Transactional
    public void subscribe(Long userId, SubscribeRequest req) {
        PushSubscription sub = repo.findByEndpoint(req.endpoint())
                .orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(req.endpoint());
        sub.setP256dh(req.keys().p256dh());
        sub.setAuth(req.keys().auth());
        repo.save(sub);
    }

    /** Baja: solo el dueño puede borrar su suscripción. */
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        repo.findByEndpoint(endpoint)
                .filter(s -> s.getUserId().equals(userId))
                .ifPresent(repo::delete);
    }

    /**
     * Envía un payload a todas las suscripciones de los usuarios dados. Best-effort:
     * los errores se loguean y no propagan; las suscripciones caducas se eliminan.
     */
    public void sendToUsers(Collection<Long> userIds, String payloadJson) {
        if (userIds.isEmpty() || !configured()) {
            return;
        }
        for (PushSubscription sub : repo.findByUserIdIn(userIds)) {
            send(sub, payloadJson);
        }
    }

    private void send(PushSubscription sub, String payloadJson) {
        try {
            Notification notification = Notification.builder()
                    .endpoint(sub.getEndpoint())
                    .userPublicKey(sub.getP256dh())
                    .userAuth(sub.getAuth())
                    .payload(payloadJson.getBytes(StandardCharsets.UTF_8))
                    .build();
            var response = client().send(notification);
            int status = response.getStatusLine().getStatusCode();
            if (status == 404 || status == 410) {
                repo.delete(sub); // suscripción expirada / desinstalada
            } else if (status >= 400) {
                log.warn("Push falló endpoint={} status={}", sub.getEndpoint(), status);
            }
        } catch (Exception e) {
            log.warn("Push error endpoint={}: {}", sub.getEndpoint(), e.getMessage());
        }
    }

    private boolean configured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    private synchronized nl.martijndwars.webpush.PushService client() throws Exception {
        if (client == null) {
            // La librería registra el provider BouncyCastle en su static block (Utils).
            client = new nl.martijndwars.webpush.PushService(publicKey, privateKey, subject);
        }
        return client;
    }
}
