-- Muro público de pedidos (M.2, ADR-007). Entidad separada: sin group_id,
-- sin cronómetro. Oración de un toque (contador idempotente).
CREATE TABLE public_prayer_requests (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    author_id         BIGINT       NOT NULL,
    title             VARCHAR(150) NOT NULL,
    body              TEXT         NULL,
    is_anonymous      BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    moderation_status VARCHAR(20)  NOT NULL DEFAULT 'VISIBLE',
    pray_count        INT          NOT NULL DEFAULT 0,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at       TIMESTAMP    NULL,
    CONSTRAINT fk_ppr_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ppr_moderation_created ON public_prayer_requests(moderation_status, created_at);

-- "Oré por esto" idempotente: unique (request, user); pray_count = COUNT.
CREATE TABLE public_prayers (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_request_id BIGINT    NOT NULL,
    user_id           BIGINT    NOT NULL,
    prayed_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_public_prayer UNIQUE (public_request_id, user_id),
    CONSTRAINT fk_pp_request FOREIGN KEY (public_request_id) REFERENCES public_prayer_requests(id) ON DELETE CASCADE,
    CONSTRAINT fk_pp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_pp_request ON public_prayers(public_request_id);
