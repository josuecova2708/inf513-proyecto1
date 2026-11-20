# Flujo 1: Onboarding, Administración de Usuarios, Permisos Dinámicos y QR (CU1)

## Finalidad del Flujo
Demostrar el control de acceso basado en roles (**RBAC**) desde la base de datos:
1. Cómo un correo desconocido solo puede pedir ayuda (`HELP`) o auto-registrarse como Cliente (`REGCLI`).
2. Cómo el **Administrador** gestiona usuarios de cualquier rol (`INSUSU`, `MODUSU`, `ELIUSU`), modifica permisos en vivo (`ASIPRM`) y configura el código QR oficial de cobro de FEDER (`SUBQR`).
3. Cómo cualquier usuario registrado gestiona sus propios datos (`VERPRF`, `MODPRF`).

---

## Paso 1.1 — Usuario No Registrado (Auto-registro como Cliente)
- **Quién lo ejecuta:** Cualquier persona desde un correo que **no** exista en la BD.
- **Rol:** No registrado (`N`) → pasa a ser **Cliente (`C`)** tras `REGCLI`.

### Comandos listos para copiar en el Asunto:

**1. Consultar comandos disponibles para un desconocido:**
```text
HELP
```

**2. Intentar ver el catálogo sin estar registrado (demuestra bloqueo de seguridad):**
```text
LISPRO["*"]
```

**3. Auto-registrarse como Cliente con el correo remitente:**
```text
REGCLI["9012345","Maria","Lopez","77788899","clave123","Av. Las Americas #120"]
```

---

## Paso 1.2 — Administrador: Gestión de Usuarios, Roles y Matriz de Permisos
- **Quién lo ejecuta:** `josueurquidicarrillo@gmail.com` o `juliocesarbaldiviezomoron@gmail.com`
- **Rol:** **Administrador (`A`)**

### Comandos listos para copiar en el Asunto:

**1. Ver todos los comandos habilitados para el Administrador:**
```text
HELP
```

**2. Consultar la ayuda detallada de un comando específico:**
```text
HELP["INSUSU"]
```

**3. Listar todos los usuarios del sistema (o filtrar por rol):**
```text
LISUSU["*"]
```
```text
LISUSU["administrador"]
```

**4. Listar los roles del sistema y cuántos usuarios tiene cada uno:**
```text
LISROL
```

**5. Registrar un nuevo usuario con cualquier rol (`administrador`, `personal`, `productor`, `cliente`):**
```text
INSUSU["personal","6543210","Luis","Vaca","luis.vaca@example.com","70011223","feder123","Calle Sucre #12"]
```

**6. Modificar un dato de un usuario (`nombre`, `apellido`, `ci`, `email`, `telefono`, `direccion`, `rol`, `estado`, `password`):**
*(Reemplace `12` por el ID del usuario creado)*
```text
MODUSU["12","telefono","79988776"]
```

**7. Dar de baja (inactivar) y reactivar un usuario:**
```text
ELIUSU["12"]
```
```text
MODUSU["12","estado","activo"]
```

**8. Consultar la matriz de permisos de un rol (`ver`, `crear`, `editar`, `eliminar`):**
```text
LISPRM["personal"]
```

**9. Quitar o conceder un permiso en caliente sobre un módulo:**
*(Ejemplo: quitarle al rol `cliente` el permiso de `ver` el módulo `comunidades`, y luego devolvérselo)*
```text
ASIPRM["cliente","comunidades","ver","no"]
```
```text
ASIPRM["cliente","comunidades","ver","si"]
```

---

## Paso 1.3 — Administrador: Configuración del QR de Cobro de FEDER
- **Quién lo ejecuta:** `josueurquidicarrillo@gmail.com` o `juliocesarbaldiviezomoron@gmail.com`
- **Rol:** **Administrador (`A`)**

### Comandos listos para copiar en el Asunto:

**1. Subir o actualizar la imagen del QR de cobro:**
*(Importante: adjunte al correo una imagen `.png` o `.jpg` con el código QR bancario)*
```text
SUBQR
```

**2. Verificar el QR vigente en el sistema (puede hacerlo Administrador, Personal o Cliente):**
```text
VERQR
```

---

## Paso 1.4 — Cualquier Usuario Registrado: Gestión de Mi Perfil
- **Quién lo ejecuta:** `josueurquidicarrillo@gmail.com` (Admin), `cacharrospremium@gmail.com` (Personal) o `covarrubiasjosue27@gmail.com` (Cliente)
- **Rol:** **Todos (`A, P, R, C`)**

### Comandos listos para copiar en el Asunto:

**1. Ver mis datos de perfil y rol actual:**
```text
VERPRF
```

**2. Actualizar mi teléfono o dirección:**
```text
MODPRF["telefono","76543210"]
```
```text
MODPRF["direccion","Av. Cristo Redentor 4to Anillo"]
```
