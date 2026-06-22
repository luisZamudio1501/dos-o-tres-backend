package com.dosotres.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.group.GroupService;
import com.dosotres.group.dto.CreateGroupRequest;
import com.dosotres.group.dto.GroupResponse;
import com.dosotres.messaging.dto.ConversationSummaryResponse;
import com.dosotres.messaging.dto.MessageResponse;
import com.dosotres.publicwall.PublicPrayerRequest;
import com.dosotres.push.PushNotificationService;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-19T12:00:00Z"), ZoneId.of("UTC"));

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private MessagingPolicy messagingPolicy;
    @Mock private PushNotificationService pushService;
    @Mock private GroupService groupService;

    private MessagingService service;

    @BeforeEach
    void setUp() {
        service = new MessagingService(conversationRepository, participantRepository, messageRepository,
                userRepository, messagingPolicy, pushService, groupService, new ObjectMapper(), FIXED_CLOCK);
    }

    private User makeUser(Long id, String name) {
        User u = new User();
        u.setId(id);
        u.setDisplayName(name);
        return u;
    }

    private Conversation makeConversation(Long id) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setState(ConversationState.ACCEPTED);
        return c;
    }

    private ConversationParticipant makeParticipant(Conversation c, User u) {
        ConversationParticipant p = new ConversationParticipant();
        p.setConversation(c);
        p.setUser(u);
        return p;
    }

    @Test
    void startConversation_new_createsConversationAndTwoParticipants() {
        User u1 = makeUser(1L, "Luis");
        User u2 = makeUser(2L, "Ana");
        when(messagingPolicy.assertCanInitiate(1L, 2L)).thenReturn(ConversationState.ACCEPTED);
        when(conversationRepository.findConversationIdsBetween(1L, 2L)).thenReturn(List.of());
        when(userRepository.findById(1L)).thenReturn(Optional.of(u1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(u2));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(participantRepository.save(any(ConversationParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(makeConversation(100L), u2)));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        when(messageRepository.countByConversationIdAndCreatedAtAfterAndSenderIdNot(eq(100L), any(), eq(1L)))
                .thenReturn(0L);

        ConversationSummaryResponse res = service.startConversation(1L, 2L);

        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.otherUserId()).isEqualTo(2L);
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    void startConversation_existing_returnsItWithoutCreating() {
        User u1 = makeUser(1L, "Luis");
        User u2 = makeUser(2L, "Ana");
        Conversation c = makeConversation(100L);
        c.setInitiatedBy(u1);
        when(conversationRepository.findConversationIdsBetween(1L, 2L)).thenReturn(List.of(100L));
        when(participantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, u1)));
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, u2)));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        when(messageRepository.countByConversationIdAndCreatedAtAfterAndSenderIdNot(eq(100L), any(), eq(1L)))
                .thenReturn(0L);

        ConversationSummaryResponse res = service.startConversation(1L, 2L);

        assertThat(res.id()).isEqualTo(100L);
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void sendMessage_savesUpdatesLastMessageAndPushes() {
        User sender = makeUser(1L, "Luis");
        User other = makeUser(2L, "Ana");
        Conversation c = makeConversation(100L);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, other)));
        when(messagingPolicy.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse res = service.sendMessage(1L, 100L, "Hola Ana");

        assertThat(res.body()).isEqualTo("Hola Ana");
        assertThat(res.senderId()).isEqualTo(1L);
        assertThat(c.getLastMessageAt()).isEqualTo(FIXED_CLOCK.instant());
        verify(pushService).sendToUsers(eq(List.of(2L)), anyString());
    }

    @Test
    void sendMessage_byNonParticipant_throwsForbidden() {
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(makeConversation(100L)));
        when(participantRepository.existsByConversationIdAndUserId(100L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.sendMessage(9L, 100L, "Hola"))
                .isInstanceOf(ForbiddenException.class);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendMessage_blocked_throwsForbidden() {
        User other = makeUser(2L, "Ana");
        Conversation c = makeConversation(100L);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, other)));
        when(messagingPolicy.isBlockedBetween(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.sendMessage(1L, 100L, "Hola"))
                .isInstanceOf(ForbiddenException.class);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void getMessages_byNonParticipant_throwsForbidden() {
        when(participantRepository.existsByConversationIdAndUserId(100L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.getMessages(9L, 100L, PageRequest.of(0, 30)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markRead_setsLastReadAt() {
        ConversationParticipant me = makeParticipant(makeConversation(100L), makeUser(1L, "Luis"));
        when(participantRepository.findByConversationIdAndUserId(100L, 1L)).thenReturn(Optional.of(me));

        service.markRead(1L, 100L);

        assertThat(me.getLastReadAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    private Conversation makePendingConversation(Long id, User initiatedBy) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setState(ConversationState.PENDING);
        c.setInitiatedBy(initiatedBy);
        return c;
    }

    @Test
    void sendMessage_pendingAndReceiverTries_throwsForbidden() {
        User initiator = makeUser(1L, "Luis");
        User receiver = makeUser(2L, "Ana");
        Conversation c = makePendingConversation(100L, initiator);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.sendMessage(2L, 100L, "Hola"))
                .isInstanceOf(ForbiddenException.class);
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    void sendMessage_pendingAndInitiatorSends_allowed() {
        User initiator = makeUser(1L, "Luis");
        User receiver = makeUser(2L, "Ana");
        Conversation c = makePendingConversation(100L, initiator);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, receiver)));
        when(messagingPolicy.isBlockedBetween(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(initiator));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse res = service.sendMessage(1L, 100L, "Hola Ana");

        assertThat(res.body()).isEqualTo("Hola Ana");
    }

    @Test
    void acceptConversation_byReceiver_setsAccepted() {
        User initiator = makeUser(1L, "Luis");
        Conversation c = makePendingConversation(100L, initiator);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);

        service.acceptConversation(2L, 100L);

        assertThat(c.getState()).isEqualTo(ConversationState.ACCEPTED);
    }

    @Test
    void acceptConversation_byInitiator_throwsForbidden() {
        User initiator = makeUser(1L, "Luis");
        Conversation c = makePendingConversation(100L, initiator);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.acceptConversation(1L, 100L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void declineConversation_byReceiver_setsDeclined() {
        User initiator = makeUser(1L, "Luis");
        Conversation c = makePendingConversation(100L, initiator);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 2L)).thenReturn(true);

        service.declineConversation(2L, 100L);

        assertThat(c.getState()).isEqualTo(ConversationState.DECLINED);
    }

    private PublicPrayerRequest makeOrigin(Long id, String title, User author) {
        PublicPrayerRequest r = new PublicPrayerRequest();
        r.setId(id);
        r.setTitle(title);
        r.setAuthor(author);
        return r;
    }

    @Test
    void startLinkRequest_new_createsPendingWithOrigin() {
        User orante = makeUser(1L, "Luis");
        User author = makeUser(2L, "Ana");
        PublicPrayerRequest origin = makeOrigin(10L, "Por mi familia", author);
        when(conversationRepository.findByOriginPublicRequestIdAndInitiatedById(10L, 1L))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(orante));
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> {
            Conversation c = inv.getArgument(0);
            c.setId(100L);
            return c;
        });
        when(participantRepository.save(any(ConversationParticipant.class))).thenAnswer(inv -> inv.getArgument(0));
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(makeConversation(100L), author)));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        when(messageRepository.countByConversationIdAndCreatedAtAfterAndSenderIdNot(eq(100L), any(), eq(1L)))
                .thenReturn(0L);

        ConversationSummaryResponse res = service.startLinkRequest(1L, 2L, origin);

        verify(messagingPolicy).assertCanRequestLink(1L, 2L);
        assertThat(res.id()).isEqualTo(100L);
        assertThat(res.state()).isEqualTo("PENDING");
        assertThat(res.iAmInitiator()).isTrue();
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    void startLinkRequest_existing_returnsItIdempotent() {
        User orante = makeUser(1L, "Luis");
        User author = makeUser(2L, "Ana");
        PublicPrayerRequest origin = makeOrigin(10L, "Por mi familia", author);
        Conversation c = makePendingConversation(100L, orante);
        c.setOriginPublicRequest(origin);
        c.setOriginContext("Por mi familia");
        when(conversationRepository.findByOriginPublicRequestIdAndInitiatedById(10L, 1L))
                .thenReturn(Optional.of(c));
        when(participantRepository.findByConversationIdAndUserId(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, orante)));
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, author)));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        when(messageRepository.countByConversationIdAndCreatedAtAfterAndSenderIdNot(eq(100L), any(), eq(1L)))
                .thenReturn(0L);

        ConversationSummaryResponse res = service.startLinkRequest(1L, 2L, origin);

        assertThat(res.id()).isEqualTo(100L);
        verify(conversationRepository, never()).save(any(Conversation.class));
    }

    @Test
    void listConversations_pendingLinkRequest_masksInitiatorForRecipient() {
        User orante = makeUser(1L, "Luis");
        User author = makeUser(2L, "Ana");
        PublicPrayerRequest origin = makeOrigin(10L, "Por mi familia", author);
        Conversation c = makePendingConversation(100L, orante);
        c.setOriginPublicRequest(origin);
        c.setOriginContext("Por mi familia");
        // El receptor (autor, id 2) mira su bandeja.
        when(participantRepository.findMyConversations(2L))
                .thenReturn(List.of(makeParticipant(c, author)));
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 2L))
                .thenReturn(Optional.of(makeParticipant(c, orante)));
        when(messageRepository.findFirstByConversationIdOrderByCreatedAtDesc(100L)).thenReturn(Optional.empty());
        when(messageRepository.countByConversationIdAndCreatedAtAfterAndSenderIdNot(eq(100L), any(), eq(2L)))
                .thenReturn(0L);

        List<ConversationSummaryResponse> res = service.listConversations(2L);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).otherUserId()).isNull();
        assertThat(res.get(0).otherUserName()).isNull();
        assertThat(res.get(0).originTitle()).isEqualTo("Por mi familia");
        assertThat(res.get(0).iAmInitiator()).isFalse();
    }

    private GroupResponse makeGroupResponse(Long id, int memberCount, String role) {
        return new GroupResponse(id, "Mi grupo", null, null, null, "invite-code", memberCount, role,
                "2026-06-19T12:00:00Z");
    }

    @Test
    void createGroupFromConversation_accepted_createsGroupWithBothMembers() {
        User initiator = makeUser(1L, "Luis");
        User other = makeUser(2L, "Ana");
        Conversation c = makeConversation(100L);
        c.setState(ConversationState.ACCEPTED);
        CreateGroupRequest req = new CreateGroupRequest("Mi grupo", null);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);
        when(participantRepository.findFirstByConversationIdAndUserIdNot(100L, 1L))
                .thenReturn(Optional.of(makeParticipant(c, other)));
        when(groupService.create(req, 1L)).thenReturn(makeGroupResponse(50L, 1, "ADMIN"));

        GroupResponse res = service.createGroupFromConversation(1L, 100L, req);

        verify(groupService).addMemberDirect(50L, 2L);
        assertThat(res.id()).isEqualTo(50L);
        assertThat(res.memberCount()).isEqualTo(2);
        assertThat(res.role()).isEqualTo("ADMIN");
    }

    @Test
    void createGroupFromConversation_notAccepted_throwsForbidden() {
        User initiator = makeUser(1L, "Luis");
        Conversation c = makePendingConversation(100L, initiator);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.createGroupFromConversation(1L, 100L, new CreateGroupRequest("X", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(groupService, never()).create(any(), any());
    }

    @Test
    void createGroupFromConversation_byNonParticipant_throwsForbidden() {
        Conversation c = makeConversation(100L);
        c.setState(ConversationState.ACCEPTED);
        when(conversationRepository.findById(100L)).thenReturn(Optional.of(c));
        when(participantRepository.existsByConversationIdAndUserId(100L, 9L)).thenReturn(false);

        assertThatThrownBy(() -> service.createGroupFromConversation(9L, 100L, new CreateGroupRequest("X", null)))
                .isInstanceOf(ForbiddenException.class);
        verify(groupService, never()).create(any(), any());
    }
}
