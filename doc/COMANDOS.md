# Manual de comandos - FEDER por correo (Grupo 28)

Envíe un correo a **grupo28sa@tecnoweb.org.bo** con el comando en el **Asunto** (el cuerpo se ignora).

- Formato: `COMANDO["param1","param2",...]`. Los comandos sin parámetros pueden ir solos: `HELP`, `LISCAR`.
- `param?` = opcional (puede omitirse o ir vacío `""`). `"*"` = todos (en filtros).
- Se toleran espacios extra, minúsculas, prefijos `RE:`/`RV:`/`FW:` y comillas escapadas `\"` dentro de un parámetro.
- Fechas `AAAA-MM-DD`; fecha y hora `AAAA-MM-DD HH:MM`; decimales con punto o coma.
- El sistema identifica al usuario por el **correo remitente**. Quien no está registrado solo puede usar `HELP` y `REGCLI`.
- `HELP` devuelve solo los comandos que **su rol** puede usar; `HELP["COMANDO"]` explica uno en detalle.

Roles: **A** = Administrador, **P** = Personal de FEDER, **R** = Productor (solo sus datos), **C** = Cliente (solo sus datos), **N** = no registrado.
Los permisos salen de la tabla `rol_permiso` y el Administrador los puede cambiar con `ASIPRM` (esta tabla refleja la configuración inicial).

## General

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `HELP["comando\|*"]` | A,P,R,C,N | Muestra los comandos que usted puede usar, o el detalle de uno. | `HELP   o   HELP["INSCAR"]` |

## CU1 - Usuarios, roles y permisos

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `REGCLI["ci","nombre","apellido","telefono","password","direccion?"]` | N | Registrarse como Cliente de FEDER con el correo desde el que escribe. | `REGCLI["7894561","Ana","Rojas","71234567","miClave123","Av. Banzer 4to anillo"]` |
| `VERPRF` | A,P,R,C | Ver mis datos de usuario. | `VERPRF` |
| `MODPRF["nombre\|apellido\|telefono\|direccion\|password","valor?"]` | A,P,R,C | Modificar un dato de mi perfil. | `MODPRF["telefono","76543210"]` |
| `LISUSU["administrador\|personal\|productor\|cliente\|*"]` | A,P | Listar usuarios (opcionalmente por rol). | `LISUSU["*"]   o   LISUSU["productor"]` |
| `INSUSU["administrador\|personal\|productor\|cliente","ci","nombre","apellido","email","telefono?","password","direccion?"]` | A | Registrar un usuario con cualquier rol. | `INSUSU["personal","6543210","Luis","Vaca","luis.vaca@gmail.com","70011223","clave123","Calle Sucre 12"]` |
| `MODUSU["id","nombre\|apellido\|ci\|email\|telefono\|direccion\|rol\|estado\|password","valor?"]` | A | Modificar un dato de un usuario. | `MODUSU["5","rol","personal"]` |
| `ELIUSU["id"]` | A | Dar de baja (inactivar) un usuario. | `ELIUSU["5"]` |
| `LISROL` | A,P | Listar roles y cuantos usuarios tiene cada uno. | `LISROL` |
| `LISPRM["administrador\|personal\|productor\|cliente\|*"]` | A,P | Ver la matriz de permisos (ver/crear/editar/eliminar) por rol. | `LISPRM["cliente"]` |
| `ASIPRM["administrador\|personal\|productor\|cliente","usuarios\|perfil\|comunidades\|emprendimientos\|ofertas\|produccion\|lotes\|catalogo\|inventario\|carrito\|encargos\|pedidos\|pagos\|entregas\|notificaciones\|finanzas\|reportes","ver\|crear\|editar\|eliminar","si\|no"]` | A | Conceder o quitar un permiso a un rol sobre un modulo. | `ASIPRM["personal","finanzas","ver","no"]` |

