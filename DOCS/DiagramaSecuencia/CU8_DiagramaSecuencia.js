!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU8 - Diagrama de Secuencia - Reportes y Estadisticas (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU8 en version COMPACTA
 *              para Word/informe. Cubre los 3 flujos principales del codigo:
 *                a) Reportes Comerciales y Financieros por rango de fechas
 *                   (REPVEN, REPPED, REPENC, REPMET, REPING en ReporteHandler)
 *                b) Reporte de Estado Actual de Inventario y Lotes por Vencer
 *                   (REPINV sobre v_disponibilidad_producto y lotes <= 15 dias)
 *                c) Reporte de Produccion por Comunidad y Emprendimiento
 *                   (REPPRD en ProduccionHandler; Productor ve solo su emprendimiento)
 *              Basado en CorreoService, ProcesadorComandos, ReporteHandler,
 *              ProduccionHandler, Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU8 - Reportes y Estadisticas (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU8 - Reportes y Estadisticas (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Admin / Personal / Productor",    "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "ReporteHandler + ProduccionHdl",  "Sequence", "");
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
    // (A) REPORTES POR RANGO DE FECHAS — REPVEN | REPPED | REPENC | REPMET | REPING
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: REPVEN | REPPED | REPENC | REPMET | REPING [\"fecha_inicio\",\"fecha_fin\"]", n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente) + permisosDe(rolId)",                            n++, false);
    mensaje(proc,  proc, "Autorizacion.puede(ctx, permisos, 'reportes', VER) + spec.validar(fechas)",              n++, false);
    mensaje(proc,  pop,  "Respuesta.error('No autorizado' | 'Parametros invalidos')",                              n++, true);
    mensaje(proc,  hdl,  "ReporteHandler: ventas() | pedidos() | encargos() | metodos() | ingresos()",             n++, false);
    mensaje(hdl,   hdl,  "rango(fi, ff): valida fecha_fin >= fecha_inicio",                                        n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('La fecha fin no puede ser anterior a la fecha inicio')",                   n++, true);
    mensaje(hdl,   db,   "REPVEN: SELECT categoria, producto, sum(cantidad), sum(subtotal) FROM detalle_pedido JOIN pedido IN VENDIDO", n++, false);
    mensaje(hdl,   db,   "REPPED/REPENC: SELECT estado, conteos por origen/tipo_entrega | encargos y top 15 productos encargados", n++, false);
    mensaje(hdl,   db,   "REPMET/REPING: SELECT pagos por metodo_pago y estado | ingresos cobrados por dia + total ingresado", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(titulo con rango + tablas formateadas por TablaFormato)",                   n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, PlantillaHtml)",                              n++, false);

    // =================================================================
    // (B) REPORTE DE INVENTARIO ACTUAL Y VENCIMIENTOS — REPINV
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: REPINV",                                                                  n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('reportes', VER)",                                      n++, false);
    mensaje(proc,  hdl,  "ReporteHandler.inventario(p, ctx)",                                                      n++, false);
    mensaje(hdl,   db,   "SELECT disponible, reservado, minimo, nivel (BAJO|OK), lotes_por_vencer, valor_venta_bs FROM v_disponibilidad_producto", n++, false);
    mensaje(hdl,   db,   "SELECT codigo, producto, disponible, fecha_vencimiento FROM lote WHERE fecha_vencimiento <= CURRENT_DATE + 15", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('REPORTE DE INVENTARIO' + tabla stock + tabla lotes por vencer en 15 dias)",n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: REPINV', cuerpo, PlantillaHtml)",                                 n++, false);

    // =================================================================
    // (C) REPORTE DE PRODUCCION POR COMUNIDAD Y EMPRENDIMIENTO — REPPRD
    //     El Productor solo ve la produccion de su propio emprendimiento.
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: REPPRD[\"fecha_inicio\",\"fecha_fin\"]",                                  n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('produccion', VER) + spec.validar(fechas)",             n++, false);
    mensaje(proc,  hdl,  "ProduccionHandler.reporte(p, ctx)",                                                      n++, false);
    mensaje(hdl,   db,   "SELECT comunidad, emprendimiento, producto, sum(reportada/validada/futura/rechazada/vendida) [Productor: AND usuario_id=?]", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Produccion del fi al ff' + tabla agrupada por comunidad y emprendimiento)",n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: REPPRD[...]', cuerpo, PlantillaHtml)",                            n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU8 abierto. ==");
}

main();
