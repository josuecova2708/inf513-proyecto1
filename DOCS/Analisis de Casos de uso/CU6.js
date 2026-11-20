!INC Local Scripts.EAConstants-JScript

/*
 * ===================================================================
 *  FEDER - Análisis de Casos de Uso (diagramas de comunicación)
 *  CU6. Gestión de Procesar Pedidos, Pagos Multicanal, Entregas y
 *       Notificaciones al Cliente
 * -------------------------------------------------------------------
 *  Lenguaje: JavaScript (Enterprise Architect)
 *
 *  USO: seleccione en el navegador el paquete donde quiere el análisis
 *  (el mismo de los CU anteriores) y ejecute el script.
 *  Crea la carpeta del CU6 con su diagrama. Si ya existía, la borra y
 *  la vuelve a crear.
 *
 *  Patrón: Interfaz (boundary) -> Controller (control) -> Entidad (BD)
 *    - InterfazCorreo   : el comando llega en el asunto del correo
 *    - PedidoController : PedidoHandler + EntregaHandler + NotificacionHandler
 *                         (LISPED, VERPED, MODPED, CANPED, PROENT, MODENT, LISNOT)
 *    - PagoController   : PagoHandler (INSPAG, MODPAG, SUBQR)
 *    - Notificacion, ReservaLote, Pedido, Entrega, Pago, QrPago : tablas
 *
 *  Simplificación: un solo actor ("Cliente/Personal"). LISPAG, VERQR y
 *  LISENT siguen el mismo camino que LISPED.
 *  Cuando el pago completa el total, PagoController le pide a
 *  PedidoController que confirme el pedido y avise al cliente
 *  (5.3 / 6.3): así solo un controller toca Pedido y Notificacion y
 *  ninguna línea se cruza.
 *
 *  Cuadrícula (col, fila) — la interfaz abre en abanico a los dos
 *  controllers (diagonales suaves, ninguna empinada):
 *
 *                                                     Notificacion(4,-3)
 *                                                     ReservaLote(4,-2)
 *                       PedidoController(2,-1) ------ Pedido(4,-1)
 *                      /        |                     Entrega(4,0)
 *    Actor(0,0) - InterfazCorreo(1,0)
 *                      \        |
 *                       PagoController(2,1) --------- Pago(4,1)
 *                                                     QrPago(4,2)
 * ===================================================================
 */

var SOLO = [];   // vacío [] = todos

// Actor único del CU6 (es el mismo del CU5; si no existe, se crea)
var CLIENTE_PERSONAL = ["Cliente/Personal", "Cliente / Personal"];

