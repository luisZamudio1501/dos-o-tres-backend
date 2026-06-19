-- Rol de moderador global (ADR-006). Ortogonal al GroupRole por-grupo.
ALTER TABLE users ADD COLUMN global_role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- Reportes polimórficos sobre contenido público / mensajes / usuarios (ADR-009).
CREATE TABLE reports (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    reporter_id BIGINT       NOT NULL,
    target_type VARCHAR(20)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(500) NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by BIGINT       NULL,
    resolved_at TIMESTAMP    NULL,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_report_resolver FOREIGN KEY (resolved_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_reports_status ON reports(status);

-- Bloqueos usuario→usuario (ADR-009). Cimiento para mensajería.
CREATE TABLE user_blocks (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    blocker_id BIGINT    NOT NULL,
    blocked_id BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_block UNIQUE (blocker_id, blocked_id),
    CONSTRAINT fk_block_blocker FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_blocked FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);
