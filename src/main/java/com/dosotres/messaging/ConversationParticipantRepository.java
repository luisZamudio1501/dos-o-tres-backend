package com.dosotres.messaging;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findFirstByConversationIdAndUserIdNot(Long conversationId, Long userId);

    /** Conversaciones del usuario, más reciente actividad primero. */
    @Query("SELECT p FROM ConversationParticipant p WHERE p.user.id = :userId "
            + "ORDER BY p.conversation.lastMessageAt DESC")
    List<ConversationParticipant> findMyConversations(@Param("userId") Long userId);
}
