-- V8: Personalización visual de grupos.
-- Habilita color de fondo y emoji por grupo, usados en el dashboard, switcher y muro.
-- Ambos campos son opcionales: grupos existentes y nuevos pueden quedar sin personalizar.

ALTER TABLE `groups`
    ADD COLUMN color VARCHAR(7) NULL AFTER description,
    ADD COLUMN icon_emoji VARCHAR(8) NULL AFTER color;
