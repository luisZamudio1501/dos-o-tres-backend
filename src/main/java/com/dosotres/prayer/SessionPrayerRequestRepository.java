package com.dosotres.prayer;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionPrayerRequestRepository extends JpaRepository<SessionPrayerRequest, Long> {

    List<SessionPrayerRequest> findBySessionId(String sessionId);

    void deleteByPrayerRequestId(Long prayerRequestId);
}
