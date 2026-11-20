!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU1 - Diagrama de Secuencia - Gestion de Usuarios (compacto)
 * Author:      Grupo 14 - Moron Decoraciones
 * Purpose:     Genera el diagrama de secuencia del CU1 en version COMPACTA
 *              para Word/informe. Cubre los 3 flujos principales:
 *                a) Listar usuarios (index)
 *                b) Crear / Editar (store/update + validacion + 422)
 *                c) Desactivar (baja logica activo=false)
 *              Basado en UsuarioController, UsuarioRequest, VerificarPermiso,
 *              PermisoService y BitacoraService.
 * Date:        2026-08-09
 *
 * ACTUALIZADO al esquema nuevo (docs/Context/DiagramaClases.sql). Cambios que
 * este diagrama refleja y el anterior no:
 *
 *   1) La autorizacion ya no consulta la tabla `permiso`. Ahora son dos tablas,
 *      `recurso` + `rol_recurso` (4 booleanos por par rol/recurso), leidas por
 *      PermisoService con cache de 5 min.
 *   2) Los recursos van en MAYUSCULA SINGULAR: 'USUARIO', no 'usuarios'. La
 *      comparacion es estricta y sensible a mayusculas: un nombre mal escrito
 *      da 403 permanente.
 *   3) La columna del hash es `usuario.contrasena`, no `password`. El campo de
 *      la API sigue llamandose `password` y UsuarioController lo mapea; el hash
 *      lo hace el cast 'hashed' del modelo. `ci_nit` ya no existe (el dato
 *      fiscal vive en venta.nit_ci) y la tabla no tiene timestamps.
 *   4) Los ids de rol dejaron de estar hardcodeados: se resuelven por nombre,
 *      porque dependen del orden de insercion del seeder.
 *   5) Se agregan las reglas de alcance del Vendedor (solo Clientes) y los dos
 *      guardas de destroy(), que antes no aparecian.
 *
 * USO:
 *   1) Browser EA -> clic en el paquete de diagramas de secuencia.
 *   2) Pega este codigo en el script y presiona Run.
 */

/* ---------------------------------------------------------------
 * Helpers
 * ------------------------------------------------------------- */

function log(msg) { Repository.WriteOutput("Script", msg, 0); }

function crearLifeline(pkg, nombre, tipo, estereotipo) {
    var e = pkg.Elements.AddNew(nombre, tipo);
    if (estereotipo != null && estereotipo != "") e.Stereotype = estereotipo;
    e.Update();
    return e;
}

function ponerEnDiagrama(dia, elem, izq, der, top, bot) {
    var dobj = dia.DiagramObjects.AddNew("l=" + izq + ";r=" + der + ";t=" + top + ";b=" + bot + ";", "");
    dobj.ElementID = elem.ElementID;
    dobj.Update();
    return dobj;
}

