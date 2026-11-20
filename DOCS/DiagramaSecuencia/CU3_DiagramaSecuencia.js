!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU3 - Diagrama de Secuencia - Produccion, Lotes y Trazabilidad (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU3 en version COMPACTA
 *              para Word/informe. Cubre los 3 flujos principales del codigo:
 *                a) Reportar y listar produccion disponible o futura (INSPRC, LISPRC)
 *                b) Validar o rechazar produccion reportada (VALPRC con FOR UPDATE)
 *                c) Ingresar lotes al inventario (INSLOT -> fn_ingresar_lote + aviso
 *                   de encargos esperando) y consultar trazabilidad (LISLOT, TRALOT, TRAPED)
 *              Basado en CorreoService, ProcesadorComandos, ProduccionHandler,
 *              LoteHandler, Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU3 - Produccion, Lotes y Trazabilidad (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU3 - Produccion, Lotes y Trazabilidad (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Productor / Personal / Admin",    "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "ProduccionHandler + LoteHandler", "Sequence", "");
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
    // (A) REPORTAR Y LISTAR PRODUCCION — INSPRC | LISPRC
    //     El Productor solo puede reportar/ver produccion de su propio emprendimiento.
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: INSPRC[\"emp\",\"prod\",\"disponible|futura\",\"cant\",\"unid\",...] | LISPRC", n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente) + permisosDe(rolId)",                            n++, false);
    mensaje(proc,  proc, "Autorizacion.puede('produccion', CREAR|VER) + spec.validar(params)",                     n++, false);
    mensaje(proc,  hdl,  "ProduccionHandler.insertar(p, ctx) | listar(p, ctx)",                                    n++, false);
    mensaje(hdl,   hdl,  "valida emp propio (si Productor), fecha_disponible >= fecha_produccion y obligatoria si futura", n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('Solo puede reportar de su emprendimiento' | 'fechas invalidas')",          n++, true);
    mensaje(hdl,   db,   "tx: verifica emprendimiento activo y producto existe + INSERT INTO produccion RETURNING id", n++, false);
    mensaje(hdl,   db,   "LISPRC: SELECT produccion JOIN emprendimiento JOIN producto (+ subconsulta en_lotes)",   n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Produccion registrada Estado: REPORTADA' | tabla produccion)",             n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (B) VALIDAR O RECHAZAR PRODUCCION — VALPRC (Personal / Admin)
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: VALPRC[\"id\",\"validada|rechazada\",\"observacion?\"]",                  n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('produccion', EDITAR) + spec.validar()",                n++, false);
    mensaje(proc,  hdl,  "ProduccionHandler.validar(p, ctx)",                                                      n++, false);
    mensaje(hdl,   db,   "tx: SELECT estado FROM produccion WHERE id = ? FOR UPDATE",                              n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('No existe la produccion' | 'La produccion ya fue validada/rechazada')",    n++, true);
    mensaje(hdl,   db,   "UPDATE produccion SET estado=?, validado_por=?, fecha_validacion=now(), observacion=?",  n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Produccion VALIDADA/RECHAZADA. Siguiente paso: INSLOT')",                  n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: VALPRC[...]', cuerpo, html)",                                     n++, false);

    // =================================================================
    // (C) INGRESAR LOTES Y TRAZABILIDAD — INSLOT | LISLOT | TRALOT | TRAPED
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: INSLOT[\"prod_id\",\"codigo\",\"cant\",\"vence?\",\"costo?\"] | LISLOT | TRALOT | TRAPED", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('lotes'|'pedidos') + spec.validar()",                   n++, false);
    mensaje(proc,  hdl,  "LoteHandler: insertar() | listar() | trazarLote() | trazarPedido()",                     n++, false);
    mensaje(hdl,   db,   "INSLOT tx: SELECT estado, cantidad, ingresado FROM produccion WHERE id=? FOR UPDATE",    n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('primero debe validarse con VALPRC' | 'La cantidad excede lo validado')",   n++, true);
    mensaje(hdl,   db,   "SELECT fn_ingresar_lote(...) [crea lote + movimiento ENTRADA] + count(encargos aceptados)", n++, false);
    mensaje(hdl,   db,   "TRALOT: SELECT origen (comunidad/emp/cosecha) + movimiento_inventario + reserva_lote/pedido", n++, false);
    mensaje(hdl,   db,   "TRAPED: SELECT * FROM v_trazabilidad WHERE pedido = ? [Cliente: valida pedido propio]",  n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Lote ingresado + aviso encargos' | reporte de trazabilidad de origen)",    n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU3 abierto. ==");
}

main();
