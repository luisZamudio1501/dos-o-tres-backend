-- V3 hito "Módulo de Compromiso": metas de oración (minutos diarios por un período).
-- PrayerGoal (= meta) es distinta de prayer_commitments (= "oré por este pedido hoy").
-- group_id NULL = meta personal (etapa 1); las grupales llegan después.
CREATE TABLE prayer_goals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    group_id BIGINT NULL,
    daily_minutes INT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    mode VARCHAR(20) NOT NULL,
    scheduled_time TIME NULL,
    timezone VARCHAR(50) NOT NULL,
    last_reminded_on DATE NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_owner FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_group FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_goal_owner ON prayer_goals(owner_user_id);
-- Soporta el job de recordatorios (filtra SCHEDULED activas por período).
CREATE INDEX idx_goal_mode_period ON prayer_goals(mode, period_start, period_end);