## CU2 - Productores y comunidades

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `LISCOM["municipio\|*"]` | A,P,R,C | Listar comunidades (filtro opcional por municipio). | `LISCOM["*"]   o   LISCOM["San Julian"]` |
| `INSCOM["nombre","municipio","provincia?","descripcion?"]` | A,P | Registrar una comunidad. | `INSCOM["Comunidad Los Angeles","San Julian","Nuflo de Chavez","Productores de arroz y mani"]` |
| `MODCOM["id","nombre\|municipio\|provincia\|departamento\|descripcion","valor?"]` | A,P | Modificar un dato de una comunidad. | `MODCOM["1","provincia","Ichilo"]` |
| `ELICOM["id"]` | A,P | Eliminar una comunidad sin emprendimientos. | `ELICOM["3"]` |
| `LISEMP["comunidad_id\|*"]` | A,P,R | Listar productores/emprendimientos (filtro por comunidad). El Productor ve solo el suyo. | `LISEMP["*"]   o   LISEMP["1"]` |
| `INSEMP["comunidad_id","nombre","agricola\|artesania\|alimentos_procesados\|textil\|otro","responsable","ci_nit?","telefono?","direccion?","email_usuario?","latitud?","longitud?"]` | A,P | Registrar un productor/emprendimiento; opcionalmente vincular la cuenta (email) de un usuario Productor. | `INSEMP["1","Arrocera Los Angeles","agricola","Pedro Choque","4567890","71122334","Km 5 carretera","pedro@gmail.com"]` |
| `MODEMP["id","nombre\|comunidad_id\|rubro\|responsable\|ci_nit\|telefono\|direccion\|latitud\|longitud\|estado\|usuario","valor?"]` | A,P,R | Modificar un dato del emprendimiento. El Productor solo: responsable, telefono, direccion, latitud, longitud. | `MODEMP["2","telefono","76655443"]` |
| `ELIEMP["id"]` | A,P | Dar de baja (inactivar) un emprendimiento. | `ELIEMP["2"]` |
| `LISOFE["emprendimiento_id\|*"]` | A,P,R | Productos que ofrece cada emprendimiento (precio de referencia y temporada). | `LISOFE["*"]   o   LISOFE["2"]` |
| `INSOFE["emprendimiento_id","producto_id","precio_referencia?","temporada?"]` | A,P,R | Registrar (o actualizar) un producto ofrecido por un emprendimiento. | `INSOFE["2","1","9.50","marzo-junio"]` |
| `ELIOFE["emprendimiento_id","producto_id"]` | A,P,R | Quitar un producto de lo ofrecido por un emprendimiento. | `ELIOFE["2","1"]` |

## CU3 - Produccion, lotes y trazabilidad

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `INSPRC["emprendimiento_id","producto_id","disponible\|futura","cantidad","unidad_medida","fecha_produccion?","fecha_disponible?","precio_propuesto?","procedencia?"]` | A,P,R | Reportar produccion: disponible (ya cosechada) o futura. El Productor solo para su emprendimiento. | `INSPRC["1","3","disponible","500","kg","2026-11-10","2026-11-15","8.50","Parcela norte, San Julian"]` |
| `LISPRC["reportada\|validada\|rechazada\|*"]` | A,P,R | Listar produccion reportada por estado. El Productor ve solo la suya. | `LISPRC["reportada"]   o   LISPRC["*"]` |
| `VALPRC["id","validada\|rechazada","observacion?"]` | A,P | Validar o rechazar una produccion reportada (Personal de FEDER). | `VALPRC["4","validada","Calidad verificada en campo"]` |
| `INSLOT["produccion_id","codigo","cantidad","fecha_vencimiento?","costo_unitario?"]` | A,P | Ingresar al inventario un lote de una produccion VALIDADA (registra el movimiento de entrada). | `INSLOT["4","ARR-2026-001","500","2027-05-30","6.20"]` |
| `LISLOT["producto_id\|*"]` | A,P,R | Listar lotes (filtro por producto). El Productor ve solo los lotes de su produccion. | `LISLOT["*"]   o   LISLOT["3"]` |
| `TRALOT["codigo"]` | A,P,R | Trazabilidad de un lote: origen (comunidad, emprendimiento, cosecha), movimientos y pedidos donde se vendio. | `TRALOT["ARR-2026-001"]` |
| `TRAPED["pedido_id"]` | A,P,C | Trazabilidad de un pedido: de que lote, emprendimiento y comunidad vino cada producto. | `TRAPED["12"]` |

