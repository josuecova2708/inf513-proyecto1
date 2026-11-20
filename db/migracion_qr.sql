-- =============================================================================
--  Migracion: QR de cobro subido por el Administrador (pago por QR sin PagoFacil)
--  Se puede ejecutar varias veces.
--  Uso:  java -cp target\FederCorreo-1.0.jar feder.app.Herramientas sql db/migracion_qr.sql
-- =============================================================================

CREATE TABLE IF NOT EXISTS qr_pago (
    id              SERIAL PRIMARY KEY,
    imagen          BYTEA        NOT NULL,
    tipo_mime       VARCHAR(50)  NOT NULL DEFAULT 'image/png',
    nombre_archivo  VARCHAR(120),
    subido_por      INT          REFERENCES usuario(id),
    fecha           TIMESTAMP    NOT NULL DEFAULT now(),
    activo          BOOLEAN      NOT NULL DEFAULT TRUE   -- solo uno activo; los anteriores quedan de historial
);

INSERT INTO permiso (nombre, modulo, accion) VALUES ('QR de cobro', 'qr', 'gestionar')
ON CONFLICT (modulo, accion) DO NOTHING;

-- Administrador lo sube; Personal y Cliente solo lo ven
INSERT INTO rol_permiso (rol_id, permiso_id, ver, crear, editar, eliminar)
SELECT r.id, p.id, m.ver, m.crear, m.editar, m.eliminar
FROM (VALUES
    ('Administrador', true, true, true, true),
    ('Personal',      true, false, false, false),
    ('Cliente',       true, false, false, false)
) AS m(rol, ver, crear, editar, eliminar)
JOIN rol r ON r.nombre = m.rol
JOIN permiso p ON p.modulo = 'qr'
ON CONFLICT (rol_id, permiso_id) DO NOTHING;