function mensaje(origen, destino, nombre, seq, esRetorno) {
    var con = origen.Connectors.AddNew(nombre, "Sequence");
    con.SupplierID = destino.ElementID;
    con.SequenceNo = seq;
    if (esRetorno) con.Stereotype = "return";
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
    log("== Generando secuencia CU1 - Gestion de Usuarios (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU1 - Gestion de Usuarios (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — consistente con CU3) ---
    // El middleware encapsula a PermisoService: no se le da linea propia para
    // no romper el ancho compacto del diagrama en Word.
    var actor = crearLifeline(pkg, "Propietario / Vendedor",       "Actor",    "");
    var vue   = crearLifeline(pkg, "Vue DashboardUsuarios",        "Sequence", "");
    var mw    = crearLifeline(pkg, "Middleware auth:api + permiso", "Sequence", "");
    var api   = crearLifeline(pkg, "UsuarioController",            "Sequence", "");
    var db    = crearLifeline(pkg, "PostgreSQL",                   "Sequence", "database");
    var bit   = crearLifeline(pkg, "BitacoraService",              "Sequence", "");
    pkg.Elements.Refresh();

    // --- Colocarlas --- (pitch 160, cajas 130)
    var t = -70, b = -120;
    ponerEnDiagrama(dia, actor,   20,  150, t, b);
    ponerEnDiagrama(dia, vue,    180,  310, t, b);
    ponerEnDiagrama(dia, mw,     340,  470, t, b);
    ponerEnDiagrama(dia, api,    500,  630, t, b);
    ponerEnDiagrama(dia, db,     660,  790, t, b);
    ponerEnDiagrama(dia, bit,    820,  950, t, b);

    var n = 1;

    // =================================================================
    // (A) LISTAR USUARIOS — index()
    //     El Vendedor solo ve Clientes; el Propietario ve todo y puede
    //     filtrar con ?rol_id=
    // =================================================================
    mensaje(actor, vue,  "abre gestion de usuarios",                                  n++, false);
    mensaje(vue,   mw,   "GET /api/usuarios (Bearer JWT)",                            n++, false);
    mensaje(mw,    mw,   "auth:api + puede(rol,'USUARIO','ver')",                     n++, false);
    mensaje(mw,    db,   "SELECT recurso + rol_recurso [cache 5 min]",                n++, false);
    mensaje(mw,    bit,  "registrar('acceso_denegado','USUARIO') [sin permiso]",      n++, false);
    mensaje(mw,    vue,  "403 {mensaje: 'No tienes permiso para ver USUARIO'}",       n++, true);
    mensaje(mw,    api,  "index() [con permiso]",                                     n++, false);
    mensaje(api,   db,   "SELECT rol del autor + rol WHERE nombre='Cliente' — ids no hardcodeados", n++, false);
    mensaje(api,   bit,  "registrar('acceso_recurso','USUARIO')",                     n++, false);
    mensaje(api,   db,   "SELECT usuario + rol [Vendedor: solo rol Cliente]",         n++, false);
    mensaje(api,   vue,  "{usuarios[]} — sin ci_nit, sin timestamps",                 n++, true);

    // =================================================================
    // (B) CREAR / EDITAR — store() | update() + validacion + 422
    // =================================================================
    mensaje(actor, vue,  "crear / editar usuario (formulario)",                       n++, false);
    mensaje(vue,   mw,   "POST /api/usuarios | PUT /api/usuarios/{usuario} (JWT)",    n++, false);
    mensaje(mw,    mw,   "auth:api + puede(rol,'USUARIO','crear'|'editar')",          n++, false);
    mensaje(mw,    vue,  "403 sin permiso",                                           n++, true);
    mensaje(mw,    api,  "UsuarioRequest valida (mensajes ES) [con permiso]",         n++, false);
    mensaje(api,   db,   "ci/email UNIQUE ignore(id) + rol_id EXISTS en rol",         n++, false);
    mensaje(api,   vue,  "422 {errors} [invalido — password min:6 obligatoria solo en POST]", n++, true);
    mensaje(api,   vue,  "403 'No puedes editar este usuario' [Vendedor sobre no-Cliente]", n++, true);
    mensaje(api,   api,  "password -> contrasena (cast 'hashed'); Vendedor: fuerza rol Cliente", n++, false);
    mensaje(api,   db,   "INSERT|UPDATE usuario (activo = true por defecto)",         n++, false);
    mensaje(api,   bit,  "registrar('crear'|'editar','USUARIO')",                     n++, false);
    mensaje(api,   vue,  "201|200 {usuario}",                                         n++, true);

    // =================================================================
    // (C) DESACTIVAR — destroy() baja logica (activo=false)
    //     No hay DELETE real: bitacora y pedido apuntan a usuario con
    //     ON DELETE RESTRICT, asi que fallaria en cuanto tenga actividad.
    // =================================================================
    mensaje(actor, vue,  "desactivar usuario",                                        n++, false);
    mensaje(vue,   mw,   "DELETE /api/usuarios/{usuario} (Bearer JWT)",               n++, false);
    mensaje(mw,    mw,   "auth:api + puede(rol,'USUARIO','eliminar')",                n++, false);
    mensaje(mw,    api,  "destroy() [con permiso]",                                   n++, false);
    mensaje(api,   vue,  "422 'No puedes desactivarte a ti mismo'",                   n++, true);
    mensaje(api,   vue,  "403 'No puedes desactivar este usuario' [Vendedor sobre no-Cliente]", n++, true);
    mensaje(api,   db,   "UPDATE usuario SET activo=false (baja logica, FK RESTRICT)", n++, false);
    mensaje(api,   bit,  "registrar('eliminar','USUARIO')",                           n++, false);
    mensaje(api,   vue,  "{mensaje: 'Usuario desactivado'}",                          n++, true);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU1 abierto. ==");
}

main();
