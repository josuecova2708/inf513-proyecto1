# Guion de demostración — FEDER por correo (Grupo 28)

Todos los comandos se envían por correo a **grupo28sa@tecnoweb.org.bo**, con el comando en el **Asunto** (el cuerpo se ignora).
Copie el asunto **exactamente** como aparece en la columna *Asunto*.

## Quién es quién

| Rol | Correo | Cómo se creó |
|---|---|---|
| **A** Administrador | `josuecovarrubias8@gmail.com` | Por base de datos (id 8, clave `feder123`) |
| **P** Personal de FEDER | `<CORREO_PERSONAL>` (un correo real de un compañero) | Paso 2, con `INSUSU` |
| **C** Cliente | `covarrubiasjosue27@gmail.com` | Ya existe (id 7) |
| **N** No registrado | cualquier otro correo | — |

> Reemplace `<CORREO_PERSONAL>` por un correo real **desde el que puedan enviar** (el de un compañero es lo más simple).
> Los IDs entre `< >` (`<pedido>`, `<pago>`, `<produccion>`) se leen de la respuesta del paso anterior.

---

## 0. Antes de empezar (en la PC)

```bat
mvn -q -DskipTests clean package
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas diag
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas subirqr C:\ruta\qr_real.png josuecovarrubias8@gmail.com
java -DMODE=DEV -jar target\FederCorreo-1.0.jar
```

- `diag` debe mostrar **OK** para la BD y para POP3.
- `subirqr` reemplaza el QR de prueba por el QR real (o hágalo por correo en el paso 2.6).
- Deje la consola **visible** durante la defensa: se ve una línea por ciclo y cada correo procesado.
- No tenga el sistema corriendo a la vez en el servidor (los dos leerían la misma casilla).

---

## 1. Robustez del parser y manejo de errores (lo que pide el enunciado)

Desde **cualquier correo**. Cada uno debe responder con un ERROR claro.

| # | Asunto | Qué demuestra |
|---|---|---|
| 1.1 | `HELP` | Un no registrado solo ve `HELP` y `REGCLI` |
| 1.2 | `LISPRO["*"` | Sintaxis inválida: indica la **posición** del error (falta `]`) |
| 1.3 | `LISPRO[*]` | Parámetro sin comillas dobles |
| 1.4 | `LISPROD["*"]` | Comando inexistente con sugerencia: *¿Quiso decir LISPRO?* |
| 1.5 | `   lispro [ "*" ]   ` | Tolera espacios y minúsculas (con un correo registrado responde el catálogo) |
| 1.6 | `LISPRO["1";DROP TABLE usuario;--"]` | Intento de inyección SQL: rechazado por el parser |

Desde el **Cliente** (`covarrubiasjosue27@gmail.com`):

| # | Asunto | Qué demuestra |
|---|---|---|
| 1.7 | `INSCAR["abc","2"]` | Validación de tipos: *debe ser un entero positivo* |
| 1.8 | `INSCAR["1"]` | Cantidad de parámetros: *requiere 2 y llegaron 1*, con uso y ejemplo |
| 1.9 | `INSUSU["personal","1234567","X","Y","x@y.com","","clave123",""]` | **No autorizado**: un Cliente no puede crear usuarios |

---

## 2. Administrador: usuarios, roles, permisos y QR

Remitente: **A** `josuecovarrubias8@gmail.com`

| # | Asunto | Qué mostrar |
|---|---|---|
| 2.1 | `HELP` | Lista completa de comandos del Administrador (respuesta con diseño HTML) |
| 2.2 | `LISROL` | Roles y cantidad de usuarios |
| 2.3 | `LISPRM["personal"]` | Matriz de permisos ver/crear/editar/eliminar leída de la BD |
| 2.4 | `INSUSU["personal","9876543","Maria","Lopez","<CORREO_PERSONAL>","71234567","clave123","Av. Busch 100"]` | Crea el usuario **Personal** |
| 2.5 | `INSUSU["personal","9876543","Maria","Lopez","<CORREO_PERSONAL>","71234567","clave123",""]` | Repetirlo: **ERROR de unicidad** (correo / CI ya registrados) |
| 2.6 | `SUBQR` **con la imagen del QR adjunta** | Respuesta con el QR incrustado para confirmar (sin adjunto → error claro) |
| 2.7 | `VERQR` | Muestra el QR vigente |
| 2.8 | `LISUSU["*"]` | Aparece el nuevo usuario Personal |

