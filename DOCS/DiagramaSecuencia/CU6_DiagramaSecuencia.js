!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU6 - Diagrama de Secuencia - Pedidos, Pagos Multicanal, Entregas y Notificaciones (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU6 en version COMPACTA
 *              para Word/informe. Cubre los 4 flujos principales del codigo:
 *                a) Pagos Multicanal y QR (SUBQR/VERQR con Adjuntos MIME, INSPAG en
 *                   efectivo/tarjeta/qr, MODPAG -> revisarPagoCompleto confirma pedido)
 *                b) Procesamiento de Estados del Pedido (LISPED, VERPED, MODPED:
 *                   confirmado -> en_preparacion [fn_confirmar_reservas] -> listo_entrega -> entregado,
 *                   y CANPED [fn_liberar_reservas])
 *                c) Programacion y Despacho de Entregas (LISENT, PROENT, MODENT:
 *                   en_camino | entregada | fallida)
 *                d) Consulta de Notificaciones (LISNOT: lista y marca leida=true)
 *              Basado en CorreoService, Adjuntos, ProcesadorComandos, PedidoHandler,
 *              PagoHandler, EntregaHandler, NotificacionHandler, PedidoServicio,
 *              Notificador, Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU6 - Pedidos, Pagos, Entregas y Notificaciones (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU6 - Pedidos, Pagos y Entregas (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Cliente / Personal / Admin",      "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService + Adjuntos",        "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "Pedido/Pago/Entrega/NotifHandler","Sequence", "");
    var db    = crearLifeline(pkg, "Db / PostgreSQL",                 "Sequence", "database");
    var smtp  = crearLifeline(pkg, "Notificador + EnviadorCorreo",    "Sequence", "");
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
    // (A) PAGOS MULTICANAL Y QR — SUBQR | VERQR | INSPAG | MODPAG | LISPAG
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: SUBQR (+ imagen PNG/JPG adjunta) | VERQR | INSPAG[\"ped\",\"qr|efectivo|tarjeta\"] | MODPAG", n++, false);
    mensaje(pop,   pop,  "Adjuntos.establecer(Adjuntos.extraer(msg))",                                             n++, false);
    mensaje(pop,   proc, "Notificador.iniciar() + procesar(asunto, remitente)",                                    n++, false);
    mensaje(proc,  hdl,  "PagoHandler: subirQr() | verQr() | insertar() | cambiarEstado() | listar()",             n++, false);
    mensaje(hdl,   db,   "SUBQR tx: UPDATE qr_pago SET activo=false + INSERT INTO qr_pago (BYTEA) RETURNING id",   n++, false);
    mensaje(hdl,   db,   "INSPAG tx: verifica metodo habilitado y saldo en v_saldo_pedido (+ qrActivo si metodo='qr')", n++, false);
    mensaje(hdl,   db,   "INSERT INTO pago (Staff efectivo/tarjeta -> 'pagado'; Cliente o QR -> 'pendiente')",     n++, false);
    mensaje(hdl,   smtp, "si QR: Notificador.avisarStaff('Pago QR por verificar') + adjunta imagen QR cid: al cliente", n++, false);
    mensaje(hdl,   db,   "MODPAG('pagado') / pago directo: notificarPago() + PedidoServicio.revisarPagoCompleto()",n++, false);
    mensaje(hdl,   db,   "si saldo_pendiente == 0: UPDATE pedido SET estado='confirmado' + INSERT notificacion",   n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(detalle pago + QR embebido si aplica)",                                     n++, true);
    mensaje(pop,   smtp, "Notificador.enviarPendientes() + EnviadorCorreo.enviar(RE:, html, imagen QR)",           n++, false);

    // =================================================================
    // (B) PROCESAR PEDIDOS — LISPED | VERPED | MODPED | CANPED
    //     Estados: pendiente -> confirmado -> en_preparacion -> listo_entrega -> entregado (| cancelado)
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISPED[\"*\"] | VERPED[\"id\"] | MODPED[\"id\",\"estado\"] | CANPED[\"id\"]", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('pedidos') + spec.validar()",                           n++, false);
    mensaje(proc,  hdl,  "PedidoHandler: listar() | ver() | cambiarEstado() | cancelar()",                         n++, false);
    mensaje(hdl,   db,   "MODPED tx: pedidoPara(FOR UPDATE) + valida secuencia estricta de estado",                n++, false);
    mensaje(hdl,   db,   "si 'en_preparacion': SELECT fn_confirmar_reservas(id, usuario) [genera SALIDAS por lote y revisa stock minimo]", n++, false);
    mensaje(hdl,   db,   "si 'confirmado'|'listo_entrega': UPDATE pedido + Notificador.notificar(cliente)",        n++, false);
    mensaje(hdl,   db,   "CANPED tx: SELECT fn_liberar_reservas(id) + UPDATE pedido='cancelado' + pagos pendientes='anulado'", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla/detalle pedido | 'Pedido -> NUEVO_ESTADO' | 'Pedido CANCELADO')",     n++, true);
    mensaje(pop,   smtp, "Notificador.enviarPendientes() + enviar(remitente, 'RE: ' + asunto, cuerpo, html)",      n++, false);

    // =================================================================
    // (C) ENTREGAS — LISENT | PROENT | MODENT
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISENT[\"*\"] | PROENT[\"ped\",\"fecha_hora\"] | MODENT[\"ped\",\"en_camino|entregada|fallida\"]", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('entregas') + spec.validar()",                          n++, false);
    mensaje(proc,  hdl,  "EntregaHandler: listar() | programar() | cambiarEstado()",                               n++, false);
    mensaje(hdl,   db,   "PROENT tx: valida fecha futura + UPDATE entrega SET fecha_programada=?, despachado_por=?", n++, false);
    mensaje(hdl,   db,   "MODENT tx: exige pedido en 'listo_entrega'; UPDATE entrega (+ si 'entregada' cierra pedido='entregado')", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla entregas | 'Entrega programada' | 'Entrega -> ENTREGADA')",           n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (D) HISTORIAL DE NOTIFICACIONES — LISNOT
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISNOT",                                                                  n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('notificaciones', VER)",                                n++, false);
    mensaje(proc,  hdl,  "NotificacionHandler.listar(p, ctx)",                                                     n++, false);
    mensaje(hdl,   db,   "tx: SELECT ultimas 30 notificaciones del usuario + UPDATE notificacion SET leida=true",  n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('MIS NOTIFICACIONES' + tabla)",                                             n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: LISNOT', cuerpo, html)",                                          n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU6 abierto. ==");
}

main();
