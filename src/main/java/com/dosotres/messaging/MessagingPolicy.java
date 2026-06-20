package com.dosotres.messaging;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.moderation.UserBlockRepository;
import org.springframework.stereotype.Component;

/**
 * Quién puede iniciar/continuar una conversación (ADR-008).
 * Fase A: solo personas con grupo en común y sin bloqueo. Fase B (futura)
 * agregará opt-in + rate limit aquí mismo, sin tocar el schema.
 */
@Component
public class MessagingPolicy {

    private final GroupMemberRepository groupMemberRepository;
    private final UserBlockRepository userBlockRepository;

    public MessagingPolicy(GroupMemberRepository groupMemberRepository,
                           UserBlockRepository userBlockRepository) {
        this.groupMemberRepository = groupMemberRepository;
        this.userBlockRepository = userBlockRepository;
    }

    public void assertCanInitiate(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw new ValidationException("No podés iniciar una conversación con vos mismo");
        }
        if (userBlockRepository.existsBlockBetween(fromUserId, toUserId)) {
            throw new ForbiddenException("No es posible: hay un bloqueo entre ustedes");
        }
        if (!groupMemberRepository.existsSharedGroup(fromUserId, toUserId)) {
            throw new ForbiddenException("Solo podés escribir a personas con quienes compartís un grupo");
        }
    }

    public boolean isBlockedBetween(Long a, Long b) {
        return userBlockRepository.existsBlockBetween(a, b);
    }
}
