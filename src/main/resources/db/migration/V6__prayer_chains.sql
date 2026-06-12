-- Sprint 4: cadenas de oración con franjas horarias.
-- Reglas: cuaderno/REGLAS-NEGOCIO.md Fase C.
--   C1: granularidad fija por cadena (15/30/60 min, decisión 2026-06-11).
--   C2: sin límite de usuarios por franja (sin bloqueos en DB).
--   C3: activación automática por fecha — el estado se DERIVA de date_from/date_to,
--       no se almacena.
--   C4: la suscripción a una franja cubre todos los días de la campaña
--       (un solo registro; el "no completado" diario se deriva, no se guarda).
-- Horarios en UTC (daily_start_minutes = minutos desde 00:00 UTC); la conversión
-- a hora local es responsabilidad del cliente.

CREATE TABLE prayer_chains (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    slot_minutes INT NOT NULL,
    daily_start_minutes INT NOT NULL,
    duration_minutes INT NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_chain_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    CONSTRAINT fk_chain_creator FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_chain_group_dates (group_id, date_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chain_commitments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chain_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    slot_index INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cc_chain FOREIGN KEY (chain_id) REFERENCES prayer_chains(id),
    CONSTRAINT fk_cc_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_chain_user_slot UNIQUE (chain_id, user_id, slot_index),
    INDEX idx_cc_chain (chain_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
