# Guion de demostración — "Una semana en FEDER" (Grupo 28)

Todos los comandos se envían por correo a **grupo28sa@tecnoweb.org.bo** con el comando en el **Asunto** (el cuerpo se ignora).
Copie el asunto **exactamente** como está en la columna *Asunto*. Los valores entre `< >` se copian de la respuesta anterior.

## Personajes

| Rol | Persona | Correo |
|---|---|---|
| **A** Administrador | Josue Covarrubias (gerente de FEDER) | `josuecovarrubias8@gmail.com` |
| **P** Personal de FEDER | Encargado de acopio y despacho | `cacharrospremium@gmail.com` |
| **C** Cliente | Marco Cova (compra por correo) | `covarrubiasjosue27@gmail.com` |

Productores que aparecen (ya están en la BD, el Personal registra por ellos lo que entregan en acopio):
**Huerta Jorochito** (emprendimiento 3, Comunidad Jorochito, El Torno: tomate, papaya, lechuga) y **Cafe Cuevas** (emprendimiento 4, Samaipata: café).

Estado inicial del inventario (antes de empezar):

| Producto | id | Precio | Stock | Nota |
|---|---|---|---|---|
| Arroz grano de oro | 1 | Bs 9.50 / kg | 400 kg | |
| Mani pelado | 2 | Bs 18.00 / kg | 80 kg | |
| Miel de abeja pura | 3 | Bs 45.00 / frasco | 60 | |
| Tomate perita | 4 | Bs 7.00 / kg | 40 kg | lote `TOM-001` **vence el 2026-11-23** |
| Papaya | 5 | Bs 12.00 / unidad | 0 | solo por **encargo** |
| Cafe de Samaipata | 6 | Bs 35.00 / bolsa | 12 | **bajo el mínimo** (15) |

> Si la defensa es **después del 2026-11-23**, el lote `TOM-001` ya habrá vencido: el sistema lo marca como `vencido` y lo saca
> del stock (eso también se puede mostrar). En ese caso el tomate disponible será solo el lote nuevo `TOM-1125` del día 2.

---

## Preparación (en la PC, antes de la defensa)

```bat
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas diag
java -DMODE=DEV -jar target\FederCorreo-1.0.jar
```

`diag` debe decir **OK** para la BD y para POP3. Deje la consola visible: muestra cada ciclo y cada correo procesado.

---

## Día 1 · Lunes — El gerente revisa el sistema

**A** `josuecovarrubias8@gmail.com`

| # | Asunto | Qué se ve |
|---|---|---|
| 1.1 | `HELP` | Todos los comandos del Administrador, agrupados por caso de uso |
| 1.2 | `LISUSU["*"]` | Usuarios con su rol (incluido el Personal) |
| 1.3 | `LISPRM["personal"]` | Qué puede hacer el Personal (leído de `rol_permiso`) |
| 1.4 | `LISALE["activa"]` | **Alertas reales**: café bajo el mínimo y tomate `TOM-001` por vencer |
| 1.5 | `REPINV` | Inventario valorizado y lotes por vencer |

**Mensaje para el ingeniero:** el sistema detecta solo el stock bajo y los vencimientos (tarea periódica cada minuto) y avisa por correo al Personal.

---

## Día 2 · Martes — FEDER compra cosecha a los productores (abastecimiento)

Huerta Jorochito llega al centro de acopio con **200 kg de tomate**; FEDER le paga **Bs 4.20/kg**.
Cafe Cuevas avisa que en diciembre tendrá **50 bolsas** de café.

**P** `cacharrospremium@gmail.com`