## CU4 - Catalogo, inventario y alertas

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `LISCAT` | A,P,R,C | Listar categorias de productos. | `LISCAT` |
| `INSCAT["nombre","descripcion?"]` | A,P | Registrar una categoria. | `INSCAT["Frutas","Frutas frescas de temporada"]` |
| `MODCAT["id","nombre\|descripcion","valor?"]` | A,P | Modificar una categoria. | `MODCAT["1","nombre","Frutas tropicales"]` |
| `ELICAT["id"]` | A,P | Eliminar una categoria sin productos. | `ELICAT["5"]` |
| `LISPRO["categoria_id\|*"]` | A,P,R,C | Catalogo de productos con precio, modalidad (inmediata/encargo) y stock disponible. | `LISPRO["*"]   o   LISPRO["2"] (por categoria)` |
| `VERPRO["id"]` | A,P,R,C | Ficha completa de un producto: descripcion, disponibilidad y emprendimientos que lo producen. | `VERPRO["3"]` |
| `DISPRO["producto_id\|*"]` | A,P,R,C | Disponibilidad: stock propio de FEDER, reservado, produccion reportada y produccion futura. | `DISPRO["*"]   o   DISPRO["3"]` |
| `INSPRO["categoria_id","nombre","unidad_medida","precio_venta","inmediata\|encargo\|ambas","stock_minimo?","presentacion?","descripcion?","imagen?"]` | A,P | Registrar un producto en el catalogo. | `INSPRO["2","Arroz grano de oro","kg","9.50","ambas","100","Bolsa 1 kg","Arroz de San Julian","https://.../arroz.jpg"]` |
| `MODPRO["id","categoria_id\|nombre\|descripcion\|imagen\|presentacion\|unidad_medida\|precio_venta\|modalidad\|stock_minimo\|estado","valor?"]` | A,P | Modificar un dato de un producto. | `MODPRO["3","precio_venta","10.00"]` |
| `ELIPRO["id"]` | A,P | Dar de baja (inactivar) un producto del catalogo. | `ELIPRO["3"]` |
| `INSMOV["lote_id","salida\|devolucion\|ajuste","cantidad","motivo"]` | A,P | Registrar movimiento manual de un lote: salida (merma), devolucion (+) o ajuste (+/-). | `INSMOV["2","salida","5","Merma por humedad"]   o   INSMOV["2","ajuste","-1.5","Conteo fisico"]` |
| `LISMOV["lote_id\|*"]` | A,P | Historial de movimientos de inventario (filtro por lote). | `LISMOV["*"]   o   LISMOV["2"]` |
| `LISALE["activa\|atendida\|*"]` | A,P | Alertas de inventario (stock bajo y vencimiento). | `LISALE["activa"]   o   LISALE["*"]` |
| `ATEALE["id"]` | A,P | Marcar una alerta como atendida. | `ATEALE["3"]` |

## CU5 - Carrito, compras y encargos

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `LISCAR` | C | Ver mi carrito con el total y el stock actual. | `LISCAR` |
| `INSCAR["producto_id","cantidad"]` | C | Agregar un producto al carrito (suma a lo que ya tenia). Verifica stock y modalidad. | `INSCAR["3","2.5"]` |
| `MODCAR["producto_id","cantidad"]` | C | Cambiar la cantidad de un producto del carrito. | `MODCAR["3","4"]` |
| `ELICAR["producto_id"]` | C | Quitar un producto del carrito. | `ELICAR["3"]` |
| `VACCAR` | C | Vaciar el carrito. | `VACCAR` |
| `CONCAR["recojo\|envio","direccion?","nit_ci?","razon_social?","referencia?"]` | C | Confirmar la compra: reserva el stock (sin sobreventa) y genera el pedido. Luego pague con INSPAG. | `CONCAR["envio","Av. Busch 3er anillo #45","7894561","Ana Rojas","Porton verde"]   o   CONCAR["recojo"]` |
| `INSENC["producto_id","cantidad","fecha_requerida?","observacion?"]` | A,P,C | Solicitar un encargo de un producto (modalidad encargo o ambas). Agregue mas productos con ADDENC. | `INSENC["5","20","2026-12-20","Para fiesta de fin de ano"]` |
| `ADDENC["encargo_id","producto_id","cantidad"]` | A,P,C | Agregar otro producto a un encargo aun SOLICITADO. | `ADDENC["2","6","10"]` |
| `LISENC["solicitado\|aceptado\|rechazado\|disponible\|convertido\|cancelado\|*"]` | A,P,C | Listar encargos por estado. El Cliente ve solo los suyos. | `LISENC["*"]   o   LISENC["aceptado"]` |
| `VERENC["id"]` | A,P,C | Ver el detalle de un encargo. | `VERENC["2"]` |
| `CANENC["id"]` | A,P,C | Cancelar un encargo que aun no se convirtio en pedido. | `CANENC["2"]` |
| `MODENC["id","aceptado\|rechazado\|disponible","observacion?"]` | A,P | Cambiar estado del encargo: aceptado \| rechazado \| disponible (verifica stock y notifica al cliente). | `MODENC["2","disponible","Ya llego la cosecha"]` |
| `CONENC["id","recojo\|envio","direccion?"]` | A,P,C | Convertir un encargo DISPONIBLE en pedido: reserva el stock y registra la entrega. | `CONENC["2","envio","Calle Warnes 120"]   o   CONENC["2","recojo"]` |

