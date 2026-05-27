CREATE TABLE prayer_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status ENUM('PENDING', 'ANSWERED') NOT NULL DEFAULT 'PENDING',
    answered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_pr_group FOREIGN KEY (group_id) REFERENCES `groups`(id),
    CONSTRAINT fk_pr_author FOREIGN KEY (author_id) REFERENCES users(id),
    INDEX idx_pr_group_status (group_id, status),
    INDEX idx_pr_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
