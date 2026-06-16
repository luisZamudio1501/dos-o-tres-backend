-- V3 Hito #3: redefinición de la semántica de estados de los pedidos.
--   ACTIVE   = nadie oró todavía (nuevo)
--   ON_HOLD  = ya oró ≥1 persona (en espera; sigue siendo orable)
--   ANSWERED = respondido (sin cambios)
-- Antes ON_HOLD era un estado manual ("pausar") o forzado por la regla D4.
-- Recalculamos el estado de todo pedido no respondido según los cumplimientos reales.

-- Pedidos con al menos un cumplimiento real → ON_HOLD (ya oró alguien).
UPDATE prayer_requests pr
SET pr.status = 'ON_HOLD'
WHERE pr.status <> 'ANSWERED'
  AND EXISTS (
      SELECT 1 FROM prayer_commitments pc
      WHERE pc.prayer_request_id = pr.id AND pc.fulfilled = TRUE
  );

-- Pedidos sin cumplimientos reales → ACTIVE (incluye los pausados-manual / D4).
UPDATE prayer_requests pr
SET pr.status = 'ACTIVE'
WHERE pr.status <> 'ANSWERED'
  AND NOT EXISTS (
      SELECT 1 FROM prayer_commitments pc
      WHERE pc.prayer_request_id = pr.id AND pc.fulfilled = TRUE
  );