var CUS = [

	// ------------------------------------------------------------- CU6
	{
		codigo: "CU6", nombre: "Gestión de Procesar Pedidos, Pagos Multicanal, Entregas y Notificaciones al Cliente",
		actores: CLIENTE_PERSONAL,
		objetos: [
			{ id: "ui",    nombre: "InterfazCorreo",   tipo: "boundary", col: 1, fila: 0 },
			{ id: "pedc",  nombre: "PedidoController", tipo: "control",  col: 2, fila: -1 },
			{ id: "pagc",  nombre: "PagoController",   tipo: "control",  col: 2, fila: 1 },
			{ id: "not",   nombre: "Notificacion",     tipo: "entity",   col: 4, fila: -3 },
			{ id: "res",   nombre: "ReservaLote",      tipo: "entity",   col: 4, fila: -2 },
			{ id: "ped",   nombre: "Pedido",           tipo: "entity",   col: 4, fila: -1 },
			{ id: "ent",   nombre: "Entrega",          tipo: "entity",   col: 4, fila: 0 },
			{ id: "pag",   nombre: "Pago",             tipo: "entity",   col: 4, fila: 1 },
			{ id: "qr",    nombre: "QrPago",           tipo: "entity",   col: 4, fila: 2 }
		],
		mensajes: [
			// --- Procesar pedidos
			{ num: "1",    de: "actor", a: "ui",   texto: "listarPedidos(estado)" },                 // LISPED
			{ num: "1.1",  de: "ui",    a: "pedc", texto: "listar(estado)" },
			{ num: "1.2",  de: "pedc",  a: "ped",  texto: "consultar(estado)" },
			{ num: "2",    de: "actor", a: "ui",   texto: "verPedido(id)" },                         // VERPED
			{ num: "2.1",  de: "ui",    a: "pedc", texto: "ver(id)" },
			{ num: "2.2",  de: "pedc",  a: "ped",  texto: "consultarDetalle(id)" },
			{ num: "3",    de: "actor", a: "ui",   texto: "avanzarEstadoPedido(id, estado)" },       // MODPED
			{ num: "3.1",  de: "ui",    a: "pedc", texto: "cambiarEstado(id, estado)" },
			{ num: "3.2",  de: "pedc",  a: "res",  texto: "confirmarReservas(id)" },
			{ num: "3.3",  de: "pedc",  a: "ped",  texto: "actualizarEstado(id, estado)" },
			{ num: "3.4",  de: "pedc",  a: "not",  texto: "notificar(clienteId, mensaje)" },
			{ num: "4",    de: "actor", a: "ui",   texto: "cancelarPedido(id)" },                    // CANPED
			{ num: "4.1",  de: "ui",    a: "pedc", texto: "cancelar(id)" },
			{ num: "4.2",  de: "pedc",  a: "res",  texto: "liberarReservas(id)" },
			{ num: "4.3",  de: "pedc",  a: "ped",  texto: "actualizarEstado(id, cancelado)" },

			// --- Pagos multicanal
			{ num: "5",    de: "actor", a: "ui",   texto: "registrarPago(pedidoId, metodo, monto)" }, // INSPAG
			{ num: "5.1",  de: "ui",    a: "pagc", texto: "registrar(pedidoId, metodo, monto)" },
			{ num: "5.2",  de: "pagc",  a: "pag",  texto: "insertar(pedidoId, metodo, monto)" },
			{ num: "5.3",  de: "pagc",  a: "pedc", texto: "revisarPagoCompleto(pedidoId)" },
			{ num: "5.4",  de: "pedc",  a: "ped",  texto: "confirmar(pedidoId)" },
			{ num: "5.5",  de: "pedc",  a: "not",  texto: "notificar(clienteId, mensaje)" },
			{ num: "6",    de: "actor", a: "ui",   texto: "verificarPago(id, estado)" },             // MODPAG
			{ num: "6.1",  de: "ui",    a: "pagc", texto: "verificar(id, estado)" },
			{ num: "6.2",  de: "pagc",  a: "pag",  texto: "actualizarEstado(id, estado)" },
			{ num: "6.3",  de: "pagc",  a: "pedc", texto: "revisarPagoCompleto(pedidoId)" },
			{ num: "7",    de: "actor", a: "ui",   texto: "subirQR(imagen)" },                       // SUBQR
			{ num: "7.1",  de: "ui",    a: "pagc", texto: "subirQr(imagen)" },
			{ num: "7.2",  de: "pagc",  a: "qr",   texto: "reemplazarVigente(imagen)" },

			// --- Entregas
			{ num: "8",    de: "actor", a: "ui",   texto: "programarEntrega(pedidoId, fechaHora)" }, // PROENT
			{ num: "8.1",  de: "ui",    a: "pedc", texto: "programarEntrega(pedidoId, fechaHora)" },
			{ num: "8.2",  de: "pedc",  a: "ent",  texto: "actualizarFecha(pedidoId, fechaHora)" },
			{ num: "9",    de: "actor", a: "ui",   texto: "actualizarEntrega(pedidoId, estado)" },   // MODENT
			{ num: "9.1",  de: "ui",    a: "pedc", texto: "cambiarEstadoEntrega(pedidoId, estado)" },
			{ num: "9.2",  de: "pedc",  a: "ent",  texto: "actualizarEstado(pedidoId, estado)" },
			{ num: "9.3",  de: "pedc",  a: "ped",  texto: "cerrar(pedidoId)" },

			// --- Notificaciones al cliente
			{ num: "10",   de: "actor", a: "ui",   texto: "verNotificaciones()" },                   // LISNOT
			{ num: "10.1", de: "ui",    a: "pedc", texto: "listarNotificaciones(clienteId)" },
			{ num: "10.2", de: "pedc",  a: "not",  texto: "consultarYMarcarLeidas(clienteId)" }
		]
	}
];

