package com.dosotres.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dispara el resumen semanal de administración (lunes por la mañana). Toda la
 * lógica vive en {@link AdminWeeklyReportService}. Desactivable por propiedad.
 */
@Component
@ConditionalOnProperty(name = "app.admin.weekly-report.enabled", havingValue = "true", matchIfMissing = true)
public class AdminWeeklyReportJob {

    private final AdminWeeklyReportService reportService;

    public AdminWeeklyReportJob(AdminWeeklyReportService reportService) {
        this.reportService = reportService;
    }

    @Scheduled(cron = "${app.admin.weekly-report.cron}", zone = "America/Argentina/Buenos_Aires")
    public void run() {
        reportService.sendWeeklyReport();
    }
}
