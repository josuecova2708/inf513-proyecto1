-- =============================================================================
--  FEDER - Datos iniciales y de prueba (Grupo 28)
--  Ejecutar DESPUES de schema.sql. Se puede volver a ejecutar: vacia los datos
--  (excepto rol y metodo_pago, que crea schema.sql) y los vuelve a cargar.
--
--  Clave de TODOS los usuarios de prueba: feder123  (hash BCrypt $2a$, compatible con Laravel)
--  IMPORTANTE: reemplace los correos @example.com por correos reales para probar por email
--  (o use MODUSU["id","email","nuevo@correo"]).
-- =============================================================================

TRUNCATE notificacion, entrega, pago, reserva_lote, detalle_pedido, pedido, detalle_encargo, encargo,
         alerta_inventario, movimiento_inventario, lote, produccion, emprendimiento_producto, producto,
         categoria, emprendimiento, comunidad, usuario, rol_permiso, permiso
         RESTART IDENTITY CASCADE;

-- ---------- CU1: modulos del sistema (un permiso por modulo) ----------------
INSERT INTO permiso (nombre, modulo, accion) VALUES
    ('Gestionar usuarios, roles y permisos', 'usuarios',        'gestionar'),
    ('Mi perfil',                            'perfil',          'gestionar'),
    ('Comunidades agricolas',                'comunidades',     'gestionar'),
    ('Productores / emprendimientos',        'emprendimientos', 'gestionar'),
    ('Productos ofrecidos por productor',    'ofertas',         'gestionar'),
    ('Produccion agricola',                  'produccion',      'gestionar'),
    ('Lotes y trazabilidad',                 'lotes',           'gestionar'),
    ('Catalogo de productos',                'catalogo',        'gestionar'),
    ('Inventario y alertas',                 'inventario',      'gestionar'),
    ('Carrito de compras',                   'carrito',         'gestionar'),
    ('Encargos',                             'encargos',        'gestionar'),
    ('Pedidos',                              'pedidos',         'gestionar'),
    ('Pagos',                                'pagos',           'gestionar'),
    ('Entregas',                             'entregas',        'gestionar'),
    ('Notificaciones',                       'notificaciones',  'gestionar'),
    ('Indicadores financieros',              'finanzas',        'gestionar'),
    ('Reportes',                             'reportes',        'gestionar');

-- ---------- CU1: matriz de permisos por rol (ver, crear, editar, eliminar) ---
-- Productor y Cliente ademas quedan limitados a SUS datos dentro de cada handler.
INSERT INTO rol_permiso (rol_id, permiso_id, ver, crear, editar, eliminar)
SELECT r.id, p.id, m.ver, m.crear, m.editar, m.eliminar
FROM (VALUES
    -- Administrador: todo (no compra: no usa carrito)
    ('Administrador','usuarios',        true, true, true, true),
    ('Administrador','perfil',          true, false,true, false),
    ('Administrador','comunidades',     true, true, true, true),
    ('Administrador','emprendimientos', true, true, true, true),
    ('Administrador','ofertas',         true, true, true, true),
    ('Administrador','produccion',      true, true, true, true),
    ('Administrador','lotes',           true, true, true, true),
    ('Administrador','catalogo',        true, true, true, true),
    ('Administrador','inventario',      true, true, true, true),
    ('Administrador','encargos',        true, true, true, true),
    ('Administrador','pedidos',         true, true, true, true),
    ('Administrador','pagos',           true, true, true, true),
    ('Administrador','entregas',        true, true, true, true),
    ('Administrador','notificaciones',  true, false,false,false),
    ('Administrador','finanzas',        true, false,false,false),
    ('Administrador','reportes',        true, false,false,false),
    -- Personal de FEDER: operacion completa; usuarios solo consulta; pagos/entregas sin eliminar
    ('Personal','usuarios',        true, false,false,false),
    ('Personal','perfil',          true, false,true, false),
    ('Personal','comunidades',     true, true, true, true),
    ('Personal','emprendimientos', true, true, true, true),
    ('Personal','ofertas',         true, true, true, true),
    ('Personal','produccion',      true, true, true, true),
    ('Personal','lotes',           true, true, true, true),
    ('Personal','catalogo',        true, true, true, true),
    ('Personal','inventario',      true, true, true, true),
    ('Personal','encargos',        true, true, true, true),
    ('Personal','pedidos',         true, true, true, true),
    ('Personal','pagos',           true, true, true, false),
    ('Personal','entregas',        true, true, true, false),
    ('Personal','notificaciones',  true, false,false,false),
    ('Personal','finanzas',        true, false,false,false),
    ('Personal','reportes',        true, false,false,false),
    -- Productor: su emprendimiento, sus ofertas, reporta produccion, ve sus lotes y el catalogo
    ('Productor','perfil',          true, false,true, false),
    ('Productor','comunidades',     true, false,false,false),
    ('Productor','emprendimientos', true, false,true, false),
    ('Productor','ofertas',         true, true, false,true),
    ('Productor','produccion',      true, true, false,false),
    ('Productor','lotes',           true, false,false,false),
    ('Productor','catalogo',        true, false,false,false),
    ('Productor','notificaciones',  true, false,false,false),
    -- Cliente: catalogo, carrito, encargos, sus pedidos/pagos/entregas
    ('Cliente','perfil',          true, false,true, false),
    ('Cliente','comunidades',     true, false,false,false),
    ('Cliente','catalogo',        true, false,false,false),
    ('Cliente','carrito',         true, true, true, true),
    ('Cliente','encargos',        true, true, false,true),
    ('Cliente','pedidos',         true, false,false,true),
    ('Cliente','pagos',           true, true, false,false),
    ('Cliente','entregas',        true, false,false,false),
    ('Cliente','notificaciones',  true, false,false,false)
) AS m(rol, modulo, ver, crear, editar, eliminar)
JOIN rol r ON r.nombre = m.rol
JOIN permiso p ON p.modulo = m.modulo;