| # | Asunto | Qué se ve |
|---|---|---|
| 2.1 | `INSPRC["3","4","disponible","200","kg","2026-11-18","2026-11-19","4.20","Cosecha de noviembre, El Torno"]` | Producción reportada → anote `<prc_tomate>` |
| 2.2 | `INSPRC["4","6","futura","50","bolsa","","2026-12-10","22.00","Cosecha de diciembre, Samaipata"]` | Producción **futura** de café → `<prc_cafe>` |
| 2.3 | `LISPRC["reportada"]` | Las dos producciones esperando validación |
| 2.4 | `VALPRC["<prc_tomate>","validada","Calidad verificada en acopio"]` | Se aprueba la calidad |
| 2.5 | `INSLOT["<prc_tomate>","TOM-1125","200","2026-12-05","4.20"]` | **Entra al inventario** el lote `TOM-1125` (costo Bs 4.20) |
| 2.6 | `INSLOT["<prc_tomate>","TOM-1126","10","2026-12-05","4.20"]` | ERROR: *excede lo validado* (no se puede ingresar más de lo cosechado) |
| 2.7 | `DISPRO["*"]` | Tomate ahora 240 kg; café muestra la producción **futura** de diciembre |
| 2.8 | `TRALOT["TOM-1125"]` | Trazabilidad: Comunidad Jorochito → Huerta Jorochito → cosecha → entrada |

---

## Día 3 · Miércoles — Venta por correo con envío y pago por QR

Marco quiere tomate, miel y café, con envío a su casa.

**C** `covarrubiasjosue27@gmail.com`

| # | Asunto | Qué se ve |
|---|---|---|
| 3.1 | `LISPRO["*"]` | Catálogo con precios y stock (tabla HTML) |
| 3.2 | `VERPRO["4"]` | Ficha del tomate y qué productores lo producen |
| 3.3 | `INSCAR["4","10"]` | 10 kg de tomate al carrito |
| 3.4 | `INSCAR["3","2"]` | 2 frascos de miel |
| 3.5 | `INSCAR["6","1"]` | 1 bolsa de café |
| 3.6 | `MODCAR["4","8"]` | Cambia de idea: mejor 8 kg de tomate |
| 3.7 | `INSCAR["6","50"]` | ERROR **sin sobreventa**: *hay 12 bolsas disponibles y su carrito tendría 51* |
| 3.8 | `LISCAR` | Carrito: tomate 56 + miel 90 + café 35 = **Bs 181.00** |
| 3.9 | `CONCAR["envio","Av. Banzer 5to anillo #210","13545823","Marco Cova","Porton negro"]` | **Reserva el stock** y crea el pedido (+ Bs 15 de envío = **Bs 196.00**) → `<pedido1>` |
| 3.10 | `INSPAG["<pedido1>","qr"]` | Recibe el **QR**, el monto exacto (Bs 196.00) y la glosa (número de pedido) → `<pago1>` |

Al instante el **Personal** recibe el correo **"Pago QR por verificar"** con el pago, el cliente, el monto y los comandos listos.

**P** `cacharrospremium@gmail.com` (revisa la cuenta del banco: el depósito llegó)

| # | Asunto | Qué se ve |
|---|---|---|
| 3.11 | `LISPAG["<pedido1>"]` | Pago QR de Bs 196.00 en `pendiente` |
| 3.12 | `MODPAG["<pago1>","pagado"]` | Marco recibe **"Pago confirmado"**; el pedido pasa a **CONFIRMADO** |

---

## Día 4 · Jueves — Preparación y despacho del pedido

**P** `cacharrospremium@gmail.com`

| # | Asunto | Qué se ve |
|---|---|---|
| 4.1 | `VERPED["<pedido1>"]` | Detalle: productos, pago y entrega |
| 4.2 | `MODPED["<pedido1>","en_preparacion"]` | **Descuenta los lotes**. El tomate sale primero de `TOM-001` (vence antes): *primero vence, primero sale* |
| 4.3 | `PROENT["<pedido1>","2026-11-21 10:00"]` | Programa la entrega (use una fecha **futura**) |
| 4.4 | `MODPED["<pedido1>","listo_entrega"]` | Marco recibe **"Pedido listo para entrega"** |
| 4.5 | `MODENT["<pedido1>","en_camino"]` | El repartidor sale (solo pedidos con envío) |
| 4.6 | `MODENT["<pedido1>","entregada","Marco Cova"]` | Entregado: el pedido se cierra |
| 4.7 | `TRALOT["TOM-001"]` | El lote viejo ahora muestra en qué pedido se vendió |

