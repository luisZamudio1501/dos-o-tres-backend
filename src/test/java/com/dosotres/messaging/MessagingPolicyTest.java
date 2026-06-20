package com.dosotres.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.moderation.UserBlockRepository;
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

    private MessagingPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new MessagingPolicy(groupMemberRepository, userBlockRepository);
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
    void assertCanInitiate_noSharedGroup_throwsForbidden() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(groupMemberRepository.existsSharedGroup(1L, 2L)).thenReturn(false);

        assertThatThrownBy(() -> policy.assertCanInitiate(1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assertCanInitiate_sharedGroupNoBlock_passes() {
        when(userBlockRepository.existsBlockBetween(1L, 2L)).thenReturn(false);
        when(groupMemberRepository.existsSharedGroup(1L, 2L)).thenReturn(true);

        assertThatCode(() -> policy.assertCanInitiate(1L, 2L)).doesNotThrowAnyException();
    }
}
