CREATE TABLE prayer_sessions (
    id CHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT,
    started_at TIMESTAMP NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    status ENUM('ACTIVE', 'COMPLETED', 'ABANDONED') NOT NULL DEFAULT 'ACTIVE',
    last_sync_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ps_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ps_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    INDEX idx_ps_user_status (user_id, status),
    INDEX idx_ps_last_sync (status, last_sync_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE prayer_commitments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prayer_request_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    committed_date DATE NOT NULL,
    fulfilled BOOLEAN NOT NULL DEFAULT FALSE,
    fulfilled_at TIMESTAMP NULL,
    session_id CHAR(36),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_request FOREIGN KEY (prayer_request_id) REFERENCES prayer_requests(id),
    CONSTRAINT fk_pc_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_pc_session FOREIGN KEY (session_id) REFERENCES prayer_sessions(id),
    CONSTRAINT uk_commitment UNIQUE (prayer_request_id, user_id, committed_date),
    INDEX idx_pc_user_date (user_id, committed_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