**C** `covarrubiasjosue27@gmail.com`

| # | Asunto | Qué se ve |
|---|---|---|
| 4.8 | `LISNOT` | Todas las notificaciones que recibió (pago, pedido listo…) |
| 4.9 | `TRAPED["<pedido1>"]` | **De dónde vino lo que compró:** lote → productor → comunidad |

---

## Día 4 · Jueves (tarde) — Venta con recojo en tienda y pago en efectivo

Marco pasa por la tienda a recoger 5 kg de arroz y paga en efectivo.

| # | Remitente | Asunto | Qué se ve |
|---|---|---|---|
| 5.1 | C | `INSCAR["1","5"]` | 5 kg de arroz (Bs 47.50) |
| 5.2 | C | `CONCAR["recojo"]` | Pedido con recojo, sin costo de envío → `<pedido2>` |
| 5.3 | P | `INSPAG["<pedido2>","efectivo","47.50","Recibo 0001"]` | El Personal recibió el dinero en mano: queda **PAGADO** directo y el pedido **CONFIRMADO** |
| 5.4 | P | `MODPED["<pedido2>","en_preparacion"]` | Descuenta el arroz del lote |
| 5.5 | P | `MODPED["<pedido2>","listo_entrega"]` | |
| 5.6 | P | `MODENT["<pedido2>","entregada","Marco Cova"]` | Entregado en tienda |

**Diferencia que conviene explicar:** si el Cliente registra un pago queda *pendiente* hasta que el Personal lo verifique; si el Personal registra efectivo o tarjeta, queda *pagado* porque lo recibió en mano.

---

## Día 5 · Viernes — Un pago que no llegó (pago rechazado y cancelación)

Marco pide maní y dice que pagó por QR, pero el Personal no encuentra el depósito.

| # | Remitente | Asunto | Qué se ve |
|---|---|---|---|
| 6.1 | C | `INSCAR["2","3"]` | 3 kg de maní |
| 6.2 | C | `CONCAR["recojo"]` | Reserva 3 kg → `<pedido3>` |
| 6.3 | C | `INSPAG["<pedido3>","qr"]` | Recibe el QR (Bs 54.00) → `<pago3>` |
| 6.4 | P | `DISPRO["2"]` | El maní muestra **3 kg reservados** |
| 6.5 | P | `MODPAG["<pago3>","rechazado"]` | No llegó el depósito: pago **RECHAZADO** |
| 6.6 | C | `LISPAG["<pedido3>"]` | Marco ve su pago rechazado |
| 6.7 | C | `CANPED["<pedido3>"]` | Marco cancela el pedido y se **libera la reserva** |
| 6.8 | P | `DISPRO["2"]` | El maní vuelve a tener 0 kg reservados |

---

## Semana siguiente — Encargo de papaya para un evento

Marco necesita **30 papayas** para un matrimonio. La papaya no hay en stock: se vende por **encargo**.

