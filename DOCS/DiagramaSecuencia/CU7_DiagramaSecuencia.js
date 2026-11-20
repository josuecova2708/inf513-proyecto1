!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU7 - Diagrama de Secuencia - Indicadores Financieros (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU7 en version COMPACTA
 *              para Word/informe. Cubre los 2 flujos principales del codigo:
 *                a) Indicadores Financieros del Periodo (INDFIN: ventas efectivas,
 *                   ticket promedio, pedidos cancelados, cobrado, cuentas por cobrar,
 *                   rentabilidad/margen bruto sobre lotes despachados, inventario
 *                   valorizado a costo y a precio de venta, y cobros por metodo de pago)
 *                b) Cuentas por Cobrar (LISSAL: pedidos reales activos con saldo_pendiente > 0
 *                   sobre la vista v_saldo_pedido)
 *              Basado en CorreoService, ProcesadorComandos, IndicadorHandler,
 *              Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU7 - Indicadores Financieros (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU7 - Indicadores Financieros (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Administrador / Personal FEDER",  "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "IndicadorHandler",                "Sequence", "");
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
    // (A) INDICADORES FINANCIEROS DEL PERIODO — INDFIN["fecha_inicio","fecha_fin"]
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: INDFIN[\"2026-11-01\",\"2026-11-30\"]",                                   n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente) + permisosDe(rolId)",                            n++, false);
    mensaje(proc,  proc, "Autorizacion.puede(ctx, permisos, 'finanzas', VER) + spec.validar(fechas)",              n++, false);
    mensaje(proc,  pop,  "Respuesta.error('No autorizado' | 'Parametros invalidos')",                              n++, true);
    mensaje(proc,  hdl,  "IndicadorHandler.indicadores(p, ctx)",                                                   n++, false);
    mensaje(hdl,   hdl,  "valida fecha_fin >= fecha_inicio",                                                       n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('La fecha fin no puede ser anterior a la fecha inicio')",                   n++, true);
    mensaje(hdl,   db,   "SELECT count(*) pedidos, sum(total) ventas, avg(total) ticket FROM pedido WHERE estado IN VENDIDO", n++, false);
    mensaje(hdl,   db,   "SELECT count(*) cancelados + sum(monto) cobrado (pago='pagado') + sum(saldo_pendiente) v_saldo_pedido", n++, false);
    mensaje(hdl,   db,   "SELECT ingreso y costo FROM reserva_lote('confirmada') JOIN detalle_pedido JOIN lote",   n++, false);
    mensaje(hdl,   db,   "SELECT inventario valorizado (a costo_unitario y a precio_venta) FROM lote WHERE estado<>'vencido'", n++, false);
    mensaje(hdl,   db,   "Db.tabla: SELECT metodo, count(pagos), sum(monto) FROM metodo_pago LEFT JOIN pago GROUP BY mp.id", n++, false);
    mensaje(hdl,   hdl,  "calcula margen = ingreso - costo y porcentaje (margen / ingreso * 100)",                 n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('INDICADORES FINANCIEROS del periodo' + resumen y tabla por metodo)",       n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: INDFIN[...]', cuerpo, PlantillaHtml)",                            n++, false);

    // =================================================================
    // (B) CUENTAS POR COBRAR — LISSAL
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISSAL",                                                                  n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('finanzas', VER)",                                      n++, false);
    mensaje(proc,  hdl,  "IndicadorHandler.saldos(p, ctx)",                                                        n++, false);
    mensaje(hdl,   db,   "Db.tabla: SELECT pedido_id, numero, cliente, estado, total, cobrado, saldo FROM v_saldo_pedido WHERE saldo > 0", n++, false);
    mensaje(db,    hdl,  "TablaFormato.formatear(ResultSet)",                                                      n++, true);
    mensaje(hdl,   pop,  "Respuesta.ok('CUENTAS POR COBRAR' + tabla de pedidos con saldo pendiente)",              n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: LISSAL', cuerpo, PlantillaHtml)",                                 n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU7 abierto. ==");
}

main();