## CU6 - Pedidos, pagos, entregas y notificaciones

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `LISPED["pendiente\|confirmado\|en_preparacion\|listo_entrega\|entregado\|cancelado\|*"]` | A,P,C | Listar pedidos por estado. El Cliente ve solo los suyos. | `LISPED["*"]   o   LISPED["confirmado"]` |
| `VERPED["id"]` | A,P,C | Detalle de un pedido: productos, pagos y entrega. | `VERPED["12"]` |
| `MODPED["id","confirmado\|en_preparacion\|listo_entrega\|entregado"]` | A,P | Avanzar el estado del pedido (confirmado, en_preparacion, listo_entrega, entregado). Notifica al cliente. | `MODPED["12","en_preparacion"]` |
| `CANPED["id"]` | A,P,C | Cancelar un pedido y liberar su stock reservado (Cliente: solo pendientes propios). | `CANPED["12"]` |
| `INSPAG["pedido_id","qr\|efectivo\|tarjeta","monto?","referencia?"]` | A,P,C | Registrar un pago de un pedido (monto por defecto: el saldo pendiente). | `INSPAG["12","efectivo","57.00","Recibo 0012"]   o   INSPAG["12","qr"]` |
| `MODPAG["id","pagado\|rechazado\|anulado"]` | A,P | Verificar un pago: pagado \| rechazado \| anulado. Al completar el total, el pedido se confirma y se notifica. | `MODPAG["7","pagado"]` |
| `LISPAG["pedido_id\|*"]` | A,P,C | Listar pagos (filtro por pedido). El Cliente ve solo los suyos. | `LISPAG["*"]   o   LISPAG["12"]` |
| `LISENT["pendiente\|en_camino\|entregada\|fallida\|*"]` | A,P,C | Listar entregas por estado. El Cliente ve solo las suyas. | `LISENT["*"]   o   LISENT["pendiente"]` |
| `PROENT["pedido_id","fecha_programada"]` | A,P | Programar fecha y hora de entrega de un pedido. | `PROENT["12","2026-11-25 15:30"]` |
| `MODENT["pedido_id","en_camino\|entregada\|fallida","receptor?"]` | A,P | Actualizar la entrega: en_camino \| entregada (cierra el pedido) \| fallida. | `MODENT["12","entregada","Ana Rojas"]` |
| `LISNOT` | A,P,R,C | Ver mis ultimas notificaciones (se marcan como leidas). | `LISNOT` |

## CU7 - Indicadores financieros

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `INDFIN["fecha_inicio","fecha_fin"]` | A,P | Indicadores del periodo: ventas, ticket promedio, cobrado, por cobrar, costo, margen bruto e inventario valorizado. | `INDFIN["2026-11-01","2026-11-30"]` |
| `LISSAL` | A,P | Cuentas por cobrar: pedidos activos con saldo pendiente. | `LISSAL` |

## CU8 - Reportes y estadisticas

| Comando | Roles | Descripción | Ejemplo |
|---|---|---|---|
| `REPPRD["fecha_inicio","fecha_fin"]` | A,P,R | Reporte de produccion por comunidad y emprendimiento en un rango de fechas (Productor: solo la suya). | `REPPRD["2026-01-01","2026-12-31"]` |
| `REPVEN["fecha_inicio","fecha_fin"]` | A,P | Ventas por producto en un rango de fechas. | `REPVEN["2026-11-01","2026-11-30"]` |
| `REPINV` | A,P | Inventario actual por producto: stock, reservado, minimo, lotes por vencer y valor. | `REPINV` |
| `REPPED["fecha_inicio","fecha_fin"]` | A,P | Pedidos por estado y por origen (inmediato/encargo). | `REPPED["2026-11-01","2026-11-30"]` |
| `REPENC["fecha_inicio","fecha_fin"]` | A,P | Encargos por estado y productos mas encargados. | `REPENC["2026-11-01","2026-11-30"]` |
| `REPMET["fecha_inicio","fecha_fin"]` | A,P | Pagos por metodo (QR, efectivo, tarjeta) y estado. | `REPMET["2026-11-01","2026-11-30"]` |
| `REPING["fecha_inicio","fecha_fin"]` | A,P | Ingresos cobrados por dia en un rango de fechas. | `REPING["2026-11-01","2026-11-30"]` |

## Flujos típicos

**Compra inmediata (Cliente):** `LISPRO["*"]` → `INSCAR["1","10"]` → `LISCAR` → `CONCAR["recojo"]` (reserva el stock) → `INSPAG["<pedido>","efectivo"]` → recibe correo *Pago confirmado* / *Pedido confirmado*.

**Procesar el pedido (Personal):** `MODPAG["<pago>","pagado"]` → `MODPED["<pedido>","en_preparacion"]` (descuenta los lotes) → `MODPED["<pedido>","listo_entrega"]` (avisa al cliente) → `MODENT["<pedido>","entregada","Receptor"]`.

**Producción (Productor → Personal):** `INSPRC[...]` → `VALPRC["<id>","validada"]` → `INSLOT["<id>","COD-001","cantidad","vence","costo"]` → el stock aparece en el catálogo.

**Encargo:** Cliente `INSENC[...]` → Personal `MODENC["<id>","aceptado"]` → (llega el stock) `MODENC["<id>","disponible"]` (avisa al cliente) → Cliente `CONENC["<id>","recojo"]` → pago como en una compra.
