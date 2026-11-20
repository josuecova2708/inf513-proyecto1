# Flujo 2: Comunidades, Productores, Ofertas y Catálogo de Productos (CU2 y CU4)

## Finalidad del Flujo
Configurar la base productiva y comercial de FEDER:
1. Registrar las **comunidades agrícolas** de donde provienen los productos (`INSCOM`, `MODCOM`, `LISCOM`).
2. Registrar los **emprendimientos/productores** y vincularlos opcionalmente a un correo de usuario con rol Productor (`INSEMP`, `MODEMP`, `LISEMP`).
3. Administrar el **catálogo comercial** de FEDER: categorías y productos con su modalidad de venta (`inmediata`, `encargo` o `ambas`) y stock mínimo para alertas (`INSCAT`, `INSPRO`, `MODPRO`).
4. Registrar qué productos ofrece cada emprendimiento, su precio de referencia y su temporada (`INSOFE`, `LISOFE`).

---

## Paso 2.1 — Gestión de Comunidades Agrícolas (CU2)
- **Quién lo ejecuta:**
  - **Administrador (`A`):** `josueurquidicarrillo@gmail.com` / `juliocesarbaldiviezomoron@gmail.com`
  - **Personal (`P`):** `cacharrospremium@gmail.com`
  *(Nota: `LISCOM` también puede ser consultado por Productores y Clientes)*

### Comandos listos para copiar en el Asunto:

**1. Listar todas las comunidades (o filtrar por municipio):**
```text
LISCOM["*"]
```
```text
LISCOM["San Julian"]
```

**2. Registrar una nueva comunidad agrícola:**
```text
INSCOM["Comunidad El Progreso","Warnes","Warnes","Productores de granos, citricos y hortalizas"]
```

**3. Modificar un dato de una comunidad (`nombre`, `municipio`, `provincia`, `departamento`, `descripcion`):**
*(Reemplace `5` por el ID devuelto al crear la comunidad)*
```text
MODCOM["5","descripcion","Productores de granos y hortalizas organicas"]
```

**4. Eliminar una comunidad que no tenga emprendimientos asociados:**
```text
ELICOM["5"]
```

---

## Paso 2.2 — Gestión de Emprendimientos / Productores (CU2)
- **Quién lo ejecuta:**
  - **Administrador (`A`) / Personal (`P`):** crean, editan todo y dan de baja emprendimientos.
  - **Productor (`R`):** solo puede ver su propio emprendimiento (`LISEMP`) y editar sus datos de contacto (`responsable`, `telefono`, `direccion`, `latitud`, `longitud`).

### Comandos listos para copiar en el Asunto:

**1. Listar todos los emprendimientos (o filtrar por ID de comunidad):**
```text
LISEMP["*"]
```
```text
LISEMP["1"]
```

**2. Registrar un nuevo emprendimiento agrícola:**
*(Rubros válidos: `agricola`, `artesania`, `alimentos_procesados`, `textil`, `otro`)*
```text
INSEMP["2","Citricos Okinawa","agricola","Kenji Sato","5544332","75544332","Colonia Okinawa Km 12","","-17.2167","-62.8833"]
```

**3. Modificar un dato de un emprendimiento:**
```text
MODEMP["1","telefono","71122339"]
```

**4. Dar de baja (inactivar) y reactivar un emprendimiento (solo Admin/Personal):**
*(Reemplace `5` por el ID del emprendimiento)*
```text
ELIEMP["5"]
```
```text
MODEMP["5","estado","activo"]
```

---

## Paso 2.3 — Administración del Catálogo: Categorías y Productos (CU4)
- **Quién lo ejecuta:**
  - **Administrador (`A`) / Personal (`P`):** crean y modifican categorías y productos.
  - **Todos los roles (`A, P, R, C`):** pueden consultar `LISCAT`, `LISPRO`, `VERPRO`, `DISPRO`.

### Comandos listos para copiar en el Asunto:

**1. Listar categorías del catálogo:**
```text
LISCAT
```

**2. Crear y modificar una categoría (Admin/Personal):**
```text
INSCAT["Tuberculos","Papa, yuca y camote de los valles"]
```
```text
MODCAT["6","descripcion","Papa, yuca, camote y derivados"]
```

**3. Eliminar una categoría sin productos (Admin/Personal):**
```text
ELICAT["6"]
```

**4. Registrar un nuevo producto en el catálogo (Admin/Personal):**
*(Modalidades: `inmediata`, `encargo`, `ambas`)*
```text
INSPRO["4","Naranja criolla","kg","6.00","ambas","25","Malla 1 kg","Naranja dulce de temporada"]
```

**5. Modificar el precio de venta, stock mínimo o modalidad de un producto (Admin/Personal):**
```text
MODPRO["1","precio_venta","9.50"]
```
```text
MODPRO["6","stock_minimo","15"]
```

**6. Consultar el catálogo completo, una ficha de producto y su disponibilidad detallada:**
```text
LISPRO["*"]
```
```text
VERPRO["4"]
```
```text
DISPRO["*"]
```

---

## Paso 2.4 — Ofertas por Emprendimiento (Qué produce cada Productor) (CU2)
- **Quién lo ejecuta:**
  - **Administrador (`A`) / Personal (`P`):** para cualquier emprendimiento.
  - **Productor (`R`):** solo para su propio emprendimiento.

### Comandos listos para copiar en el Asunto:

**1. Ver qué productos ofrece cada emprendimiento (precio de referencia y temporada):**
```text
LISOFE["*"]
```
```text
LISOFE["1"]
```

**2. Registrar o actualizar que el emprendimiento `1` ofrece el producto `1` (Arroz):**
```text
INSOFE["1","1","6.50","todo el ano"]
```

**3. Quitar un producto de la oferta de un emprendimiento (si se requiere):**
```text
ELIOFE["1","2"]
```
```text
INSOFE["1","2","12.00","abril-agosto"]
```