-- ---------- CU1: usuarios de prueba (clave: feder123) -----------------------
INSERT INTO usuario (rol_id, nombre, apellido, ci, email, telefono, password_hash, direccion) VALUES
    ((SELECT id FROM rol WHERE nombre='Administrador'), 'Admin',  'FEDER',   '1000001', 'feder.admin@example.com',    '70000001', '$2a$10$qCg2XXfCe.Uk8ISt8mKcoecKpb.3lb4reviQHIiaIch5wq37cEK/q', 'Oficina central FEDER'),
    ((SELECT id FROM rol WHERE nombre='Personal'),      'Lucia',  'Mendez',  '1000002', 'feder.personal@example.com', '70000002', '$2a$10$qCg2XXfCe.Uk8ISt8mKcoecKpb.3lb4reviQHIiaIch5wq37cEK/q', 'Oficina central FEDER'),
    ((SELECT id FROM rol WHERE nombre='Productor'),     'Pedro',  'Choque',  '4567890', 'productor.pedro@example.com','71122334', '$2a$10$qCg2XXfCe.Uk8ISt8mKcoecKpb.3lb4reviQHIiaIch5wq37cEK/q', 'Comunidad Los Angeles, San Julian'),
    ((SELECT id FROM rol WHERE nombre='Productor'),     'Rosa',   'Quispe',  '5678901', 'productor.rosa@example.com', '72233445', '$2a$10$qCg2XXfCe.Uk8ISt8mKcoecKpb.3lb4reviQHIiaIch5wq37cEK/q', 'Comunidad Cuevas, Samaipata'),
    ((SELECT id FROM rol WHERE nombre='Cliente'),       'Ana',    'Rojas',   '7894561', 'cliente.ana@example.com',    '73344556', '$2a$10$qCg2XXfCe.Uk8ISt8mKcoecKpb.3lb4reviQHIiaIch5wq37cEK/q', 'Av. Banzer 4to anillo #120'),
    ((SELECT id FROM rol WHERE nombre='Cliente'),       'Carlos', 'Vaca',    '8905672', 'cliente.carlos@example.com', '74455667', '$2a$10$qCg2XXfCe.Uk8ISt8mKcoecKpb.3lb4reviQHIiaIch5wq37cEK/q', 'Calle Warnes #45');

-- ---------- CU2: comunidades y emprendimientos ------------------------------
INSERT INTO comunidad (nombre, municipio, provincia, descripcion) VALUES
    ('Comunidad Los Angeles', 'San Julian',  'Nuflo de Chavez', 'Productores de arroz y mani'),
    ('Colonia Okinawa',       'Okinawa Uno', 'Warnes',          'Granos y oleaginosas'),
    ('Comunidad Cuevas',      'Samaipata',   'Florida',         'Apicultura y cafe de altura'),
    ('Comunidad Jorochito',   'El Torno',    'Andres Ibanez',   'Hortalizas y frutas');

INSERT INTO emprendimiento (comunidad_id, usuario_id, nombre, rubro, responsable, ci_nit, telefono, direccion) VALUES
    (1, (SELECT id FROM usuario WHERE email='productor.pedro@example.com'), 'Arrocera Los Angeles',  'agricola',             'Pedro Choque', '4567890', '71122334', 'Km 5 carretera a San Julian'),
    (3, (SELECT id FROM usuario WHERE email='productor.rosa@example.com'),  'Apicola Flor del Monte','agricola',             'Rosa Quispe',  '5678901', '72233445', 'Camino a Cuevas s/n'),
    (4, NULL,                                                               'Huerta Jorochito',      'agricola',             'Juan Mamani',  '6789012', '75566778', 'Jorochito, El Torno'),
    (3, NULL,                                                               'Cafe Cuevas',           'alimentos_procesados', 'Elena Flores', '7890123', '76677889', 'Samaipata centro');

