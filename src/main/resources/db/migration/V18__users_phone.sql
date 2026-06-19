-- Teléfono de contacto opcional, privado por defecto (M.1, ADR/decisión #8).
-- Nunca se expone en el muro público; la visibilidad se controla aparte.
ALTER TABLE users ADD COLUMN phone VARCHAR(30) NULL;
ALTER TABLE users ADD COLUMN phone_visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE';
