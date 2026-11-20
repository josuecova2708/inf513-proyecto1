!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU1 - Diagrama Procedimental (Diseno) - Gestion de Usuarios
 * Author:      Grupo 14 - Moron Decoraciones
 * Purpose:     Genera el diagrama de clases de DISENO del CU1: los modelos,
 *              controladores, middleware, FormRequest y servicios que
 *              intervienen en la gestion de usuarios y en la autenticacion.
 * Date:        2026-08-09
 *
 * ACTUALIZADO al esquema nuevo (docs/Context/DiagramaClases.sql). El diagrama
 * anterior quedo desfasado en cuatro puntos:
 *
 *   1) La clase «Model» Permiso (accion, recurso, rol_id) YA NO EXISTE. La
 *      matriz de acceso se partio en dos: `Recurso` (que se puede tocar) y
 *      `RolRecurso` (clase asociativa Rol<->Recurso con los 4 booleanos
 *      ver/crear/editar/eliminar y PK COMPUESTA, sin id propio).
 *   2) Usuario.password paso a llamarse `contrasena`. El campo de la API sigue
 *      siendo `password`: lo resuelve Usuario::getAuthPassword() y lo mapea
 *      UsuarioController::conContrasena(). Ademas la clase gana `telefono` y
 *      `direccion`, y pierde `ci_nit` (el dato fiscal vive en venta.nit_ci).
 *   3) Faltaban las piezas que hacen cumplir el control de acceso:
 *      VerificarPermiso (middleware), PermisoService (lee recurso+rol_recurso
 *      con cache de 5 min) y BitacoraService (auditoria).
 *   4) Ninguna de estas tablas tiene timestamps.
 *
 * USO:
 *   1) Browser EA -> clic en el paquete donde quieras el diagrama.
 *   2) Pega este codigo en el script y presiona Run.
 */

/* ---------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------- */

function log(msg) { Repository.WriteOutput("Script", msg, 0); }

/*
 * Crea una clase con sus atributos y operaciones.
 *   attrs   = [[nombre, tipo, visibilidad], ...]
 *   metodos = [[nombre, tipoRetorno, visibilidad], ...]
 */
function crearClase(pkg, nombre, estereotipo, notas, attrs, metodos) {
    var e = pkg.Elements.AddNew(nombre, "Class");
    if (estereotipo != null && estereotipo != "") e.Stereotype = estereotipo;
    if (notas != null && notas != "") e.Notes = notas;
    e.Update();

    var i;
    for (i = 0; i < attrs.length; i++) {
        var a = e.Attributes.AddNew(attrs[i][0], attrs[i][1]);
        a.Visibility = attrs[i][2];
        a.Pos = i;
        a.Update();
    }
    e.Attributes.Refresh();

    for (i = 0; i < metodos.length; i++) {
        var m = e.Methods.AddNew(metodos[i][0], metodos[i][1]);
        m.Visibility = metodos[i][2];
        m.Pos = i;
        m.Update();
    }
    e.Methods.Refresh();

    e.Update();
    return e;
}

/*
 * Coloca la clase en el diagrama. `top` se pasa en positivo y se invierte:
 * en EA el eje Y crece hacia arriba, asi que t y b son negativos.
 * El alto se calcula segun cuantas filas tiene la caja.
 */
function ponerClase(dia, elem, izq, top, ancho) {
    var filas = elem.Attributes.Count + elem.Methods.Count;
    var alto = 45 + filas * 14;

    var dobj = dia.DiagramObjects.AddNew(
        "l=" + izq + ";r=" + (izq + ancho) + ";t=" + (-top) + ";b=" + (-(top + alto)) + ";", "");
    dobj.ElementID = elem.ElementID;
    dobj.Update();
    return dobj;
}

/* Asociacion navegable con cardinalidades: origen = Client, destino = Supplier. */
function asociacion(origen, destino, nombre, cardOrigen, cardDestino) {
    var con = origen.Connectors.AddNew(nombre, "Association");
    con.SupplierID = destino.ElementID;
    con.Direction = "Source -> Destination";
    con.Update();

    con.ClientEnd.Cardinality = cardOrigen;
    con.ClientEnd.Update();
    con.SupplierEnd.Cardinality = cardDestino;
    con.SupplierEnd.Update();

    con.Update();
    origen.Connectors.Refresh();
    return con;
}

/* Dependencia «use»: el controlador/servicio usa a la otra clase. */
function dependencia(origen, destino, nombre) {
    var con = origen.Connectors.AddNew(nombre, "Dependency");
    con.SupplierID = destino.ElementID;
    con.Direction = "Source -> Destination";
    con.Stereotype = "use";
    con.Update();
    origen.Connectors.Refresh();
    return con;
}

/* ---------------------------------------------------------------
 * Main
 * ------------------------------------------------------------- */

