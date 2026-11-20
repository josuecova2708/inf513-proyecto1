# Índice de Flujos de Trabajo (Workflows) — FEDER por Correo (Grupo 28)

Todos los comandos se envían al correo **`grupo28sa@tecnoweb.org.bo`** colocando el comando exactamente en el **Asunto** del correo (el cuerpo puede ir vacío).

---

## Usuarios Reales Configurados en la Base de Datos

| Rol | Nombre | Correo Remitente | Responsabilidad Principal |
|---|---|---|---|
| **Administrador (A)** | Josue Urquidi | `josueurquidicarrillo@gmail.com` | Gestión de usuarios, roles, matriz de permisos, QR de cobro, finanzas y reportes gerenciales |
| **Administrador (A)** | Julio Baldiviezo | `juliocesarbaldiviezomoron@gmail.com` | Gestión de usuarios, roles, matriz de permisos, QR de cobro, finanzas y reportes gerenciales |
| **Administrador (A)** | Josue Covarrubias | `josuecovarrubias8@gmail.com` | Administrador adicional |
| **Personal (P)** | Personal FEDER | `cacharrospremium@gmail.com` | Acopio, validación de producción, ingreso de lotes, verificación de pagos QR/efectivo, despacho y entregas |
| **Productor (R)** | Pedro Choque | `productor.pedro@example.com` | Emprendimiento *Arrocera Los Angeles* (ID 1): gestiona sus ofertas, reporta cosechas y ve sus lotes |
| **Cliente (C)** | Marco Cova | `covarrubiasjosue27@gmail.com` | Consulta catálogo, arma carrito, realiza pedidos inmediatos o por encargo, registra pagos y ve trazabilidad |
| **No Registrado (N)** | Cualquier correo nuevo | *(cualquier otro gmail)* | Solo puede consultar `HELP` o auto-registrarse como Cliente con `REGCLI` |

---

## Mapa de Archivos en `workflow/`

| Archivo | Flujo de Trabajo | Casos de Uso | Roles que Intervienen | Finalidad |
|---|---|---|---|---|
| [01_onboarding_y_administracion_usuarios.md](01_onboarding_y_administracion_usuarios.md) | **Flujo 1: Registro, Usuarios, Roles, Permisos y QR** | CU1 + QR | No Registrado, Administrador, Cualquier Rol | Auto-registro de nuevos clientes (`REGCLI`), alta de personal/productores (`INSUSU`), matriz dinámica de permisos (`ASIPRM`), perfil (`VERPRF`/`MODPRF`) y carga de imagen QR (`SUBQR`). |
| [02_comunidades_productores_y_catalogo.md](02_comunidades_productores_y_catalogo.md) | **Flujo 2: Comunidades, Emprendimientos, Ofertas y Catálogo** | CU2 + CU4 | Administrador / Personal, Productor | Registrar comunidades agrícolas, dar de alta emprendimientos vinculando cuentas de productor, definir qué productos ofrecen (`INSOFE`) y administrar categorías y productos del catálogo (`INSPRO`). |
| [03_acopio_produccion_lotes_e_inventario.md](03_acopio_produccion_lotes_e_inventario.md) | **Flujo 3: Producción, Validación, Lotes FEFO y Alertas** | CU3 + CU4 | Productor, Personal / Administrador | El productor o personal reporta cosecha disponible o futura (`INSPRC`), el personal valida calidad (`VALPRC`), ingresa lotes con vencimiento y costo (`INSLOT`), ajusta mermas (`INSMOV`) y atiende alertas (`LISALE`/`ATEALE`). |
| [04_compra_inmediata_qr_y_despacho.md](04_compra_inmediata_qr_y_despacho.md) | **Flujo 4: Compra Inmediata con Envío, Pago QR y Entrega** | CU5 + CU6 + CU3 | Cliente, Personal | Flujo comercial completo: catálogo (`LISPRO`), carrito (`INSCAR`), reserva de stock sin sobreventa (`CONCAR`), cobro con imagen QR (`INSPAG`), verificación bancaria (`MODPAG`), descuento FEFO de lotes (`MODPED`), envío (`PROENT`/`MODENT`) y trazabilidad (`TRAPED`). |
| [05_compra_recojo_efectivo_y_rechazo.md](05_compra_recojo_efectivo_y_rechazo.md) | **Flujo 5: Compra en Tienda (Efectivo) y Rechazo / Cancelación** | CU5 + CU6 | Cliente, Personal | Dos variantes clave: (A) Compra con recojo en tienda pagada en efectivo directamente al Personal (confirmación inmediata), y (B) Pago QR rechazado (`MODPAG`) seguido de cancelación del pedido (`CANPED`) para demostrar liberación de stock reservado. |
| [06_encargos_anticipados_y_pagos_parciales.md](06_encargos_anticipados_y_pagos_parciales.md) | **Flujo 6: Encargos de Productos de Temporada y Pagos Parciales** | CU5 + CU3 + CU6 + CU7 | Cliente, Personal, Administrador | Solicitud de productos bajo modalidad `encargo` (`INSENC`/`ADDENC`), aceptación (`MODENC`), abastecimiento del lote, aviso automático de disponibilidad, conversión a pedido (`CONENC`) y pago en 2 cuotas verificando cuentas por cobrar (`LISSAL`). |
| [07_finanzas_y_reportes_gerenciales.md](07_finanzas_y_reportes_gerenciales.md) | **Flujo 7: Indicadores Financieros y Reportes Gerenciales** | CU7 + CU8 | Administrador, Personal, Productor | Cierre gerencial: margen bruto real y ticket promedio (`INDFIN`), cuentas por cobrar (`LISSAL`), y los 7 reportes estadísticos (`REPVEN`, `REPINV`, `REPPED`, `REPENC`, `REPMET`, `REPING`, `REPPRD`). |