| # | Remitente | Asunto | Qué se ve |
|---|---|---|---|
| 7.1 | C | `INSCAR["5","30"]` | ERROR: la papaya es solo por encargo, sugiere `INSENC` |
| 7.2 | C | `INSENC["5","30","2026-12-05","Para un matrimonio"]` | Encargo **solicitado** → `<encargo>` |
| 7.3 | P | `LISENC["solicitado"]` | El Personal ve el pedido especial |
| 7.4 | P | `MODENC["<encargo>","aceptado","Coordinamos con Huerta Jorochito"]` | Encargo **aceptado** |
| 7.5 | P | `INSPRC["3","5","disponible","40","unidad","2026-11-25","2026-11-26","6.00","Papaya para encargo"]` | La huerta entrega 40 papayas → `<prc_papaya>` |
| 7.6 | P | `VALPRC["<prc_papaya>","validada","Fruta en buen estado"]` | |
| 7.7 | P | `INSLOT["<prc_papaya>","PAP-1126","40","2026-12-08","6.00"]` | Entra el lote de papaya |
| 7.8 | P | `MODENC["<encargo>","disponible","Ya llego la papaya"]` | Verifica el stock y **avisa a Marco** por correo |
| 7.9 | C | `CONENC["<encargo>","recojo"]` | El encargo se convierte en pedido (Bs 360.00) → `<pedido4>` |
| 7.10 | P | `INSPAG["<pedido4>","efectivo","180.00","Anticipo 50%"]` | Anticipo: queda **saldo** de Bs 180.00 |
| 7.11 | A | `LISSAL` | **Cuentas por cobrar**: el pedido de papaya con saldo pendiente |
| 7.12 | P | `INSPAG["<pedido4>","efectivo","180.00","Saldo final"]` | Pago completo: el pedido pasa a **CONFIRMADO** |

---

## Cierre de mes — El gerente revisa los números

**A** `josuecovarrubias8@gmail.com`

| # | Asunto | Qué se ve |
|---|---|---|
| 8.1 | `INDFIN["2026-11-01","2026-11-30"]` | Ventas, ticket promedio, cobrado, por cobrar, **costo y margen bruto** (precio de venta vs costo de los lotes) |
| 8.2 | `REPVEN["2026-11-01","2026-11-30"]` | Ventas por producto: tomate, miel, café, arroz, papaya |
| 8.3 | `REPMET["2026-11-01","2026-11-30"]` | Pagos por método: QR pagado, QR **rechazado**, efectivo |
| 8.4 | `REPPED["2026-11-01","2026-11-30"]` | Pedidos por estado (entregado, cancelado…) y origen (inmediato / encargo) |
| 8.5 | `REPPRD["2026-11-01","2026-12-31"]` | Producción por comunidad y productor (incluye la futura de café) |
| 8.6 | `REPINV` | Inventario después de la semana |
| 8.7 | `LISALE["activa"]` | Alertas: ¿el tomate quedó bajo el mínimo después de las ventas? |

---

## Si el ingeniero pide ver errores

**Errores de comando** (desde cualquier correo):

| Asunto | Resultado |
|---|---|
| `LISPRO["*"` | Error de sintaxis con la **posición** exacta |
| `LISPRO[*]` | Falta de comillas dobles |
| `LISPROD["*"]` | Comando inexistente: *¿Quiso decir LISPRO?* |
| `INSCAR["abc","2"]` | Tipo inválido: *debe ser un entero positivo* |
| `INSCAR["1"]` | Faltan parámetros, con uso y ejemplo |
| `LISPRO["1";DROP TABLE usuario;--"]` | Intento de inyección SQL rechazado |
| `   lispro [ "*" ]   ` | Funciona: tolera espacios y minúsculas |
| C: `INSUSU["personal","1234567","X","Y","x@y.com","","clave123",""]` | **No autorizado**: un Cliente no crea usuarios |

**Errores de infraestructura** (sin tocar la configuración real):

```bat
copy config.properties prueba.properties
REM en prueba.properties cambie:  db.pass=claveMala   (o  mail.pass=claveMala)
java -DMODE=DEV -Dconfig=prueba.properties -jar target\FederCorreo-1.0.jar
```

Se ve `[ERROR][BD] ...` o `[ERROR][POP3] ... usuario/clave rechazados`, y la línea `[Ciclo #1] BD FALLO | ...`.
Todo queda en `logs\error.log`; cada correo procesado queda en `logs\access.log`.

---

## Volver a empezar

Para repetir la demo desde cero (borra todo y recarga los datos de prueba; después vuelva a crear al Administrador y al Personal y a subir el QR):

```bat
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas sql db\schema.sql
java -cp target\FederCorreo-1.0.jar feder.app.Herramientas sql db\datos_prueba.sql
```