function main()
{
    Repository.EnsureOutputVisible("Script");
    Repository.ClearOutput("Script");
    log("== Generando CU1 - Diagrama Procedimental (Gestion de Usuarios) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona en el Browser el paquete destino y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU1 - Diseno Procedimental - Gestion de Usuarios", "Logical");
    dia.Update();
    pkg.Diagrams.Refresh();

    /* =============================================================
     * CAPA DE MODELO
     * ============================================================= */

    // Recurso: lo que se puede tocar. `clave` es el identificador que consume
    // el middleware; va en MAYUSCULA SINGULAR (USUARIO, PRODUCTO, ...).
    var recurso = crearClase(pkg, "Recurso", "Model",
        "Tabla `recurso`. Reemplaza, junto con RolRecurso, a la vieja tabla `permiso`.",
        [
            ["id",          "int",    "Private"],
            ["clave",       "string", "Private"],
            ["nombre",      "string", "Private"],
            ["descripcion", "string", "Private"],
            ["ACCIONES",    "array",  "Public"]
        ],
        [
            ["rolRecursos", "HasMany",       "Public"],
            ["roles",       "BelongsToMany", "Public"]
        ]);

    // RolRecurso: clase asociativa con PK COMPUESTA (rol_id, recurso_id).
    // Sin id propio: usa el trait TienePkCompuesta porque Eloquent asume PK escalar.
    var rolRecurso = crearClase(pkg, "RolRecurso", "Model",
        "Tabla `rol_recurso`. PK compuesta (rol_id, recurso_id), sin id propio. "
        + "Los 4 booleanos arrancan en FALSE: sin seeder, nadie puede hacer nada.",
        [
            ["rol_id",     "int",  "Private"],
            ["recurso_id", "int",  "Private"],
            ["ver",        "bool", "Private"],
            ["crear",      "bool", "Private"],
            ["editar",     "bool", "Private"],
            ["eliminar",   "bool", "Private"]
        ],
        [
            ["rol",     "BelongsTo", "Public"],
            ["recurso", "BelongsTo", "Public"]
        ]);

    var rol = crearClase(pkg, "Rol", "Model",
        "Tabla `rol`. Los ids dependen del orden del seeder, asi que el codigo "
        + "resuelve los roles por NOMBRE, no por id.",
        [
            ["id",          "int",    "Private"],
            ["nombre",      "string", "Private"],
            ["descripcion", "string", "Private"]
        ],
        [
            ["rolRecursos", "HasMany",       "Public"],
            ["recursos",    "BelongsToMany", "Public"],
            ["usuarios",    "HasMany",       "Public"]
        ]);

    // Usuario: `contrasena`, NO `password`. getAuthPassword() se lo explica al guard.
    var usuario = crearClase(pkg, "Usuario", "Model",
        "Tabla `usuario`. Implementa JWTSubject. La columna del hash es "
        + "`contrasena`; el campo de la API sigue siendo `password`.",
        [
            ["id",         "int",    "Private"],
            ["rol_id",     "int",    "Private"],
            ["nombre",     "string", "Private"],
            ["apellido",   "string", "Private"],
            ["ci",         "string", "Private"],
            ["email",      "string", "Private"],
            ["contrasena", "string", "Private"],
            ["telefono",   "string", "Private"],
            ["direccion",  "string", "Private"],
            ["activo",     "bool",   "Private"]
        ],
        [
            ["getAuthPassword",      "string",    "Public"],
            ["getRememberTokenName", "string",    "Public"],
            ["getJWTIdentifier",     "int",       "Public"],
            ["getJWTCustomClaims",   "array",     "Public"],
            ["rol",                  "BelongsTo", "Public"],
            ["bitacoras",            "HasMany",   "Public"],
            ["preferencia",          "HasOne",    "Public"]
        ]);

    var bitacora = crearClase(pkg, "Bitacora", "Model",
        "Tabla `bitacora`. Renombres: evento->accion y detalle->evento. "
        + "usuario_id admite NULL (login_fallido con email inexistente).",
        [
            ["id",         "int",      "Private"],
            ["usuario_id", "int",      "Private"],
            ["fecha",      "datetime", "Private"],
            ["accion",     "string",   "Private"],
            ["evento",     "string",   "Private"],
            ["recurso",    "string",   "Private"],
            ["ip",         "inet",     "Private"]
        ],
        [
            ["usuario", "BelongsTo", "Public"]
        ]);

    /* =============================================================
     * CAPA DE CONTROL: middleware, servicios, validacion
     * ============================================================= */

    var mw = crearClase(pkg, "VerificarPermiso", "middleware",
        "Uso: ->middleware('permiso:USUARIO,editar'). Si no hay permiso, "
        + "registra acceso_denegado en bitacora y responde 403.",
        [],
        [
            ["handle", "Response", "Public"]
        ]);

    var permisoSvc = crearClase(pkg, "PermisoService", "service",
        "Aplana recurso+rol_recurso al array de strings 'RECURSO:accion'. "
        + "Cache de 5 min por rol; comparacion estricta y sensible a mayusculas.",
        [
            ["TTL", "int", "Private"]
        ],
        [
            ["permisosDe",    "array",  "Public"],
            ["puede",         "bool",   "Public"],
            ["olvidar",       "void",   "Public"],
            ["olvidarTodos",  "void",   "Public"],
            ["clave",         "string", "Private"]
        ]);

    var bitacoraSvc = crearClase(pkg, "BitacoraService", "service",
        "registrar(accion, usuarioId, recurso, detalle). El parametro $detalle "
        + "va a la columna `evento`.",
        [],
        [
            ["registrar", "void", "Public"]
        ]);

    var request = crearClase(pkg, "UsuarioRequest", "request",
        "Validacion con mensajes en espanol. rol_id se valida con exists('rol') "
        + "(antes era in([1,2,3])); password es obligatoria solo en POST.",
        [],
        [
            ["authorize", "bool",  "Public"],
            ["rules",     "array", "Public"],
            ["messages",  "array", "Public"]
        ]);

    /* =============================================================
     * CAPA DE CONTROLADORES
     * ============================================================= */

    var usuarioCtrl = crearClase(pkg, "UsuarioController", "controller",
        "CRUD de usuarios. El Vendedor solo alcanza Clientes. destroy() es baja "
        + "logica (activo=false): bitacora y pedido usan ON DELETE RESTRICT.",
        [],
        [
            ["index",         "JsonResponse", "Public"],
            ["store",         "JsonResponse", "Public"],
            ["update",        "JsonResponse", "Public"],
            ["destroy",       "JsonResponse", "Public"],
            ["conContrasena", "array",        "Private"],
            ["esVendedor",    "bool",         "Private"],
            ["rolClienteId",  "int",          "Private"],
            ["payload",       "array",        "Private"]
        ]);

    var authCtrl = crearClase(pkg, "AuthController", "controller",
        "JWT (guard api). login/me/refresh devuelven usuario + permisos ya "
        + "aplanados por PermisoService. registro() crea siempre rol Cliente.",
        [],
        [
            ["login",          "JsonResponse", "Public"],
            ["registro",       "JsonResponse", "Public"],
            ["me",             "JsonResponse", "Public"],
            ["logout",         "JsonResponse", "Public"],
            ["refresh",        "JsonResponse", "Public"],
            ["respuestaAuth",  "JsonResponse", "Private"],
            ["usuarioPayload", "array",        "Private"]
        ]);

    pkg.Elements.Refresh();

    /* =============================================================
     * DISPOSICION — 3 bandas horizontales
     * ============================================================= */

    // Banda 1: matriz de acceso + identidad
    ponerClase(dia, recurso,      30,  40, 210);
    ponerClase(dia, rolRecurso,  290,  40, 210);
    ponerClase(dia, rol,         550,  40, 210);
    ponerClase(dia, usuario,     810,  40, 230);

    // Banda 2: control de acceso y auditoria
    ponerClase(dia, mw,           30, 330, 210);
    ponerClase(dia, permisoSvc,  290, 330, 210);
    ponerClase(dia, bitacoraSvc, 550, 330, 210);
    ponerClase(dia, bitacora,    810, 330, 230);

    // Banda 3: entrada HTTP
    ponerClase(dia, request,      30, 570, 210);
    ponerClase(dia, usuarioCtrl, 330, 570, 250);
    ponerClase(dia, authCtrl,    670, 570, 250);

    /* =============================================================
     * RELACIONES
     * ============================================================= */

    // Modelo <-> modelo (con cardinalidades)
    asociacion(rol,     rolRecurso, "",           "1", "0..*");
    asociacion(recurso, rolRecurso, "",           "1", "0..*");
    asociacion(rol,     usuario,    "clasifica",  "1", "0..*");
    asociacion(usuario, bitacora,   "genera",     "1", "0..*");

    // Control de acceso
    dependencia(mw,         permisoSvc,  "puede(rol, recurso, accion)");
    dependencia(mw,         bitacoraSvc, "acceso_denegado");
    dependencia(permisoSvc, rolRecurso,  "lee la matriz");
    dependencia(bitacoraSvc, bitacora,   "escribe");

    // Controladores
    dependencia(usuarioCtrl, request,     "valida");
    dependencia(usuarioCtrl, usuario,     "");
    dependencia(usuarioCtrl, rol,         "resuelve rol Cliente por nombre");
    dependencia(usuarioCtrl, bitacoraSvc, "");
    dependencia(authCtrl,    usuario,     "");
    dependencia(authCtrl,    rol,         "registro: rol Cliente");
    dependencia(authCtrl,    permisoSvc,  "permisos del token");
    dependencia(authCtrl,    bitacoraSvc, "login_ok / login_fallido");

    log("Clases creadas: 11");

    /* --- Refrescar y abrir --- */
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU1 procedimental abierto. ==");
}

main();
