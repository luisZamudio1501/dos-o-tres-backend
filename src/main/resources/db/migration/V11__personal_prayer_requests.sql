-- V3 hito "Espacio personal": pedidos privados que pueden compartirse a un grupo.
--   visibility PRIVATE = sin grupo, solo el autor (dueño).
--   visibility GROUP   = compartido con un grupo (group_id seteado).
-- Los pedidos existentes quedan como GROUP (sin cambios de comportamiento).

-- group_id pasa a ser opcional (los privados no tienen grupo).
ALTER TABLE prayer_requests
    MODIFY COLUMN group_id BIGINT NULL;

-- Nueva columna de visibilidad; default GROUP para preservar lo existente.
ALTER TABLE prayer_requests
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'GROUP' AFTER group_id;
