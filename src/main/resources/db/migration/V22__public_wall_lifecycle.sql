-- Fase 4 (ADR-010): ciclo de vida del pedido público.
-- status (ACTIVE/ANSWERED) y answered_at ya existen desde V19. Se agrega:
--  - testimony: testimonio opcional del autor al marcar respondido.
--  - last_activity_at: marca de actividad (creación / última oración) para archivar por inactividad.
--  - archived_at: archivado ortogonal (sale del feed activo, sigue accesible). Los ANSWERED nunca se archivan.
ALTER TABLE public_prayer_requests
    ADD COLUMN testimony        TEXT      NULL AFTER body,
    ADD COLUMN last_activity_at TIMESTAMP NULL AFTER created_at,
    ADD COLUMN archived_at      TIMESTAMP NULL AFTER answered_at;

-- Backfill: la actividad inicial de los pedidos existentes es su creación.
UPDATE public_prayer_requests SET last_activity_at = created_at WHERE last_activity_at IS NULL;

-- Feed activo: VISIBLE + ACTIVE + no archivado, por fecha.
CREATE INDEX idx_ppr_active_feed ON public_prayer_requests(moderation_status, status, archived_at, created_at);
-- Barrido del job de archivado por inactividad.
CREATE INDEX idx_ppr_archive_sweep ON public_prayer_requests(status, archived_at, last_activity_at);