// ================================================================
//  MOTOR (no hace falta tocar nada de aquí para abajo)
// ================================================================
var Y_CENTRO     = 680;  // altura de la fila 0 (hay 3 filas de entidades por encima)
var ALTO_FILA    = 190;  // distancia vertical entre filas (compacto)
var X_INICIO     = 60;
var SEPARACION   = 380;  // distancia horizontal entre columnas (compacto)
var OBJ_LADO     = 60;
var ACT_ANCHO    = 50, ACT_ALTO = 90;
var ALTO_RENGLON = 20;   // separación entre mensajes apilados (línea horizontal)
var DESP_BASE    = 14;   // separación del mensaje más cercano a la línea
var PASO_VERT    = 34;   // separación entre mensajes en una línea vertical
var ESCALON      = 14;   // corrimiento de cada mensaje respecto al anterior (0 = sin escalera)

function log(msg) { Session.Output(msg); }
function sql(v) { return String(v).replace(/'/g, "''"); }

function buscarPaquete(padre, nombre) {
	for (var i = 0; i < padre.Packages.Count; i++) {
		var p = padre.Packages.GetAt(i);
		if (p.Name == nombre) return p;
	}
	return null;
}

function borrarPaquete(padre, nombre) {
	for (var i = padre.Packages.Count - 1; i >= 0; i--) {
		if (padre.Packages.GetAt(i).Name == nombre) {
			padre.Packages.DeleteAt(i, true);
			log("  - se borró la versión anterior: " + nombre);
		}
	}
	padre.Packages.Refresh();
}

function buscarActor(pkg, nombre, excluirId) {
	if (pkg.PackageID == excluirId) return null;
	for (var i = 0; i < pkg.Elements.Count; i++) {
		var e = pkg.Elements.GetAt(i);
		if (e.Type == "Actor" && e.Name == nombre) return e;
	}
	for (var j = 0; j < pkg.Packages.Count; j++) {
		var r = buscarActor(pkg.Packages.GetAt(j), nombre, excluirId);
		if (r != null) return r;
	}
	return null;
}

// Busca el actor en todo el modelo (fuera de la carpeta del CU);
// si no existe, lo crea dentro de la carpeta del CU.
function obtenerActor(nombres, pkgCU) {
	for (var m = 0; m < Repository.Models.Count; m++) {
		var raiz = Repository.Models.GetAt(m);
		for (var a = 0; a < nombres.length; a++) {
			var e = buscarActor(raiz, nombres[a], pkgCU.PackageID);
			if (e != null) return e;
		}
	}
	var nuevo = pkgCU.Elements.AddNew(nombres[0], "Actor");
	nuevo.Update();
	pkgCU.Elements.Refresh();
	log("  (no existía el actor '" + nombres[0] + "', se creó en la carpeta del CU)");
	return nuevo;
}

function colocar(diag, elementId, l, t, ancho, alto) {
	var o = diag.DiagramObjects.AddNew(
		"l=" + l + ";r=" + (l + ancho) + ";t=" + t + ";b=" + (t + alto) + ";", "");
	o.ElementID = elementId;
	o.Update();
	return o;
}

// "login(loginDto)" -> { nombre: "login", args: "loginDto" }
function partirTexto(texto) {
	var m = /^\s*([^(]+)\((.*)\)\s*$/.exec(texto);
	if (m) return { nombre: m[1].replace(/\s+$/, ""), args: m[2] };
	return { nombre: texto, args: "" };
}

function obtenerOperacion(el, nombre) {
	for (var i = 0; i < el.Methods.Count; i++) {
		if (el.Methods.GetAt(i).Name == nombre) return el.Methods.GetAt(i);
	}
	var op = el.Methods.AddNew(nombre, "void");
	op.Visibility = "Public";
	op.Update();
	el.Methods.Refresh();
	return op;
}

// Lado del origen por donde sale la línea: 1=arriba 2=derecha 3=abajo 4=izquierda
function ladoSalida(pOrigen, pDestino) {
	var dx = pDestino.col - pOrigen.col, dy = pDestino.fila - pOrigen.fila;
	if (Math.abs(dx) >= Math.abs(dy)) return dx >= 0 ? 2 : 4;
	return dy > 0 ? 3 : 1;
}

function esVertical(pOrigen, pDestino) {
	return pOrigen.col == pDestino.col;
}

// Asociación base (la línea), recta
function crearEnlace(diag, origen, destino, edge) {
	var c = origen.Connectors.AddNew("", "Association");
	c.SupplierID = destino.ElementID;
	c.Direction = "Unspecified";
	c.Update();
	origen.Connectors.Refresh();

	Repository.Execute(
		"UPDATE t_connector SET Direction='Unspecified', HeadStyle=0, RouteStyle=0, " +
		"SourceIsNavigable='0', DestIsNavigable='0' " +
		"WHERE Connector_ID=" + c.ConnectorID);

	var dl = diag.DiagramLinks.AddNew("", "");
	dl.ConnectorID = c.ConnectorID;
	dl.Geometry = "SX=0;SY=0;EX=0;EY=0;EDGE=" + edge + ";$LLB=;LLT=;LMT=;LMB=;LRT=;LRB=;IRHS=;ILHS=;";
	dl.Style = "Mode=1;EOID=" + destino.ElementID + ";SOID=" + origen.ElementID + ";Color=-1;LWidth=0;Hidden=0;";
	dl.Update();
	diag.DiagramLinks.Refresh();
	return c.ConnectorID;
}

// Mensaje de comunicación enganchado a su asociación base
function crearMensaje(diag, origen, destino, numero, texto, orden, enlaceId, edge, dx, dy) {
	var p = partirTexto(texto);
	var guid = (destino.Type == "Actor") ? "" : obtenerOperacion(destino, p.nombre).MethodGUID;

	// SX/SY = corrimiento del mensaje desde la mitad de la línea
	// (SX hacia la derecha, SY hacia arriba)
	var geo = "SX=" + dx + ";SY=" + dy + ";EX=" + dx + ";EY=" + dy + ";EDGE=" + edge + ";";

	var c = origen.Connectors.AddNew(p.nombre, "Collaboration");
	c.SupplierID = destino.ElementID;
	c.Direction = "Source -> Destination";
	c.SequenceNo = orden;
	c.Update();
	origen.Connectors.Refresh();

	Repository.Execute(
		"UPDATE t_connector SET " +
		"Name='" + sql(p.nombre) + "', " +
		"Direction='Source -> Destination', " +
		"Connector_Type='Collaboration', " +
		"SourceIsAggregate='" + enlaceId + "', " +
		"SourceIsNavigable='0', DestIsNavigable='1', " +
		"HeadStyle=1, RouteStyle=0, " +
		"SeqNo=" + orden + ", " +
		"DiagramID=" + diag.DiagramID + ", " +
		"Top_Start_Label='" + sql(numero + ": " + texto) + "', " +
		"PDATA1='Synchronous', " +
		"PDATA2='" + sql("retval=void;params=;paramsDlg=" + p.args + ";") + "', " +
		"PDATA3='Call', " +
		"PDATA4='" + sql(numero) + "', " +
		"PDATA5='" + geo + "', " +
		"StateFlags='IsReturn=false;'" +
		(guid != "" ? ", StyleEx='" + sql("operation_guid=" + guid + ";") + "'" : "") + " " +
		"WHERE Connector_ID=" + c.ConnectorID);

	var dl = diag.DiagramLinks.AddNew("", "");
	dl.ConnectorID = c.ConnectorID;
	dl.Geometry = geo + "$LLB=;LLT=;LMT=;LMB=;LRT=;LRB=;IRHS=;ILHS=;";
	dl.Style = "Mode=1;EOID=" + destino.ElementID + ";SOID=" + origen.ElementID + ";Color=-1;LWidth=0;Hidden=0;";
	dl.Update();
	diag.DiagramLinks.Refresh();
}

function generarCU(CU, destino) {
	log("--- " + CU.codigo + ". " + CU.nombre);
	var nombreCarpeta = CU.codigo + ". " + CU.nombre;
	borrarPaquete(destino, nombreCarpeta);
	var pkgCU = destino.Packages.AddNew(nombreCarpeta, "");
	pkgCU.TreePos = parseInt(CU.codigo.replace(/\D/g, ""), 10);
	pkgCU.Update();
	destino.Packages.Refresh();

	var elems = {}, pos = {};

	// Actor principal (col 0, fila 0)
	if (CU.actores.length > 0) {
		elems.actor = obtenerActor(CU.actores, pkgCU);
		pos.actor = { col: 0, fila: 0, actor: true };
		log("  actor: " + elems.actor.Name);
	}

	// Objetos y actores secundarios
	for (var i = 0; i < CU.objetos.length; i++) {
		var def = CU.objetos[i];
		pos[def.id] = { col: def.col, fila: def.fila, actor: def.tipo == "actor" };
		if (def.tipo == "actor") {
			elems[def.id] = obtenerActor(def.buscar || [def.nombre], pkgCU);
			log("  actor secundario: " + elems[def.id].Name);
			continue;
		}
		var e = pkgCU.Elements.AddNew(def.nombre, "Object");
		e.Stereotype = def.tipo;
		e.Update();
		elems[def.id] = e;
	}
	pkgCU.Elements.Refresh();

	// Diagrama
	var diag = pkgCU.Diagrams.AddNew("Análisis de " + CU.codigo + ". " + CU.nombre, "Collaboration");
	diag.Update();
	var s = diag.ExtendedStyle;
	if (s.indexOf("HideQuals=") >= 0) s = s.replace(/HideQuals=\d/, "HideQuals=1");
	else s = s + "HideQuals=1;";
	diag.ExtendedStyle = s;
	diag.Update();

	// Colocación: todos los centros alineados a la cuadrícula
	for (var id in pos) {
		if (elems[id] == null) continue;
		var x  = X_INICIO + pos[id].col * SEPARACION;
		var yc = Y_CENTRO + pos[id].fila * ALTO_FILA;
		if (pos[id].actor) colocar(diag, elems[id].ElementID, x + (OBJ_LADO - ACT_ANCHO) / 2, yc - ACT_ALTO / 2, ACT_ANCHO, ACT_ALTO);
		else colocar(diag, elems[id].ElementID, x, yc - OBJ_LADO / 2, OBJ_LADO, OBJ_LADO);
	}

	// Una asociación por par + cuántos mensajes lleva cada una
	var enlaces = {}, total = {};
	for (var p = 0; p < CU.mensajes.length; p++) {
		var mp = CU.mensajes[p];
		if (elems[mp.de] == null || elems[mp.a] == null) continue;
		var clave = [mp.de, mp.a].sort().join("|");
		total[clave] = (total[clave] || 0) + 1;
		if (enlaces[clave] != null) continue;
		var po = pos[mp.de], pd = pos[mp.a];
		enlaces[clave] = crearEnlace(diag, elems[mp.de], elems[mp.a], ladoSalida(po, pd));
	}

	// Mensajes apilados (el primero lejos de la línea, el último pegado)
	var usados = {};
	for (var m = 0; m < CU.mensajes.length; m++) {
		var msg = CU.mensajes[m];
		var cl = [msg.de, msg.a].sort().join("|");
		if (enlaces[cl] == null) { log("  ! mensaje con objeto inexistente: " + msg.num + " " + msg.texto); continue; }
		var idx = usados[cl] || 0;
		usados[cl] = idx + 1;
		var o = pos[msg.de], d = pos[msg.a];
		var n = total[cl], sx, sy;
		if (esVertical(o, d)) {
			// a lo largo de la línea, centrados en su mitad (el primero arriba)
			sy = Math.round(((n - 1) / 2 - idx) * PASO_VERT);
			sx = 0;
		} else {
			// encima de la línea (el primero arriba) y escalonados:
			// cada mensaje se corre ESCALON hacia la derecha
			sy = DESP_BASE + (n - 1 - idx) * ALTO_RENGLON;
			sx = Math.round((idx - (n - 1) / 2) * ESCALON);
		}
		crearMensaje(diag, elems[msg.de], elems[msg.a], msg.num, msg.texto, m + 1,
			enlaces[cl], ladoSalida(o, d), sx, sy);
	}

	diag.Update();
	Repository.SaveDiagram(diag.DiagramID);
	log("  objetos: " + CU.objetos.length + ", mensajes: " + CU.mensajes.length);
	return diag;
}

function main() {
	Repository.EnsureOutputVisible("Script");
	Repository.ClearOutput("Script");
	log("=== FEDER - Análisis de Casos de Uso ===");

	var destino = Repository.GetTreeSelectedPackage();
	if (destino == null) { log("! Seleccione un paquete en el navegador antes de ejecutar el script."); return; }

	var ultimo = null, hechos = 0;
	for (var i = 0; i < CUS.length; i++) {
		if (SOLO.length > 0 && ("|" + SOLO.join("|") + "|").indexOf("|" + CUS[i].codigo + "|") < 0) continue;
		try {
			var d = generarCU(CUS[i], destino);
			if (d != null) { ultimo = d; hechos++; }
		} catch (err) {
			log("! Error en " + CUS[i].codigo + ": " + err.message);
		}
	}

	Repository.RefreshModelView(destino.PackageID);
	if (ultimo != null) Repository.OpenDiagram(ultimo.DiagramID);
	log("=== Listo: " + hechos + " diagrama(s) ===");
}

main();
