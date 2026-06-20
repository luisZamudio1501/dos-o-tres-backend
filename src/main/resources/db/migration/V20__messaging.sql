-- Mensajería 1:1 (M.5, ADR-008). Schema genérico: el estado PENDING/ACCEPTED y
-- conversation_participant permiten sumar desconocidos (Fase B) y grupal sin
-- migración nueva. Fase A: solo grupos en común, auto-ACCEPTED.
CREATE TABLE conversations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    state           VARCHAR(20) NOT NULL DEFAULT 'ACCEPTED',
    initiated_by    BIGINT      NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_message_at TIMESTAMP   NULL,
    CONSTRAINT fk_conv_initiator FOREIGN KEY (initiated_by) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversation_participants (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT    NOT NULL,
    user_id         BIGINT    NOT NULL,
    last_read_at    TIMESTAMP NULL,
    CONSTRAINT uq_conv_participant UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_cp_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cp_user ON conversation_participants(user_id);

CREATE TABLE messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT    NOT NULL,
    sender_id       BIGINT    NOT NULL,
    body            TEXT      NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_msg_conv_created ON messages(conversation_id, created_at);