---

## 3. Personal: permisos limitados

Remitente: **P** `<CORREO_PERSONAL>`

| # | Asunto | Qué mostrar |
|---|---|---|
| 3.1 | `HELP` | Menos comandos que el Administrador (los filtra su rol) |
| 3.2 | `INSUSU["cliente","1112223","Z","Z","z@z.com","","clave123",""]` | **No autorizado**: el Personal no crea usuarios |
| 3.3 | `LISALE["activa"]` | Alertas de inventario (stock bajo / vencimiento) |

---

## 4. Producción → inventario → catálogo (trazabilidad)

Remitente: **P** `<CORREO_PERSONAL>` (o **A**)

| # | Asunto | Qué mostrar |
|---|---|---|
| 4.1 | `INSPRC["3","4","disponible","100","kg","2026-11-18","2026-11-19","6.50","Parcela sur, Jorochito"]` | Reporta 100 kg de tomate de *Huerta Jorochito* → devuelve el id `<produccion>` |
| 4.2 | `LISPRC["reportada"]` | La producción aparece como reportada |
| 4.3 | `VALPRC["<produccion>","validada","Calidad verificada en campo"]` | Se valida |
| 4.4 | `INSLOT["<produccion>","TOM-DEMO-01","100","2026-12-15","5.00"]` | Ingresa el lote al inventario (movimiento de entrada) |
| 4.5 | `LISLOT["4"]` | Lotes de tomate, incluido `TOM-DEMO-01` |
| 4.6 | `TRALOT["TOM-DEMO-01"]` | Trazabilidad: comunidad → emprendimiento → cosecha → movimientos |
| 4.7 | `DISPRO["4"]` | Stock propio, reservado y producción reportada/futura |

---

## 5. Compra del Cliente con pago por QR

Remitente: **C** `covarrubiasjosue27@gmail.com`

| # | Asunto | Qué mostrar |
|---|---|---|
| 5.1 | `LISPRO["*"]` | Catálogo con tabla HTML (precio, modalidad, stock) |
| 5.2 | `VERPRO["4"]` | Ficha del tomate y quién lo produce |
| 5.3 | `INSCAR["4","5"]` | Agrega 5 kg de tomate |
| 5.4 | `INSCAR["1","2"]` | Agrega 2 kg de arroz |
| 5.5 | `INSCAR["2","99999"]` | **Sin sobreventa**: *Stock insuficiente…* |
| 5.6 | `INSCAR["5","1"]` | La papaya es solo por **encargo**: lo indica y sugiere `INSENC` |
| 5.7 | `LISCAR` | Carrito con total |
| 5.8 | `CONCAR["recojo"]` | Confirma la compra, **reserva el stock** y devuelve el id `<pedido>` |
| 5.9 | `INSPAG["<pedido>","qr"]` | Recibe el **QR incrustado**, el monto exacto y la glosa (número de pedido) → devuelve el id `<pago>` |

En ese momento el **Personal** recibe el correo *Pago QR por verificar* con los comandos listos para copiar.

---

## 6. Verificación manual del pago y entrega

Remitente: **P** `<CORREO_PERSONAL>`

| # | Asunto | Qué mostrar |
|---|---|---|
| 6.1 | `LISPAG["<pedido>"]` | Pago en estado `pendiente` |
| 6.2 | `MODPAG["<pago>","pagado"]` | El Cliente recibe *Pago confirmado* y el pedido pasa a **confirmado** |
| 6.3 | `MODPED["<pedido>","en_preparacion"]` | Descuenta los lotes (salida de inventario) |
| 6.4 | `PROENT["<pedido>","2026-11-21 15:30"]` | Programa la entrega (la fecha debe ser **futura**; ajústela al día de la defensa) |
| 6.5 | `MODPED["<pedido>","listo_entrega"]` | El Cliente recibe *Pedido listo para entrega* |
| 6.6 | `MODENT["<pedido>","entregada","Josue Covarrubias"]` | Cierra el pedido (entregado) |
| 6.7 | `TRALOT["TOM-DEMO-01"]` | Ahora el lote muestra el pedido donde se vendió |

