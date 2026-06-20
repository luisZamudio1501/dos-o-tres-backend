-- Mensajería con desconocidos (Fase 4, ADR-008). El receptor decide si
-- acepta mensajes de gente sin grupo en común; default OFF.
ALTER TABLE users ADD COLUMN allow_stranger_messages BOOLEAN NOT NULL DEFAULT FALSE;
