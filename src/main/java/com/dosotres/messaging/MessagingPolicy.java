package com.dosotres.messaging;

import com.dosotres.common.exception.ForbiddenException;
import com.dosotres.common.exception.ResourceNotFoundException;
import com.dosotres.common.exception.ValidationException;
import com.dosotres.group.GroupMemberRepository;
import com.dosotres.moderation.UserBlockRepository;
import com.dosotres.user.AgePolicy;
import com.dosotres.user.User;
import com.dosotres.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Quién puede iniciar/continuar una conversación (ADR-008).
 * Fase A: personas con grupo en común y sin bloqueo → ACCEPTED directo.
 * Fase 4: desconocidos, solo si el receptor optó in (allowStrangerMessages);
 * queda PENDING hasta que el receptor acepte, y el iniciador tiene un
 * rate limit diario de solicitudes nuevas.
 */
@Component
public class MessagingPolicy {

    private static final int MAX_STRANGER_REQUESTS_PER_DAY = 10;

    private final GroupMemberRepository groupMemberRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final Clock clock;

    public MessagingPolicy(GroupMemberRepository groupMemberRepository,
                           UserBlockRepository userBlockRepository,
                           UserRepository userRepository,
                           ConversationRepository conversationRepository,
                           Clock clock) {
        this.groupMemberRepository = groupMemberRepository;
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.clock = clock;
    }

    /** Valida si puede iniciar y devuelve el estado con el que debe crearse la conversación. */
    public ConversationState assertCanInitiate(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw new ValidationException("No podés iniciar una conversación con vos mismo");
        }
        if (userBlockRepository.existsBlockBetween(fromUserId, toUserId)) {
            throw new ForbiddenException("No es posible: hay un bloqueo entre ustedes");
        }
        if (groupMemberRepository.existsSharedGroup(fromUserId, toUserId)) {
            return ConversationState.ACCEPTED;
        }

        User toUser = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", toUserId));
        if (!toUser.isAllowStrangerMessages()) {
            throw new ForbiddenException("Esta persona no recibe mensajes de desconocidos");
        }

        Instant cutoff = clock.instant().minus(Duration.ofHours(24));
        long recentRequests = conversationRepository
                .countByInitiatedByIdAndStateAndCreatedAtAfter(fromUserId, ConversationState.PENDING, cutoff);
        if (recentRequests >= MAX_STRANGER_REQUESTS_PER_DAY) {
            throw new ForbiddenException("Alcanzaste el límite de solicitudes nuevas por hoy");
        }

        return ConversationState.PENDING;
    }

    /**
     * Valida una solicitud de vínculo originada en el muro (Fase 5). A diferencia del
     * mensaje a desconocido genérico, no exige opt-in del receptor (el contexto es haber
     * orado por su pedido), pero gatea a que AMBOS sean adultos confirmados (≥18) y aplica
     * el mismo rate limit diario. Siempre PENDING (la identidad se revela al aceptar).
     */
    public void assertCanRequestLink(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw new ValidationException("No podés solicitar un vínculo con vos mismo");
        }
        if (userBlockRepository.existsBlockBetween(fromUserId, toUserId)) {
            throw new ForbiddenException("No es posible: hay un bloqueo entre ustedes");
        }

        User from = userRepository.findById(fromUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", fromUserId));
        User to = userRepository.findById(toUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", toUserId));
        if (!AgePolicy.isAdult(from.getDateOfBirth(), clock)) {
            throw new ForbiddenException("Para conectar con personas fuera de tus grupos necesitás declarar tu fecha de nacimiento y ser mayor de 18");
        }
        if (!AgePolicy.isAdult(to.getDateOfBirth(), clock)) {
            throw new ForbiddenException("No es posible conectar con esta persona");
        }

        Instant cutoff = clock.instant().minus(Duration.ofHours(24));
        long recentRequests = conversationRepository
                .countByInitiatedByIdAndStateAndCreatedAtAfter(fromUserId, ConversationState.PENDING, cutoff);
        if (recentRequests >= MAX_STRANGER_REQUESTS_PER_DAY) {
            throw new ForbiddenException("Alcanzaste el límite de solicitudes nuevas por hoy");
        }
    }

    public boolean isBlockedBetween(Long a, Long b) {
        return userBlockRepository.existsBlockBetween(a, b);
    }
}
