package com.dosotres.push;

import com.dosotres.group.GroupMemberRepository;
import com.dosotres.prayer.PrayerRequestCreatedEvent;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PrayerRequestCreatedPushListener {

    private static final Logger log = LoggerFactory.getLogger(PrayerRequestCreatedPushListener.class);

    private final PushNotificationService pushService;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PrayerRequestCreatedPushListener(PushNotificationService pushService,
                                            GroupMemberRepository groupMemberRepository,
                                            UserRepository userRepository,
                                            ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrayerRequestCreated(PrayerRequestCreatedEvent event) {
        List<Long> members = groupMemberRepository.findUserIdsByGroupId(event.groupId()).stream()
                .filter(id -> !id.equals(event.authorId())) // el autor no se avisa a sí mismo
                .collect(Collectors.toList());
        if (members.isEmpty()) {
            return;
        }
        List<Long> recipients = userRepository.findIdByIdInAndNotifyOnRequestCreatedTrue(members);
        if (recipients.isEmpty()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", "🙏 Nuevo pedido de oración",
                    "body", "«" + event.title() + "»",
                    "url", "/prayer/" + event.requestId()));
            pushService.sendToUsers(recipients, payload);
        } catch (Exception e) {
            log.warn("No se pudo enviar push de pedido creado id={}: {}",
                    event.requestId(), e.getMessage());
        }
    }
}
