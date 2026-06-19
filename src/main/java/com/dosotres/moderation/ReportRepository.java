package com.dosotres.moderation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** Cola de moderación: reportes de un estado, más antiguos primero. */
    List<Report> findByStatusOrderByCreatedAtAsc(ReportStatus status);
}
