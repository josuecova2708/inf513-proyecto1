# Flujo 3: Acopio de Producción, Validación, Lotes FEFO e Inventario (CU3 y CU4)

## Finalidad del Flujo
Cubrir todo el ciclo de abastecimiento y control de inventario de FEDER:
1. **Reporte de producción (`INSPRC`):** registrar cosechas ya listas (`disponible`) o cosechas estimadas a futuro (`futura`).
2. **Control de calidad (`VALPRC`):** el Personal de FEDER verifica y valida (o rechaza) la producción reportada.
3. **Ingreso de lotes (`INSLOT`):** una vez validada la producción, se ingresa al almacén con código único, fecha de vencimiento (para despacho **FEFO**: *primero vence, primero sale*) y costo unitario (para calcular margen bruto). El sistema impide ingresar más cantidad que la validada.
4. **Movimientos manuales y alertas (`INSMOV`, `LISALE`, `ATEALE`):** registro de mermas/ajustes y atención de alertas automáticas de stock mínimo o próximo vencimiento.

---

## Paso 3.1 — Reportar Producción Disponible y Futura (CU3)
- **Quién lo ejecuta:**
  - **Personal (`P`):** `cacharrospremium@gmail.com` (cuando el productor entrega en el centro de acopio)
  - **Administrador (`A`):** `josueurquidicarrillo@gmail.com` / `juliocesarbaldiviezomoron@gmail.com`
  - **Productor (`R`):** `productor.pedro@example.com` (solo puede reportar para su propio emprendimiento ID `1`)

### Comandos listos para copiar en el Asunto:

**1. Reportar cosecha DISPONIBLE (200 kg de Tomate perita `id=4` de Huerta Jorochito `id=3`):**
*(Anote el ID de producción `<prc_tomate>` que devuelve la respuesta)*
```text
INSPRC["3","4","disponible","200","kg","2026-11-18","2026-11-19","4.20","Cosecha de noviembre, El Torno"]
```

**2. Reportar cosecha FUTURA (50 bolsas de Café `id=6` de Café Cuevas `id=4` para diciembre):**
*(Anote el ID de producción `<prc_cafe>` que devuelve la respuesta)*
```text
INSPRC["4","6","futura","50","bolsa","","2026-12-10","22.00","Cosecha de diciembre, Samaipata"]
```

**3. Listar todas las producciones en estado `reportada` (pendientes de validación):**
```text
LISPRC["reportada"]
```

---

## Paso 3.2 — Validar Calidad e Ingresar Lotes al Inventario (CU3)
- **Quién lo ejecuta:**
  - **Personal (`P`):** `cacharrospremium@gmail.com`
  - **Administrador (`A`):** `josueurquidicarrillo@gmail.com` / `juliocesarbaldiviezomoron@gmail.com`

### Comandos listos para copiar en el Asunto:

**1. Validar la producción de tomate recibida en acopio:**
*(Reemplace `<prc_tomate>` por el ID obtenido en el paso 3.1, p. ej. `8`)*
```text
VALPRC["<prc_tomate>","validada","Calidad verificada en centro de acopio"]
```

**2. Validar la proyección de producción futura de café:**
```text
VALPRC["<prc_cafe>","validada","Cosecha programada confirmada con finca"]
```

**3. Ingresar el lote físico de tomate al inventario (`INSLOT`):**
*(Registra automáticamente el movimiento de `entrada` y habilita el stock para la venta)*
```text
INSLOT["<prc_tomate>","TOM-1125","200","2026-12-15","4.20"]
```

**4. Intentar ingresar más cantidad sobre la misma producción (demuestra validación de tope de cosecha):**
*(Devuelve ERROR controlado porque los 200 kg ya fueron ingresados)*
```text
INSLOT["<prc_tomate>","TOM-1126","10","2026-12-15","4.20"]
```

**5. Verificar lotes activos y disponibilidad actualizada por producto:**
```text
LISLOT["4"]
```
```text
DISPRO["*"]
```

**6. Consultar la trazabilidad completa del lote recién ingresado:**
```text
TRALOT["TOM-1125"]
```

---

## Paso 3.3 — Movimientos Manuales de Inventario y Alertas (CU4)
- **Quién lo ejecuta:**
  - **Personal (`P`):** `cacharrospremium@gmail.com`
  - **Administrador (`A`):** `josueurquidicarrillo@gmail.com` / `juliocesarbaldiviezomoron@gmail.com`

### Comandos listos para copiar en el Asunto:

**1. Registrar una salida manual por merma en un lote (p. ej. lote `4`):**
```text
INSMOV["4","salida","2","Merma por maduracion en almacen"]
```

**2. Registrar un ajuste de conteo físico (`+` o `-`) o devolución:**
```text
INSMOV["4","ajuste","-0.5","Ajuste por conteo fisico semanal"]
```

**3. Ver el kardex / historial de movimientos de inventario:**
```text
LISMOV["4"]
```
```text
LISMOV["*"]
```

**4. Consultar alertas activas de inventario (stock bajo mínimo o lotes por vencer):**
```text
LISALE["activa"]
```

**5. Marcar una alerta como atendida:**
*(Reemplace `1` por el ID de la alerta mostrada en `LISALE`)*
```text
ATEALE["1"]
```
