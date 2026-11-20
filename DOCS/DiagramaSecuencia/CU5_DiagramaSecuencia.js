!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU5 - Diagrama de Secuencia - Carrito, Ventas Inmediatas y Encargos (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU5 en version COMPACTA
 *              para Word/informe. Cubre los 4 flujos principales del codigo:
 *                a) Gestion del Carrito de Compras (LISCAR, INSCAR, MODCAR, ELICAR, VACCAR)
 *                b) Confirmar Compra Inmediata (CONCAR -> PedidoServicio.reservarYEntregar
 *                   -> fn_reservar_pedido FEFO con FOR UPDATE sin sobreventa + entrega)
 *                c) Solicitar, consultar y cancelar Encargos (INSENC, ADDENC, LISENC, VERENC, CANENC)
 *                d) Gestionar estado del Encargo (MODENC -> disponible + Notificador) y
 *                   Convertir Encargo en Pedido (CONENC -> reservarYEntregar)
 *              Basado en CorreoService, ProcesadorComandos, CarritoHandler,
 *              EncargoHandler, PedidoServicio, Notificador, Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU5 - Carrito, Ventas y Encargos (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU5 - Carrito, Ventas y Encargos (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Cliente / Personal FEDER",        "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "Carrito + Encargo + PedidoServ",  "Sequence", "");
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
    // (A) CARRITO DE COMPRAS — LISCAR | INSCAR | MODCAR | ELICAR | VACCAR
    //     El carrito es un pedido 'pendiente' con origen='inmediato' sin fila en entrega.
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISCAR | INSCAR[\"prod_id\",\"cant\"] | MODCAR[...] | ELICAR | VACCAR",   n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente) + permisosDe(rolId)",                            n++, false);
    mensaje(proc,  proc, "Autorizacion.puede('carrito') + spec.validar(params)",                                   n++, false);
    mensaje(proc,  hdl,  "CarritoHandler: listar() | agregar() | modificar() | quitar() | vaciar()",               n++, false);
    mensaje(hdl,   db,   "tx: carritoId(FOR UPDATE) [o nuevoPedido('PED-000xxx')] + validarProducto(modalidad y stock)", n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('solo se vende por ENCARGO' | 'Stock insuficiente')",                       n++, true);
    mensaje(hdl,   db,   "UPSERT/UPDATE/DELETE detalle_pedido + PedidoServicio.recalcular(subtotal)",              n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(contenido MI CARRITO con subtotal y disponibilidad)",                       n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (B) CONFIRMAR COMPRA INMEDIATA — CONCAR (Reserva FEFO sin sobreventa)
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: CONCAR[\"recojo|envio\",\"dir?\",\"nit_ci?\",\"razon_social?\",\"ref?\"]",n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('carrito', CREAR) + spec.validar()",                    n++, false);
    mensaje(proc,  hdl,  "CarritoHandler.confirmar(p, ctx) -> PedidoServicio.reservarYEntregar()",                 n++, false);
    mensaje(hdl,   db,   "tx: verifica stock_disponible actual por linea + UPDATE pedido (fecha, nit_ci, razon_social)", n++, false);
    mensaje(hdl,   db,   "SELECT fn_reservar_pedido(carrito, 1440 min) [FEFO por lote con FOR UPDATE -> reserva_lote]", n++, false);
    mensaje(db,    proc, "PSQLException P0001 'Stock insuficiente' [si otro cliente gano la ultima existencia]",   n++, true);
    mensaje(hdl,   db,   "UPDATE costo_envio (si envio: +Bs 15) + INSERT INTO entrega (convierte carrito en pedido real)", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Compra registrada! Stock RESERVADO por 24 horas + resumen + guia INSPAG')",n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: CONCAR[...]', cuerpo, html)",                                     n++, false);

    // =================================================================
    // (C) SOLICITAR Y GESTIONAR ENCARGOS — INSENC | ADDENC | LISENC | VERENC | CANENC
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: INSENC[\"prod\",\"cant\",\"fecha?\",\"obs?\"] | ADDENC | LISENC | VERENC | CANENC", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('encargos') + spec.validar()",                          n++, false);
    mensaje(proc,  hdl,  "EncargoHandler: insertar() | agregar() | listar() | ver() | cancelar()",                 n++, false);
    mensaje(hdl,   db,   "INSENC/ADDENC tx: productoEncargable(modalidad != 'inmediata') + INSERT encargo / detalle_encargo", n++, false);
    mensaje(hdl,   db,   "CANENC tx: SELECT * FROM encargo FOR UPDATE + UPDATE estado='cancelado'",                n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Encargo SOLICITADO' | detalle encargo | 'Encargo cancelado')",             n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (D) DISPONIBILIDAD Y CONVERSION DE ENCARGO — MODENC | CONENC
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: MODENC[\"id\",\"aceptado|rechazado|disponible\",\"obs?\"] | CONENC[\"id\",\"recojo|envio\"]", n++, false);
    mensaje(pop,   proc, "Notificador.iniciar() + procesar() -> EncargoHandler.cambiarEstado() | convertir()",     n++, false);
    mensaje(hdl,   db,   "MODENC tx: valida transicion; si 'disponible' verifica stock_disponible >= cantidad requerida", n++, false);
    mensaje(hdl,   db,   "UPDATE encargo SET estado=? + INSERT INTO notificacion ('encargo_disponible')",          n++, false);
    mensaje(hdl,   smtp, "Notificador.notificar() encola aviso -> enviarPendientes() al terminar tx con exito",    n++, false);
    mensaje(hdl,   db,   "CONENC tx: nuevoPedido('encargo') + copia detalle_encargo a precio vigente + reservarYEntregar()", n++, false);
    mensaje(hdl,   db,   "UPDATE encargo SET estado = 'convertido'",                                               n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Encargo -> DISPONIBLE (cliente notificado)' | 'Encargo convertido en pedido')", n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU5 abierto. ==");
}

main();
