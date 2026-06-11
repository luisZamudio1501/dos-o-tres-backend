-- Sprint 2: pedidos seleccionados por sesión de oración.
-- Reglas: cuaderno/REGLAS-NEGOCIO.md (B5 selección previa bloqueada, B7 cumplimiento granular).

CREATE TABLE session_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    prayer_request_id BIGINT NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sr_session FOREIGN KEY (session_id) REFERENCES prayer_sessions(id),
    CONSTRAINT fk_sr_request FOREIGN KEY (prayer_request_id) REFERENCES prayer_requests(id),
    CONSTRAINT uk_session_request UNIQUE (session_id, prayer_request_id),
    INDEX idx_sr_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
