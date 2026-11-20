!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU2 - Diagrama de Secuencia - Productores y Comunidades Agricolas (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU2 en version COMPACTA
 *              para Word/informe. Cubre los 3 flujos principales del codigo:
 *                a) Gestion de Comunidades Agricolas (LISCOM, INSCOM, MODCOM, ELICOM)
 *                b) Gestion de Productores / Emprendimientos (LISEMP, INSEMP, MODEMP, ELIEMP)
 *                   con alcance por rol (Productor solo ve/edita el suyo y campos permitidos)
 *                c) Gestion de Ofertas de Productos por Emprendimiento (LISOFE, INSOFE, ELIOFE)
 *              Basado en CorreoService, ProcesadorComandos, ComunidadHandler,
 *              EmprendimientoHandler, Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU2 - Productores y Comunidades (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU2 - Productores y Comunidades (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Admin / Personal / Productor",    "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "Comunidad + EmprendimientoHandler","Sequence", "");
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
    // (A) COMUNIDADES AGRICOLAS — LISCOM | INSCOM | MODCOM | ELICOM
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISCOM[\"municipio|*\"] | INSCOM[...] | MODCOM[...] | ELICOM[\"id\"]",    n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente) + permisosDe(rolId)",                            n++, false);
    mensaje(proc,  proc, "Autorizacion.puede(ctx, permisos, 'comunidades') + spec.validar(params)",                n++, false);
    mensaje(proc,  pop,  "Respuesta.error('No autorizado' | 'Parametros invalidos')",                              n++, true);
    mensaje(proc,  hdl,  "ComunidadHandler: listar() | insertar() | modificar() | eliminar()",                     n++, false);
    mensaje(hdl,   db,   "SELECT comunidad LEFT JOIN emprendimiento | INSERT INTO comunidad | EDICION.aplicar()",  n++, false);
    mensaje(hdl,   db,   "ELICOM: SELECT 1 FROM emprendimiento WHERE comunidad_id = ? [verifica sin hijos]",       n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('La comunidad tiene emprendimientos registrados; no se puede eliminar')",   n++, true);
    mensaje(hdl,   db,   "DELETE FROM comunidad WHERE id = ?",                                                     n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla comunidades | 'Comunidad registrada / actualizada / eliminada')",     n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (B) PRODUCTORES / EMPRENDIMIENTOS — LISEMP | INSEMP | MODEMP | ELIEMP
    //     El Productor solo ve y modifica SU propio emprendimiento y solo
    //     los campos: responsable, telefono, direccion, latitud, longitud.
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISEMP[\"*\"] | INSEMP[...] | MODEMP[\"id\",\"campo\",\"val\"] | ELIEMP[\"id\"]", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('emprendimientos') + spec.validar()",                   n++, false);
    mensaje(proc,  hdl,  "EmprendimientoHandler: listar() | insertar() | modificar() | eliminar()",                n++, false);
    mensaje(hdl,   db,   "LISEMP: SELECT emprendimiento JOIN comunidad [Productor: AND e.usuario_id = ctx.id]",    n++, false);
    mensaje(hdl,   db,   "INSEMP/MODEMP usuario: usuarioProductor(email) -> verifica usuario existe y rol='Productor'", n++, false);
    mensaje(hdl,   hdl,  "MODEMP: verificarPropio(ctx, id) + valida CAMPOS_PRODUCTOR si ctx.esProductor()",        n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('Solo puede operar sobre su propio emprendimiento' | 'campo no permitido')",n++, true);
    mensaje(hdl,   db,   "tx: INSERT INTO emprendimiento | EDICION.aplicar() | UPDATE estado='inactivo' (ELIEMP)", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla | 'Emprendimiento registrado / actualizado / dado de baja')",         n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (C) OFERTAS POR EMPRENDIMIENTO — LISOFE | INSOFE | ELIOFE
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISOFE[\"emp_id|*\"] | INSOFE[\"emp\",\"prod\",\"precio\",\"temp\"] | ELIOFE", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('ofertas') + spec.validar()",                           n++, false);
    mensaje(proc,  hdl,  "listarOfertas() | insertarOferta() | eliminarOferta()",                                  n++, false);
    mensaje(hdl,   hdl,  "verificarPropio(ctx, emprendimiento_id) [si es Productor]",                              n++, false);
    mensaje(hdl,   db,   "SELECT emprendimiento_producto JOIN emprendimiento JOIN producto",                       n++, false);
    mensaje(hdl,   db,   "INSOFE: INSERT INTO emprendimiento_producto ON CONFLICT DO UPDATE precio_referencia, temporada", n++, false);
    mensaje(hdl,   db,   "ELIOFE: DELETE FROM emprendimiento_producto WHERE emprendimiento_id=? AND producto_id=?",n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla ofertas | 'Oferta registrada' | 'Oferta eliminada')",                 n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU2 abierto. ==");
}

main();
