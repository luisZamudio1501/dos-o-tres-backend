package com.dosotres.publicwall;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Archiva diariamente los pedidos públicos activos sin actividad en la ventana (ADR-010). */
@Component
@ConditionalOnProperty(name = "app.public-wall.archive.enabled", havingValue = "true", matchIfMissing = true)
public class PublicWallArchiveJob {

    private static final Logger log = LoggerFactory.getLogger(PublicWallArchiveJob.class);

    private final PublicWallService publicWallService;

    public PublicWallArchiveJob(PublicWallService publicWallService) {
        this.publicWallService = publicWallService;
    }

    @Scheduled(cron = "${app.public-wall.archive.cron}")
    public void run() {
        int archived = publicWallService.archiveStale();
        if (archived > 0) {
            log.info("Auto-archivados {} pedidos públicos por inactividad", archived);
        }
    }
}
