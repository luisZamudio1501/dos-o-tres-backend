package com.dosotres.messaging;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    /** No leídos: mensajes posteriores a mi última lectura que no envié yo. */
    long countByConversationIdAndCreatedAtAfterAndSenderIdNot(Long conversationId, Instant after, Long senderId);
}
