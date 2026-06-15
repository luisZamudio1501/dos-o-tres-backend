-- Sprint 5: perfil de congregación (análisis: cuaderno/MODELADO-V2.md §4).
-- Reglas de privacidad:
--   * Todos los campos son OPCIONALES (NULL) — nunca se piden en el registro.
--   * Sin nombre de pastor: es un dato personal de un tercero que no consintió.
--   * Iglesia + ubicación = afiliación religiosa (dato sensible, Ley 25.326 /
--     GDPR art. 9): visible solo dentro de los grupos del usuario, borrable
--     (PATCH con null limpia el campo).
-- country se guarda como código ISO-3166-1 alfa-2; el cliente muestra el nombre.

ALTER TABLE users
    ADD COLUMN country CHAR(2) NULL AFTER locale,
    ADD COLUMN province VARCHAR(100) NULL AFTER country,
    ADD COLUMN city VARCHAR(100) NULL AFTER province,
    ADD COLUMN church_name VARCHAR(150) NULL AFTER city;
