!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: CU4 - Diagrama de Secuencia - Catalogo, Inventario, Alertas y Concurrencia (compacto)
 * Author:      Grupo 28 - FEDER (FederCorreo)
 * Purpose:     Genera el diagrama de secuencia del CU4 en version COMPACTA
 *              para Word/informe. Cubre los 4 flujos principales del codigo:
 *                a) Gestion de Categorias (LISCAT, INSCAT, MODCAT, ELICAT)
 *                b) Catalogo Unificado y Disponibilidad (LISPRO, VERPRO, DISPRO,
 *                   INSPRO, MODPRO, ELIPRO sobre v_disponibilidad_producto)
 *                c) Movimientos de Inventario con Control de Concurrencia
 *                   (INSMOV con SELECT ... FOR UPDATE + version + fn_revisar_stock_minimo, LISMOV)
 *                d) Alertas de Inventario Bajo y Vencimiento (LISALE, ATEALE)
 *              Basado en CorreoService, ProcesadorComandos, CategoriaHandler,
 *              ProductoHandler, InventarioHandler, Db/PostgreSQL y EnviadorCorreo.
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
    log("== Generando secuencia CU4 - Catalogo, Inventario y Alertas (compacto) ==");

    var pkg = Repository.GetTreeSelectedPackage();
    if (pkg == null) {
        Session.Prompt("Selecciona el paquete de diagramas de secuencia en el Browser y vuelve a ejecutar.", promptOK);
        return;
    }
    log("Paquete destino: " + pkg.Name);

    var dia = pkg.Diagrams.AddNew("CU4 - Catalogo, Inventario y Alertas (Secuencia)", "Sequence");
    dia.Update();
    pkg.Diagrams.Refresh();

    // --- Lineas de vida (6 — formato compacto para informe) ---
    var actor = crearLifeline(pkg, "Admin / Personal / Cliente",      "Actor",    "");
    var pop   = crearLifeline(pkg, "CorreoService (POP3)",            "Sequence", "");
    var proc  = crearLifeline(pkg, "ProcesadorComandos + Autorizacion","Sequence", "");
    var hdl   = crearLifeline(pkg, "Categoria/Producto/InventarioHdl","Sequence", "");
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
    // (A) CATEGORIAS DEL CATALOGO — LISCAT | INSCAT | MODCAT | ELICAT
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISCAT | INSCAT[\"nombre\",\"desc?\"] | MODCAT[...] | ELICAT[\"id\"]",    n++, false);
    mensaje(pop,   proc, "procesar(asunto, remitente)",                                                            n++, false);
    mensaje(proc,  db,   "iniciarSesion() + contextoDe(remitente) + permisosDe(rolId)",                            n++, false);
    mensaje(proc,  proc, "Autorizacion.puede('catalogo') + spec.validar(params)",                                  n++, false);
    mensaje(proc,  hdl,  "CategoriaHandler: listar() | insertar() | modificar() | eliminar()",                     n++, false);
    mensaje(hdl,   db,   "SELECT categoria LEFT JOIN producto | INSERT | EDICION.aplicar() | DELETE (si no tiene productos)", n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('La categoria tiene productos; reasignelos antes de eliminarla')",          n++, true);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla categorias | 'Categoria registrada / actualizada / eliminada')",      n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (B) CATALOGO UNIFICADO Y DISPONIBILIDAD — LISPRO | VERPRO | DISPRO | INSPRO | MODPRO | ELIPRO
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISPRO[\"cat|*\"] | VERPRO[\"id\"] | DISPRO[\"*\"] | INSPRO[...] | MODPRO | ELIPRO", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('catalogo', VER|CREAR|EDITAR|ELIMINAR)",                n++, false);
    mensaje(proc,  hdl,  "ProductoHandler: listar() | ver() | disponibilidad() | insertar() | modificar() | eliminar()", n++, false);
    mensaje(hdl,   db,   "LISPRO/VERPRO: SELECT producto JOIN categoria JOIN v_disponibilidad_producto (+ origen emp)", n++, false);
    mensaje(hdl,   db,   "DISPRO: SELECT stock_feder, reservado, reportada_sin_validar, futura, nivel (BAJO|OK)",  n++, false);
    mensaje(hdl,   db,   "tx: INSERT INTO producto | EDICION.aplicar(UPDATE producto) | UPDATE estado='inactivo'", n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(catalogo / ficha con origen / disponibilidad / confirmacion CRUD)",         n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (C) MOVIMIENTOS DE INVENTARIO Y CONCURRENCIA — INSMOV | LISMOV
    //     Bloquea la fila del lote (FOR UPDATE OF l), incrementa version y
    //     dispara fn_revisar_stock_minimo para generar alertas automaticas.
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: INSMOV[\"lote_id\",\"salida|devolucion|ajuste\",\"cant\",\"motivo\"] | LISMOV", n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('inventario', CREAR|VER) + spec.validar()",             n++, false);
    mensaje(proc,  hdl,  "InventarioHandler.movimiento(p, ctx) | listarMovimientos(p, ctx)",                       n++, false);
    mensaje(hdl,   hdl,  "valida cant != 0 (negativo solo en 'ajuste') y calcula delta",                           n++, false);
    mensaje(hdl,   db,   "tx: SELECT stock_disponible, estado, producto_id FROM lote WHERE id=? FOR UPDATE OF l",  n++, false);
    mensaje(hdl,   pop,  "ErrorNegocio('Stock insuficiente en el lote: lo reservado no se puede descontar')",      n++, true);
    mensaje(hdl,   db,   "UPDATE lote SET stock_disponible=?, version=version+1, estado=CASE('agotado'|'disponible')", n++, false);
    mensaje(hdl,   db,   "INSERT INTO movimiento_inventario + SELECT fn_revisar_stock_minimo(producto_id)",        n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok('Movimiento registrado. Stock disponible del lote: ...' | tabla LISMOV)",   n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    // =================================================================
    // (D) ALERTAS DE STOCK BAJO Y VENCIMIENTO — LISALE | ATEALE
    // =================================================================
    mensaje(actor, pop,  "correo Asunto: LISALE[\"activa|atendida|*\"] | ATEALE[\"id\"]",                          n++, false);
    mensaje(pop,   proc, "procesar() -> Autorizacion.puede('inventario', VER|EDITAR)",                             n++, false);
    mensaje(proc,  hdl,  "InventarioHandler: listarAlertas(p, ctx) | atenderAlerta(p, ctx)",                       n++, false);
    mensaje(hdl,   db,   "SELECT alerta_inventario JOIN producto | UPDATE alerta_inventario SET estado='atendida'",n++, false);
    mensaje(hdl,   pop,  "Respuesta.ok(tabla de alertas | 'Alerta marcada como atendida')",                        n++, true);
    mensaje(pop,   smtp, "enviar(remitente, 'RE: ' + asunto, cuerpo, html)",                                       n++, false);

    log("Mensajes creados: " + (n - 1));

    // --- Refrescar y abrir ---
    dia.DiagramObjects.Refresh();
    dia.DiagramLinks.Refresh();
    dia.Update();
    Repository.RefreshModelView(pkg.PackageID);
    Repository.ReloadDiagram(dia.DiagramID);
    Repository.OpenDiagram(dia.DiagramID);
    log("== Listo. Diagrama CU4 abierto. ==");
}

main();
