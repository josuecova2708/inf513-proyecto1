# Flujo 4: Compra Inmediata con Envío, Pago por QR, Despacho FEFO y Trazabilidad (CU5, CU6 y CU3)

## Finalidad del Flujo
Mostrar el flujo comercial principal de extremo a extremo:
1. El **Cliente** explora el catálogo, arma su carrito, valida que el sistema impida sobreventa y confirma su compra con envío a domicilio (`CONCAR`), lo que **reserva el stock en los lotes con bloqueo `FOR UPDATE`**.
2. El **Cliente** solicita pagar con QR (`INSPAG["<pedido>","qr"]`), recibe en su correo la imagen del QR de FEDER con el monto exacto y la glosa, y el **Personal** recibe automáticamente una notificación para verificar el depósito.
3. El **Personal** aprueba el pago (`MODPAG`), prepara el pedido descontando primero los lotes más próximos a vencer (**FEFO**), programa y despacha la entrega (`PROENT`, `MODENT`).
4. El **Cliente** revisa sus notificaciones y consulta la **trazabilidad (`TRAPED`)** para saber de qué comunidad y productor provino cada alimento que compró.

---

## Paso 4.1 — Cliente: Catálogo, Carrito y Reserva de Stock
- **Quién lo ejecuta:** `covarrubiasjosue27@gmail.com`
- **Rol:** **Cliente (`C`)**

### Comandos listos para copiar en el Asunto:

**1. Ver catálogo de productos disponibles y ficha detallada de un producto:**
```text
LISPRO["*"]
```
```text
VERPRO["4"]
```

**2. Agregar productos al carrito (Tomate `id=4`, Miel `id=3`, Café `id=6`):**
```text
INSCAR["4","10"]
```
```text
INSCAR["3","2"]
```
```text
INSCAR["6","1"]
```

**3. Modificar cantidad en el carrito (cambiar Tomate de 10 kg a 8 kg):**
```text
MODCAR["4","8"]
```

**4. Intentar agregar más stock del que existe (demuestra protección contra sobreventa):**
```text
INSCAR["6","50"]
```

**5. Revisar el carrito antes de confirmar:**
```text
LISCAR
```

**6. Confirmar el carrito con ENVÍO a domicilio (`CONCAR`):**
*(Reserva el stock en los lotes y genera el pedido. Anote el `<pedido_id>` devuelto)*
```text
CONCAR["envio","Av. Banzer 5to anillo #210","13545823","Marco Cova","Porton negro"]
```

---

## Paso 4.2 — Cliente y Personal: Pago por QR y Verificación Bancaria
- **Quién lo ejecuta:**
  1. **Cliente (`C`):** `covarrubiasjosue27@gmail.com` solicita el pago QR.
  2. **Personal (`P`):** `cacharrospremium@gmail.com` (o **Administrador**) verifica el depósito y lo aprueba.

### Comandos listos para copiar en el Asunto:

**1. [CLIENTE] Solicitar pago por QR del pedido confirmado:**
*(Recibe el QR incrustado en el correo con el monto y número de pedido. Anote el `<pago_id>`)*
```text
INSPAG["<pedido_id>","qr"]
```

**2. [PERSONAL] Listar los pagos del pedido para ver el pago QR en estado `pendiente`:**
```text
LISPAG["<pedido_id>"]
```

**3. [PERSONAL] Confirmar que el depósito QR ingresó a la cuenta de FEDER:**
*(El pago pasa a `pagado`, el pedido pasa automáticamente a `confirmado` y se envía correo al Cliente)*
```text
MODPAG["<pago_id>","pagado"]
```

---

## Paso 4.3 — Personal: Preparación (Descuento FEFO), Programación y Entrega
- **Quién lo ejecuta:** `cacharrospremium@gmail.com` (Personal) o `josueurquidicarrillo@gmail.com` (Admin)
- **Rol:** **Personal (`P`) / Administrador (`A`)**

### Comandos listos para copiar en el Asunto:

**1. Ver el detalle completo del pedido (productos, pago confirmado y datos de entrega):**
```text
VERPED["<pedido_id>"]
```

**2. Pasar el pedido a `en_preparacion` (confirma las reservas y descuenta físicamente los lotes por orden de vencimiento FEFO):**
```text
MODPED["<pedido_id>","en_preparacion"]
```

**3. Programar la fecha y hora de entrega (usar una fecha/hora futura):**
```text
PROENT["<pedido_id>","2026-11-25 10:30"]
```

**4. Marcar el pedido como `listo_entrega` (envía notificación automática al Cliente):**
```text
MODPED["<pedido_id>","listo_entrega"]
```

**5. Despachar el envío (`en_camino`) y luego registrar la recepción (`entregada`):**
```text
MODENT["<pedido_id>","en_camino"]
```
```text
MODENT["<pedido_id>","entregada","Marco Cova"]
```