Remitente: **C** `covarrubiasjosue27@gmail.com`

| # | Asunto | Qué mostrar |
|---|---|---|
| 6.8 | `LISNOT` | Notificaciones recibidas (pago, pedido listo…) |
| 6.9 | `TRAPED["<pedido>"]` | De qué lote, emprendimiento y comunidad vino cada producto |
| 6.10 | `LISPED["*"]` | El Cliente solo ve **sus** pedidos |

---

## 7. Cancelación y liberación de stock (opcional)

Remitente: **C**

| # | Asunto | Qué mostrar |
|---|---|---|
| 7.1 | `INSCAR["1","3"]` | Nuevo carrito |
| 7.2 | `CONCAR["recojo"]` | Reserva el stock → `<pedido2>` |
| 7.3 | `DISPRO["1"]` | Stock reservado aumentó |
| 7.4 | `CANPED["<pedido2>"]` | Cancela y **libera** la reserva |
| 7.5 | `DISPRO["1"]` | El reservado volvió a bajar |

---

## 8. Encargo (opcional)

| # | Remitente | Asunto | Qué mostrar |
|---|---|---|---|
| 8.1 | C | `INSENC["5","3","2026-12-05","Para un evento"]` | Encargo de papaya → `<encargo>` |
| 8.2 | P | `LISENC["solicitado"]` | El Personal ve el encargo |
| 8.3 | P | `MODENC["<encargo>","aceptado","Se coordinara con el productor"]` | Lo acepta (el cliente es notificado al pasar a disponible) |
| 8.4 | C | `VERENC["<encargo>"]` | Estado del encargo |

---

## 9. Indicadores y reportes

Remitente: **A** o **P**

| # | Asunto | Qué mostrar |
|---|---|---|
| 9.1 | `INDFIN["2026-11-01","2026-11-30"]` | Ventas, ticket promedio, cobrado, por cobrar, margen bruto |
| 9.2 | `REPVEN["2026-11-01","2026-11-30"]` | Ventas por producto |
| 9.3 | `REPMET["2026-11-01","2026-11-30"]` | Pagos por método (aparece el QR) |
| 9.4 | `REPINV` | Inventario valorizado y lotes por vencer |
| 9.5 | `LISSAL` | Cuentas por cobrar |

---

## 10. Logs y errores (para la auditoría)

En la PC, con el sistema corriendo:

- **Consola:** una línea por ciclo (`[Ciclo #N] BD OK | POP3 OK | bandeja …`) y cada correo procesado (`[ACCESS] …`).
- `logs\access.log`: un registro por correo (remitente, rol, ÉXITO/ERROR, comando, duración).
- `logs\error.log`: excepciones con traza, etiquetadas `[BD]`, `[POP3]`, `[SMTP]`, `[COMANDO]`.

Si piden ver un error de infraestructura, sin tocar la configuración real:

```bat
copy config.properties prueba.properties
REM editar prueba.properties: db.pass=claveMala   (o mail.pass=claveMala)
java -DMODE=DEV -Dconfig=prueba.properties -jar target\FederCorreo-1.0.jar
```

Se verá `[ERROR][BD] ... la autentificación password falló` (o `[ERROR][POP3] ... usuario/clave rechazados`) y `[Ciclo #1] BD FALLO | ...`. Ciérrelo con `Ctrl+C` y vuelva a arrancar con la configuración normal.

---

## Volver a empezar

Para repetir la demo con los datos iniciales (borra todo y recarga los datos de prueba; después hay que volver a crear al Administrador y subir el QR):

```bat
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas sql db\schema.sql
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas sql db\datos_prueba.sql
```