-- ---------- CU4: catalogo ---------------------------------------------------
INSERT INTO categoria (nombre, descripcion) VALUES
    ('Granos y cereales', 'Arroz, mani, maiz y otros granos'),
    ('Miel y derivados',  'Miel de abeja y productos apicolas'),
    ('Hortalizas',        'Verduras frescas'),
    ('Frutas',            'Frutas de temporada'),
    ('Cafe y bebidas',    'Cafe de altura y bebidas');

INSERT INTO producto (categoria_id, nombre, descripcion, presentacion, unidad_medida, precio_venta, modalidad_venta, stock_minimo) VALUES
    (1, 'Arroz grano de oro',      'Arroz seleccionado de San Julian',          'Bolsa 1 kg',   'kg',     9.50,  'ambas',     50),
    (1, 'Mani pelado',             'Mani pelado y seleccionado',                'Bolsa 1 kg',   'kg',     18.00, 'inmediata', 20),
    (2, 'Miel de abeja pura',      'Miel multifloral de Samaipata',             'Frasco 500 g', 'frasco', 45.00, 'ambas',     10),
    (3, 'Tomate perita',           'Tomate fresco de El Torno',                 'Por kilo',     'kg',     7.00,  'inmediata', 30),
    (4, 'Papaya',                  'Papaya de temporada (solo por encargo)',    'Unidad',       'unidad', 12.00, 'encargo',   0),
    (5, 'Cafe de Samaipata molido','Cafe de altura tostado y molido',           'Bolsa 250 g',  'bolsa',  35.00, 'ambas',     15),
    (3, 'Lechuga crespa',          'Lechuga hidroponica (solo por encargo)',    'Unidad',       'unidad', 4.00,  'encargo',   0);

INSERT INTO emprendimiento_producto (emprendimiento_id, producto_id, precio_referencia, temporada) VALUES
    (1, 1, 6.50, 'todo el ano'), (1, 2, 12.00, 'abril-agosto'), (2, 3, 28.00, 'septiembre-diciembre'),
    (3, 4, 4.00, 'todo el ano'), (3, 5, 7.00, 'noviembre-marzo'), (3, 7, 2.50, 'todo el ano'), (4, 6, 22.00, 'junio-octubre');

-- ---------- CU3: produccion (validada por Personal, id 2) y lotes -----------
INSERT INTO produccion (emprendimiento_id, producto_id, validado_por, tipo, cantidad, unidad_medida, fecha_produccion,
                        fecha_disponible, precio_propuesto, procedencia, estado, fecha_validacion) VALUES
    (1, 1, 2, 'disponible', 400, 'kg',     CURRENT_DATE - 20, CURRENT_DATE - 15, 6.50,  'Parcela norte, San Julian', 'validada', now()),
    (1, 2, 2, 'disponible', 100, 'kg',     CURRENT_DATE - 30, CURRENT_DATE - 25, 12.00, 'San Julian',                'validada', now()),
    (2, 3, 2, 'disponible', 60,  'frasco', CURRENT_DATE - 10, CURRENT_DATE - 8,  28.00, 'Apiario Cuevas',            'validada', now()),
    (3, 4, 2, 'disponible', 120, 'kg',     CURRENT_DATE - 3,  CURRENT_DATE - 2,  4.00,  'Huerta Jorochito',          'validada', now()),
    (4, 6, 2, 'disponible', 30,  'bolsa',  CURRENT_DATE - 40, CURRENT_DATE - 35, 22.00, 'Finca Cuevas',              'validada', now()),
    (3, 5, 2, 'futura',     200, 'unidad', NULL,              CURRENT_DATE + 30, 7.00,  'Huerta Jorochito',          'validada', now());
INSERT INTO produccion (emprendimiento_id, producto_id, tipo, cantidad, unidad_medida, fecha_produccion, fecha_disponible,
                        precio_propuesto, procedencia) VALUES
    (1, 1, 'disponible', 300, 'kg', CURRENT_DATE - 2, CURRENT_DATE + 3, 6.80, 'Parcela sur, San Julian');   -- reportada, sin validar

-- fn_ingresar_lote(produccion, codigo, cantidad, usuario, vencimiento, costo) registra tambien la ENTRADA
SELECT fn_ingresar_lote(1, 'ARR-001', 400, 2, CURRENT_DATE + 300, 6.50);
SELECT fn_ingresar_lote(2, 'MAN-001', 80,  2, CURRENT_DATE + 180, 12.00);
SELECT fn_ingresar_lote(3, 'MIE-001', 60,  2, CURRENT_DATE + 365, 28.00);
SELECT fn_ingresar_lote(4, 'TOM-001', 40,  2, CURRENT_DATE + 5,   4.00);   -- vence pronto: genera alerta
SELECT fn_ingresar_lote(5, 'CAF-001', 12,  2, CURRENT_DATE + 200, 22.00);  -- bajo el minimo (15): alerta

-- Fin de datos de prueba
