-- Plan Panel de Administración (Fase 1): medida de actividad "abrió la app".
-- Se actualiza desde JwtAuthFilter con throttle (>1h) para no escribir en cada
-- request. NULL hasta el primer login posterior al deploy: no hay histórico
-- previo de logins (las tendencias por created_at sí son retroactivas).
ALTER TABLE users
    ADD COLUMN last_seen_at TIMESTAMP NULL AFTER updated_at;
