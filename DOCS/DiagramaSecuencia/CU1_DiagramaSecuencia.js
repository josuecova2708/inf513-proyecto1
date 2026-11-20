!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU1 - Diagrama de Secuencia - Gestion de Usuarios (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU1 en version COMPACTA
 *              para Word/informe. Cubre los flujos principales del codigo:
 *                a) Autoregistro de Cliente (REGCLI) y ayuda contextual (HELP)
 *                b) Listar y consultar usuarios / perfil / roles / permisos
 *                   (LISUSU, VERPRF, LISROL, LISPRM)
 *                c) Crear y editar usuarios / perfil / permisos
 *                   (INSUSU, MODUSU, MODPRF, ASIPRM + BCrypt + Edicion)
 *                d) Dar de baja logica a un usuario (ELIUSU: estado='inactivo')
 *              Basado en CorreoService, ProcesadorComandos (ParserAsunto +
 *              Registro + Autorizacion), UsuarioHandler, AyudaHandler, Db/PostgreSQL
 *              y EnviadorCorreo.
 * Date:        2026-11-19
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

    // --- Lineas de vida (6 — formato compacto para informe) ---
    // ProcesadorComandos encapsula ParserAsunto, Registro y Autorizacion;
    // Db encapsula las consultas preparadas y transacciones sobre PostgreSQL.
    var actor = crearLifeline(pkg, "Admin / Personal / Usuario",      "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "UsuarioHandler + AyudaHandler",   "Sequence", "");
    var db    = crearLifeline(pkg, "Db / PostgreSQL",                 "Sequence", "database");
    var smtp  = crearLifeline(pkg, "EnviadorCorreo + PlantillaHtml",  "Sequence", "");
    pkg.Elements.Refresh();

    // --- Colocarlas --- (pitch 160, cajas 130)
    var t = -70, b = -120;
    ponerEnDiagrama(dia, actor,   20,  150, t, b);
    ponerEnDiagrama(dia, pop,    180,  310, t, b);
    ponerEnDiagrama(dia, proc,   340,  470, t, b);
    ponerEnDiagrama(dia, hdl,    500,  630, t, b);
    ponerEnDiagrama(dia, db,     660,  790, t, b);
    ponerEnDiagrama(dia, smtp,   820,  950, t, b);

    var n = 1;

    // =================================================================
    // (A) AUTOREGISTRO DE CLIENTE Y AYUDA — REGCLI | HELP
    //     REGCLI es Accion.REGISTRO (solo remitentes no registrados);
    //     HELP es Accion.PUBLICO y filtra comandos segun permisos del rol.
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: REGCLI[\"ci\",\"nombre\",\"apellido\",\"tel\",\"pass\",\"dir?\"] | HELP", n++, false);
    mensaje(pop,   proc, "ocultarClaves(asunto) + procesar(asunto, remitente)",                                    n++, false);
    mensaje(proc,  proc, "ParserAsunto.parsear() + REGISTRO.buscar(cmd)",                                          n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente): SELECT usuario JOIN rol WHERE lower(email)=?",   n++, false);
    mensaje(proc,  pop,  "Respuesta.error('Usted ya esta registrado' | 'Cuenta INACTIVA')",                        n++, true);
    mensaje(proc,  hdl,  "spec.validar(params) -> registrarCliente(p, ctx) | ayuda(p, ctx)",                       n++, false);
    mensaje(hdl,   hdl,  "hash(password): valida len >= 6 + BCrypt.hashpw(gensalt(10))",                           n++, false);
    mensaje(hdl,   db,   "tx: SELECT id FROM rol WHERE lower(nombre)='cliente' + INSERT INTO usuario RETURNING id",n++, false);
    mensaje(hdl,   proc, "Respuesta.ok('Bienvenido/a a FEDER, registrado como Cliente id...')",                    n++, true);
    mensaje(proc,  pop,  "Salida(respuesta, 'REGCLI', rol) + Db.terminarSesion()",                                 n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: REGCLI[...\"****\"]', cuerpo, PlantillaHtml)",                    n++, false);

    // =================================================================
    // (B) LISTAR Y CONSULTAR — LISUSU | VERPRF | LISROL | LISPRM
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISUSU[\"*\"] | VERPRF | LISROL | LISPRM[\"rol?\"]",                      n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "contextoDe(email) + permisosDe(rolId): SELECT modulo,ver,crear,editar,eliminar FROM rol_permiso", n++, false);
    mensaje(proc,  pop,  "Respuesta.error('No autorizado: su rol no tiene permiso para ver...') [sin permiso]",    n++, true);
    mensaje(proc,  hdl,  "listar() | verPerfil() | listarRoles() | listarPermisos() [con permiso VER]",            n++, false);
    mensaje(hdl,   db,   "Db.tabla(SELECT usuario JOIN rol / emprendimiento / rol_permiso JOIN permiso)",          n++, false);
    mensaje(db,    hdl,  "TablaFormato.formatear(ResultSet)",                                                      n++, true);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla de usuarios / perfil / roles / matriz de permisos)",                  n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (C) CREAR Y EDITAR — INSUSU | MODUSU | MODPRF | ASIPRM
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: INSUSU[...] | MODUSU[\"id\",\"campo\",\"valor\"] | MODPRF[...] | ASIPRM[...]", n++, false);
    mensaje(pop,   proc, "ocultarClaves() + procesar(asunto, remitente)",                                          n++, false);
    mensaje(proc,  proc, "Autorizacion.puede(ctx, permisos, CREAR|EDITAR) + spec.validar(params)",                 n++, false);
    mensaje(proc,  pop,  "Respuesta.error('Parametros invalidos: Uso / Ejemplo / HELP') [error tipos]",            n++, true);
    mensaje(proc,  hdl,  "insertar() | modificar() | modPerfil() | asignarPermiso()",                              n++, false);
    mensaje(hdl,   hdl,  "EDICION.convertir(campo, valor) + hash(password) si cambia clave",                       n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('No puede cambiar su propio rol/estado' | 'permisos de admin en usuarios')",n++, true);
    mensaje(hdl,   db,   "tx: INSERT INTO usuario | EDICION.aplicar(UPDATE usuario) | UPSERT rol_permiso",         n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Usuario registrado / actualizado / Permiso actualizado')",                 n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asuntoSeguro, cuerpo, html)",                                 n++, false);

    // =================================================================
    // (D) BAJA LOGICA — ELIUSU (estado = 'inactivo', conserva historial)
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: ELIUSU[\"id\"]",                                                          n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede(ELIMINAR en 'usuarios')",                               n++, false);
    mensaje(proc,  hdl,  "eliminar(p, ctx)",                                                                       n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('No puede darse de baja a si mismo' | 'No existe el usuario')",             n++, true);
    mensaje(hdl,   db,   "tx: UPDATE usuario SET estado = 'inactivo' WHERE id = ?",                                n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Usuario dado de baja (estado inactivo). Datos historicos se conservan')",  n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ELIUSU[...]', cuerpo, html)",                                     n++, false);

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
