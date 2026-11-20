# Flujo 6: Encargos Anticipados de Productos de Temporada y Pagos Parciales (CU5, CU3, CU6 y CU7)

## Finalidad del Flujo
Demostrar cómo FEDER comercializa productos que no están en stock inmediato (modalidad `encargo`, como la Papaya `id=5` o Lechuga `id=7`):
1. Si el **Cliente** intenta meter un producto de encargo al carrito normal (`INSCAR`), el sistema lo rechaza e indica usar `INSENC`.
2. El **Cliente** crea un encargo con fecha requerida (`INSENC`) y puede sumarle más ítems mientras siga solicitado (`ADDENC`).
3. El **Personal** acepta el encargo (`MODENC["<id>","aceptado"]`), coordina con el productor, ingresa la cosecha al almacén (`INSPRC` → `VALPRC` → `INSLOT`) y marca el encargo como `disponible`, lo que **notifica por correo al Cliente**.
4. El **Cliente** convierte el encargo en un pedido firme (`CONENC`), que reserva el stock.
5. Se realiza un **pago parcial (anticipo 50%)**, el **Administrador** verifica el saldo pendiente en cuentas por cobrar (`LISSAL`), y al pagar la segunda cuota el pedido queda `confirmado`.

---

## Paso 6.1 — Cliente: Solicitar un Encargo Especial
- **Quién lo ejecuta:** `covarrubiasjosue27@gmail.com`
- **Rol:** **Cliente (`C`)**

### Comandos listos para copiar en el Asunto:

**1. Intentar agregar Papaya (`id=5`, modalidad `encargo`) al carrito normal (demuestra validación de modalidad):**
```text
INSCAR["5","30"]
```

**2. Solicitar un encargo de 30 papayas para una fecha futura:**
*(Anote el `<encargo_id>` devuelto)*
```text
INSENC["5","30","2026-12-05","Para evento familiar"]
```

**3. (Opcional) Agregar otro producto al mismo encargo mientras sigue en estado `solicitado` (p. ej. 10 Lechugas `id=7`):**
```text
ADDENC["<encargo_id>","7","10"]
```

**4. Consultar el detalle del encargo solicitado:**
```text
VERENC["<encargo_id>"]
```

---

## Paso 6.2 — Personal: Aceptar el Encargo, Recibir la Cosecha y Habilitar Disponibilidad
- **Quién lo ejecuta:** `cacharrospremium@gmail.com` (Personal) o `josueurquidicarrillo@gmail.com` (Admin)
- **Rol:** **Personal (`P`) / Administrador (`A`)**

### Comandos listos para copiar en el Asunto:

**1. Listar los encargos pendientes en estado `solicitado`:**
```text
LISENC["solicitado"]
```

**2. Aceptar el encargo para coordinar con la huerta productora:**
```text
MODENC["<encargo_id>","aceptado","Coordinado con Huerta Jorochito"]
```

**3. Reportar, validar e ingresar los lotes de Papaya (`id=5`) y Lechuga (`id=7`) entregados por Huerta Jorochito (`id=3`):**
*(Anote `<prc_papaya>` y `<prc_lechuga>`)*
```text
INSPRC["3","5","disponible","40","unidad","2026-11-20","2026-11-20","6.00","Cosecha papaya para encargo"]
```
```text
VALPRC["<prc_papaya>","validada","Fruta seleccionada"]
```
```text
INSLOT["<prc_papaya>","PAP-1126","40","2026-12-08","6.00"]
```
```text
INSPRC["3","7","disponible","20","unidad","2026-11-20","2026-11-20","2.50","Cosecha lechuga para encargo"]
```
```text
VALPRC["<prc_lechuga>","validada","Hortaliza fresca"]
```
```text
INSLOT["<prc_lechuga>","LEC-1126","20","2026-11-30","2.50"]
```

**4. Marcar el encargo como `disponible` (verifica que exista stock suficiente y avisa automáticamente al Cliente por correo):**
```text
MODENC["<encargo_id>","disponible","Ya llegaron los productos de su encargo a FEDER"]
```

---

## Paso 6.3 — Cliente, Personal y Administrador: Convertir en Pedido y Pago en 2 Cuotas
- **Quién lo ejecuta:**
  1. **Cliente (`C`):** `covarrubiasjosue27@gmail.com` convierte el encargo en pedido.
  2. **Personal (`P`):** `cacharrospremium@gmail.com` registra un anticipo del 50%.
  3. **Administrador (`A`):** `josueurquidicarrillo@gmail.com` consulta cuentas por cobrar (`LISSAL`).
  4. **Personal (`P`):** registra el saldo restante y el pedido se confirma.

### Comandos listos para copiar en el Asunto:

**1. [CLIENTE] Convertir el encargo disponible en un pedido firme con recojo en tienda:**
*(Reserva el stock y crea el pedido. Anote el `<pedido_encargo>` y el total, p. ej. Bs 400.00)*
```text
CONENC["<encargo_id>","recojo"]
```

**2. [PERSONAL] Registrar un anticipo del 50% en efectivo (el pedido sigue `pendiente` porque aún queda saldo):**
```text
INSPAG["<pedido_encargo>","efectivo","200.00","Anticipo 50%"]
```

**3. [ADMINISTRADOR] Consultar cuentas por cobrar (muestra el pedido con su monto cobrado y el saldo pendiente):**
```text
LISSAL
```

**4. [PERSONAL] Registrar el pago del saldo restante (al cubrir el 100%, el pedido pasa automáticamente a `confirmado`):**
```text
INSPAG["<pedido_encargo>","efectivo","200.00","Pago saldo final"]
```
