package com.dosotres.push;

import com.dosotres.prayer.PrayerPrayedEvent;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Avisa solo al autor del pedido — nunca a todo el grupo, para no generar ruido. */
@Component
public class PrayerPrayedPushListener {

    private static final Logger log = LoggerFactory.getLogger(PrayerPrayedPushListener.class);

    private final PushNotificationService pushService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PrayerPrayedPushListener(PushNotificationService pushService,
                                    UserRepository userRepository,
                                    ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrayerPrayed(PrayerPrayedEvent event) {
        if (event.prayingUserId().equals(event.authorId())) {
            return; // oró por su propio pedido, no se avisa a sí mismo
        }
        List<Long> recipients = userRepository
                .findIdByIdInAndNotifyOnPrayedTrue(List.of(event.authorId()));
        if (recipients.isEmpty()) {
            return;
        }
        try {
            String who = event.prayingPrivately() ? "Alguien" : event.prayingUserName();
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", "🙏 Alguien oró por vos",
                    "body", who + " oró por «" + event.title() + "»",
                    "url", "/prayer/" + event.requestId()));
            pushService.sendToUsers(recipients, payload);
        } catch (Exception e) {
            log.warn("No se pudo enviar push de oración id={}: {}",
                    event.requestId(), e.getMessage());
        }
    }
}
