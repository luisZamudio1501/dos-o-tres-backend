-- Sprint 1: estados ACTIVE/ON_HOLD/ANSWERED, testimonio y feed de actividad.
-- Reglas: cuaderno/REGLAS-NEGOCIO.md (estados F/G, feed B8-B11, privacidad F3).

-- 1. Ampliar enum con valores viejos y nuevos conviviendo
ALTER TABLE prayer_requests
    MODIFY COLUMN status ENUM('PENDING', 'ACTIVE', 'ON_HOLD', 'ANSWERED') NOT NULL DEFAULT 'ACTIVE';

-- 2. Migrar datos: PENDING pasa a llamarse ACTIVE
UPDATE prayer_requests SET status = 'ACTIVE' WHERE status = 'PENDING';

-- 3. Quitar el valor viejo del enum
ALTER TABLE prayer_requests
    MODIFY COLUMN status ENUM('ACTIVE', 'ON_HOLD', 'ANSWERED') NOT NULL DEFAULT 'ACTIVE';

-- 4. Testimonio: texto plano, solo del autor, al pasar a ANSWERED
ALTER TABLE prayer_requests
    ADD COLUMN testimony TEXT NULL AFTER answered_at;

-- 5. Cumplimiento privado: el user_id se conserva siempre; el DTO enmascara
ALTER TABLE prayer_commitments
    ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE AFTER fulfilled_at;

-- 6. Feed de actividad (solo eventos automáticos del sistema; estrategia pull)
--    type es VARCHAR (no ENUM) para sumar tipos futuros (cadenas, miembros) sin migración.
CREATE TABLE activity_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    type VARCHAR(40) NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    payload JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ae_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    CONSTRAINT fk_ae_actor FOREIGN KEY (actor_id) REFERENCES users(id),
    INDEX idx_ae_group_created (group_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
