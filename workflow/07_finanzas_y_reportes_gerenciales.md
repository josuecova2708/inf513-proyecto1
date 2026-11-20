# Flujo 7: Indicadores Financieros y Reportes Gerenciales (CU7 y CU8)

## Finalidad del Flujo
Brindar a la gerencia de FEDER visibilidad financiera y operativa en tiempo real:
1. **Indicadores financieros (`INDFIN`):** calcula ingresos por ventas, ticket promedio, total cobrado, cuentas por cobrar, **costo real de lo vendido** (según el costo unitario de cada lote despachado), **margen bruto** e inventario valorizado.
2. **Cuentas por cobrar (`LISSAL`):** lista todos los pedidos activos que aún tienen saldo pendiente de pago.
3. **Reportes estadísticos (`REP*`):** ventas por producto, inventario valorizado, pedidos por estado y origen, encargos, métodos de pago, ingresos diarios y producción por comunidad/productor.

---

## Paso 7.1 — Indicadores Financieros y Cuentas por Cobrar (CU7)
- **Quién lo ejecuta:**
  - **Administrador (`A`):** `josueurquidicarrillo@gmail.com` / `juliocesarbaldiviezomoron@gmail.com`
  - **Personal (`P`):** `cacharrospremium@gmail.com`

### Comandos listos para copiar en el Asunto:

**1. Consultar indicadores financieros del mes (ventas, margen bruto, cobrado, por cobrar e inventario valorizado):**
```text
INDFIN["2026-11-01","2026-11-30"]
```

**2. Consultar cuentas por cobrar (pedidos con saldo pendiente):**
```text
LISSAL
```

---

## Paso 7.2 — Reportes Estadísticos y Operativos (CU8)
- **Quién lo ejecuta:**
  - **Administrador (`A`):** `josueurquidicarrillo@gmail.com` / `juliocesarbaldiviezomoron@gmail.com`
  - **Personal (`P`):** `cacharrospremium@gmail.com`

### Comandos listos para copiar en el Asunto:

**1. Reporte de ventas por producto (unidades vendidas e importe total en el rango de fechas):**
```text
REPVEN["2026-11-01","2026-11-30"]
```

**2. Reporte de inventario actual valorizado (stock disponible, reservado, mínimo, lotes por vencer y valor en Bs):**
```text
REPINV
```

**3. Reporte de pedidos agrupados por estado (`entregado`, `confirmado`, `cancelado`...) y por origen (`inmediato` vs `encargo`):**
```text
REPPED["2026-11-01","2026-11-30"]
```

**4. Reporte de encargos por estado y ranking de productos más encargados:**
```text
REPENC["2026-11-01","2026-11-30"]
```

**5. Reporte de pagos agrupados por método (`QR`, `Efectivo`, `Tarjeta`) y estado (`pagado`, `pendiente`, `rechazado`):**
```text
REPMET["2026-11-01","2026-11-30"]
```

**6. Reporte de ingresos efectivamente cobrados día por día:**
```text
REPING["2026-11-01","2026-11-30"]
```

**7. Reporte general de producción por comunidad y emprendimiento (incluye disponible y futura):**
```text
REPPRD["2026-11-01","2026-12-31"]
```

---

## Paso 7.3 — Reporte de Producción para el Productor (CU8 con filtro de propiedad)
- **Quién lo ejecuta:** `productor.pedro@example.com` (o cualquier correo vinculado a un emprendimiento con rol Productor)
- **Rol:** **Productor (`R`)**

### Comando listo para copiar en el Asunto:

**Consultar el reporte de producción (el sistema filtra automáticamente para mostrar únicamente la producción de su propio emprendimiento):**
```text
REPPRD["2026-01-01","2026-12-31"]
```
