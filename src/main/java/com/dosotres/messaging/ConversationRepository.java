package com.dosotres.messaging;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** IDs de conversaciones en las que participan ambos usuarios (dedup 1:1). */
    @Query("SELECT p1.conversation.id FROM ConversationParticipant p1, ConversationParticipant p2 "
            + "WHERE p1.conversation.id = p2.conversation.id "
            + "AND p1.user.id = :a AND p2.user.id = :b")
    List<Long> findConversationIdsBetween(@Param("a") Long a, @Param("b") Long b);
}
