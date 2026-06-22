-- Fase 5 (ADR-011): vínculo por orar (Etapa 1) + edad mínima 18.

-- (a) Visibilidad de la oración pública (default anónima).
ALTER TABLE public_prayers ADD COLUMN visible BOOLEAN NOT NULL DEFAULT FALSE;

-- (b) Edad declarada para el gate de menores. Nullable: cuentas previas sin declarar.
ALTER TABLE users ADD COLUMN date_of_birth DATE NULL;

-- (c) Conversación originada en el muro: contexto + anti-spam + masking del iniciador.
ALTER TABLE conversations
    ADD COLUMN origin_public_request_id BIGINT       NULL,
    ADD COLUMN origin_context           VARCHAR(150) NULL,
    ADD CONSTRAINT fk_conv_origin_ppr FOREIGN KEY (origin_public_request_id)
        REFERENCES public_prayer_requests(id) ON DELETE SET NULL;

CREATE INDEX idx_conv_origin ON conversations(origin_public_request_id, initiated_by);
