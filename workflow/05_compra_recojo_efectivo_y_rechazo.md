# Flujo 5: Compra con Recojo en Tienda (Efectivo) y Caso de Pago Rechazado / Cancelación (CU5 y CU6)

## Finalidad del Flujo
Demostrar dos escenarios operativos fundamentales:
- **Escenario A (Compra rápida en tienda con efectivo):** El Cliente reserva con modalidad `recojo` (sin costo de envío) y paga en efectivo en ventanilla. Cuando el **Personal** registra el pago en efectivo o tarjeta, queda **`pagado` directamente** y el pedido se confirma en un solo paso.
- **Escenario B (Pago QR rechazado y liberación de stock):** El Cliente reserva stock y reporta un pago QR cuyo depósito nunca llegó. El **Personal** rechaza el pago (`MODPAG["<id>","rechazado"]`), el **Cliente** cancela el pedido (`CANPED`) y el stock reservado vuelve inmediatamente a estar disponible.

---

## Escenario A — Compra con Recojo en Tienda y Pago en Efectivo

### 1. [CLIENTE] `covarrubiasjosue27@gmail.com` — Arma carrito y pide para recojo:
**Agregar 5 kg de Arroz grano de oro (`id=1`, Bs 9.50/kg = Bs 47.50):**
```text
INSCAR["1","5"]
```

**Confirmar carrito con modalidad `recojo` (sin recargo de envío):**
*(Anote el `<pedido_recojo>` devuelto)*
```text
CONCAR["recojo"]
```

### 2. [PERSONAL] `cacharrospremium@gmail.com` — Cobra en ventanilla y entrega:
**Registrar el pago en efectivo recibido en mano (queda `pagado` y confirma el pedido de inmediato):**
```text
INSPAG["<pedido_recojo>","efectivo","47.50","Recibo 0001"]
```

**Preparar, marcar listo y entregar en tienda:**
```text
MODPED["<pedido_recojo>","en_preparacion"]
```
```text
MODPED["<pedido_recojo>","listo_entrega"]
```
```text
MODENT["<pedido_recojo>","entregada","Marco Cova"]
```

---

## Escenario B — Pago QR Rechazado, Cancelación y Liberación de Reserva

### 1. [CLIENTE] `covarrubiasjosue27@gmail.com` — Reserva Maní y solicita QR:
**Agregar 3 kg de Maní pelado (`id=2`, Bs 18.00/kg = Bs 54.00) y confirmar:**
*(Anote el `<pedido_mani>` devuelto)*
```text
INSCAR["2","3"]
```
```text
CONCAR["recojo"]
```

**Solicitar pago por QR:**
*(Anote el `<pago_mani>` devuelto)*
```text
INSPAG["<pedido_mani>","qr"]
```

### 2. [PERSONAL] `cacharrospremium@gmail.com` — Verifica reserva y rechaza el pago no recibido:
**Ver cómo los 3 kg de maní aparecen en `stock_reservado`:**
```text
DISPRO["2"]
```

**Rechazar el pago porque la transferencia no ingresó al banco:**
```text
MODPAG["<pago_mani>","rechazado"]
```

### 3. [CLIENTE] `covarrubiasjosue27@gmail.com` — Revisa el rechazo y cancela el pedido:
**Ver el estado rechazado del pago:**
```text
LISPAG["<pedido_mani>"]
```

**Cancelar el pedido pendiente (libera automáticamente los 3 kg reservados):**
```text
CANPED["<pedido_mani>"]
```

### 4. [PERSONAL] `cacharrospremium@gmail.com` — Comprueba que el stock volvió a estar libre:
```text
DISPRO["2"]
```
