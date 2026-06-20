package com.dosotres.messaging;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.messaging.dto.ConversationSummaryResponse;
import com.dosotres.messaging.dto.MessageResponse;
import com.dosotres.push.PushNotificationService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessagingPolicy messagingPolicy;
    private final PushNotificationService pushService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MessagingService(ConversationRepository conversationRepository,
                            ConversationParticipantRepository participantRepository,
                            MessageRepository messageRepository,
                            UserRepository userRepository,
                            MessagingPolicy messagingPolicy,
                            PushNotificationService pushService,
                            ObjectMapper objectMapper,
                            Clock clock) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messagingPolicy = messagingPolicy;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Crea o recupera la conversación 1:1 con otro usuario (Fase A). */
    public ConversationSummaryResponse startConversation(Long userId, Long otherUserId) {
        messagingPolicy.assertCanInitiate(userId, otherUserId);

        List<Long> existing = conversationRepository.findConversationIdsBetween(userId, otherUserId);
        if (!existing.isEmpty()) {
            ConversationParticipant me = participantRepository
                    .findByConversationIdAndUserId(existing.get(0), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("ConversationParticipant",
                            "conversation+user", existing.get(0) + "+" + userId));
            return toSummary(me);
        }

        User initiator = findUser(userId);
        User other = findUser(otherUserId);

        Conversation conversation = new Conversation();
        conversation.setState(ConversationState.ACCEPTED);
        conversation.setInitiatedBy(initiator);
        conversationRepository.save(conversation);

        ConversationParticipant myParticipation = participant(conversation, initiator);
        participantRepository.save(myParticipation);
        participantRepository.save(participant(conversation, other));

        return toSummary(myParticipation);
    }

    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(Long userId) {
        return participantRepository.findMyConversations(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long userId, Long conversationId, Pageable pageable) {
        requireParticipant(conversationId, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable)
                .map(this::toMessageResponse);
    }

    public MessageResponse sendMessage(Long userId, Long conversationId, String body) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));
        requireParticipant(conversationId, userId);

        ConversationParticipant other = participantRepository
                .findFirstByConversationIdAndUserIdNot(conversationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "otherParticipant", conversationId));

        Long otherId = other.getUser().getId();
        if (messagingPolicy.isBlockedBetween(userId, otherId)) {
            throw new ForbiddenException("No es posible enviar el mensaje: hay un bloqueo entre ustedes");
        }

        User sender = findUser(userId);
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setBody(body.trim());
        messageRepository.save(message);

        conversation.setLastMessageAt(clock.instant());

        pushNewMessage(otherId, sender.getDisplayName(), message.getBody(), conversationId);

        return toMessageResponse(message);
    }

    public void markRead(Long userId, Long conversationId) {
        ConversationParticipant me = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ForbiddenException("No sos parte de esta conversación"));
        me.setLastReadAt(clock.instant());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void requireParticipant(Long conversationId, Long userId) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new ForbiddenException("No sos parte de esta conversación");
        }
    }

    private ConversationParticipant participant(Conversation conversation, User user) {
        ConversationParticipant p = new ConversationParticipant();
        p.setConversation(conversation);
        p.setUser(user);
        return p;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private void pushNewMessage(Long recipientId, String senderName, String body, Long conversationId) {
        try {
            String preview = body.length() > 80 ? body.substring(0, 80) + "…" : body;
            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", senderName,
                    "body", preview,
                    "url", "/mensajes/" + conversationId));
            pushService.sendToUsers(List.of(recipientId), payload);
        } catch (Exception e) {
            log.warn("New-message push failed conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    private ConversationSummaryResponse toSummary(ConversationParticipant me) {
        Conversation c = me.getConversation();
        Long myId = me.getUser().getId();

        ConversationParticipant other = participantRepository
                .findFirstByConversationIdAndUserIdNot(c.getId(), myId).orElse(null);
        Message last = messageRepository
                .findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).orElse(null);
        Instant since = me.getLastReadAt() != null ? me.getLastReadAt() : Instant.EPOCH;
        long unread = messageRepository
                .countByConversationIdAndCreatedAtAfterAndSenderIdNot(c.getId(), since, myId);

        return new ConversationSummaryResponse(
                c.getId(),
                other != null ? other.getUser().getId() : null,
                other != null ? other.getUser().getDisplayName() : null,
                last != null ? last.getBody() : null,
                last != null && last.getCreatedAt() != null ? last.getCreatedAt().toString() : null,
                unread);
    }

    private MessageResponse toMessageResponse(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversation().getId(),
                m.getSender().getId(),
                m.getSender().getDisplayName(),
                m.getBody(),
                m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
    }
}
