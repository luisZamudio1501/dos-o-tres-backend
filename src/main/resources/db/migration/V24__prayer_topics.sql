-- Fase 8 (ADR-014): temas de oración personales (recurrentes, no se responden).
CREATE TABLE prayer_topics (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id    BIGINT       NOT NULL,
    name             VARCHAR(100) NOT NULL,
    reminder_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    reminder_time    TIME         NULL,
    timezone         VARCHAR(50)  NOT NULL,
    last_reminded_on DATE         NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_topic_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_topic_owner ON prayer_topics(owner_user_id, created_at);
CREATE INDEX idx_topic_reminder ON prayer_topics(reminder_enabled, reminder_time);

-- Siembra perezosa del catálogo default, una sola vez por usuario (cubre nuevos
-- y existentes sin backfill; respeta borrados posteriores).
ALTER TABLE users ADD COLUMN prayer_topics_seeded BOOLEAN NOT NULL DEFAULT FALSE;
