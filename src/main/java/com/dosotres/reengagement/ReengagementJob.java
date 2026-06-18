package com.dosotres.reengagement;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara diariamente el re-engagement; toda la decisión vive en ReengagementService. */
@Component
@ConditionalOnProperty(name = "app.reengagement.enabled", havingValue = "true", matchIfMissing = true)
public class ReengagementJob {

    private final ReengagementService reengagementService;

    public ReengagementJob(ReengagementService reengagementService) {
        this.reengagementService = reengagementService;
    }

    @Scheduled(cron = "${app.reengagement.cron}")
    public void run() {
        reengagementService.sendDueReengagements();
    }
}
