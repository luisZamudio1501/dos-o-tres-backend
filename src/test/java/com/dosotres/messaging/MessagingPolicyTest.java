package com.dosotres.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.moderation.UserBlockRepository;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessagingPolicyTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConversationRepository conversationRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T12:00:00Z"), ZoneOffset.UTC);

    private MessagingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MessagingPolicy(groupMemberRepository, userBlockRepository, userRepository,
                conversationRepository, clock);
    }

    private User strangerWithOptIn(boolean optIn) {
        User user = new User();
        user.setId(2L);
        user.setAllowStrangerMessages(optIn);
        return user;
    }

    @Test
    void assertCanInitiate_self_throwsValidation() {
        assertThatThrownBy(() -> policy.assertCanInitiate(1L, 1L))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void assertCanInitiate_blocked_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> policy.assertCanInitiate(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanInitiate_sharedGroupNoBlock_returnsAccepted() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(groupMemberRepository.existsSharedGroup(1L, 2L)).thenReturn(true);

        assertThat(policy.assertCanInitiate(1L, 2L)).isEqualTo(ConversationState.ACCEPTED);
    }

    @Test
    void assertCanInitiate_strangerWithoutOptIn_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(groupMemberRepository.existsSharedGroup(1L, 2L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(strangerWithOptIn(false)));

        assertThatThrownBy(() -> policy.assertCanInitiate(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanInitiate_strangerWithOptIn_returnsPending() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(groupMemberRepository.existsSharedGroup(1L, 2L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(strangerWithOptIn(true)));
        when(conversationRepository.countByInitiatedByIdAndStateAndCreatedAtAfter(
                eq(1L), eq(ConversationState.PENDING), any())).thenReturn(0L);

        assertThat(policy.assertCanInitiate(1L, 2L)).isEqualTo(ConversationState.PENDING);
    }

    @Test
    void assertCanInitiate_strangerOverRateLimit_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(groupMemberRepository.existsSharedGroup(1L, 2L)).thenReturn(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(strangerWithOptIn(true)));
        when(conversationRepository.countByInitiatedByIdAndStateAndCreatedAtAfter(
                eq(1L), eq(ConversationState.PENDING), any())).thenReturn(10L);

        assertThatThrownBy(() -> policy.assertCanInitiate(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    private User userWithDob(Long id, LocalDate dob) {
        User u = new User();
        u.setId(id);
        u.setDateOfBirth(dob);
        return u;
    }

    // Reloj fijo en 2026-06-20: adulto = nacido en o antes de 2008-06-20.
    @Test
    void assertCanRequestLink_bothAdults_ok() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithDob(1L, LocalDate.of(1990, 1, 1))));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithDob(2L, LocalDate.of(2000, 1, 1))));
        when(conversationRepository.countByInitiatedByIdAndStateAndCreatedAtAfter(
                eq(1L), eq(ConversationState.PENDING), any())).thenReturn(0L);

        policy.assertCanRequestLink(1L, 2L); // no lanza
    }

    @Test
    void assertCanRequestLink_initiatorMinor_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithDob(1L, LocalDate.of(2015, 1, 1))));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithDob(2L, LocalDate.of(2000, 1, 1))));

        assertThatThrownBy(() -> policy.assertCanRequestLink(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanRequestLink_recipientNoDob_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithDob(1L, LocalDate.of(1990, 1, 1))));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithDob(2L, null)));

        assertThatThrownBy(() -> policy.assertCanRequestLink(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanRequestLink_blocked_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> policy.assertCanRequestLink(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanRequestLink_overRateLimit_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userWithDob(1L, LocalDate.of(1990, 1, 1))));
        when(userRepository.findById(2L)).thenReturn(Optional.of(userWithDob(2L, LocalDate.of(2000, 1, 1))));
        when(conversationRepository.countByInitiatedByIdAndStateAndCreatedAtAfter(
                eq(1L), eq(ConversationState.PENDING), any())).thenReturn(10L);

        assertThatThrownBy(() -> policy.assertCanRequestLink(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }
}
