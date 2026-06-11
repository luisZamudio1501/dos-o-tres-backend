package com.dosotres.activity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {

    Page<ActivityEvent> findByGroupIdOrderByCreatedAtDesc(Long groupId, Pageable pageable);
}
