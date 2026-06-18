package com.dosotres.push;

import com.dosotres.group.GroupMemberRepository;
import com.dosotres.prayer.PrayerAnsweredEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PrayerAnsweredPushListener {

    private static final Logger log = LoggerFactory.getLogger(PrayerAnsweredPushListener.class);

    private final PushNotificationService pushService;
    private final GroupMemberRepository groupMemberRepository;
    private final ObjectMapper objectMapper;

    public PrayerAnsweredPushListener(PushNotificationService pushService,
                                      GroupMemberRepository groupMemberRepository,
                                      ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.groupMemberRepository = groupMemberRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrayerAnswered(PrayerAnsweredEvent event) {
        if (event.groupId() == null) {
            return; // pedido privado, sin grupo al que notificar
        }
        List<Long> recipients = groupMemberRepository.findUserIdsByGroupId(event.groupId());
        if (recipients.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", "🙏 Oración respondida",
                    "body", "«" + event.title() + "» fue marcado como respondido",
                    "url", "/prayer/" + event.requestId()));
            pushService.sendToUsers(recipients, payload);
        } catch (Exception e) {
            log.warn("No se pudo enviar push de pedido respondido id={}: {}",
                    event.requestId(), e.getMessage());
        }
    }
}
