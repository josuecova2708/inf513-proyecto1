!INC Local Scripts.EAConstants-JScript

/*
 * ===================================================================
 *  FEDER - Análisis de Casos de Uso (diagramas de comunicación)
 *  CU8. Reportes y Estadísticas
 * -------------------------------------------------------------------
 *  Lenguaje: JavaScript (Enterprise Architect)
 *
 *  USO: seleccione en el navegador el paquete donde quiere el análisis
 *  (el mismo de los CU anteriores) y ejecute el script.
 *  Crea la carpeta del CU8 con su diagrama. Si ya existía, la borra y
 *  la vuelve a crear.
 *
 *  Patrón: Interfaz (boundary) -> Controller (control) -> Entidad (BD)
 *    - InterfazCorreo    : el comando llega en el asunto del correo
 *    - ReporteController : ReporteHandler (REPVEN, REPINV, REPPED, REPENC,
 *                          REPMET, REPING) + REPPRD (ProduccionHandler)
 *    - Lote, Producto, Pedido, Encargo, Pago, Produccion : tablas
 *
 *  Un solo actor ("Administrador/Personal"). Todos los reportes reciben
 *  un rango de fechas (salvo inventario, que es a la fecha de hoy).
 *
 *  Cuadrícula (col, fila) — 6 entidades en la col 5 para que el abanico
 *  siga con diagonales suaves:
 *
 *                                                              Lote(5,-2)
 *                                                              Producto(5,-1)
 *    Actor(0,0) - InterfazCorreo(1,0) - ReporteController(2,0) ---- Pedido(5,0)
 *                                                              Encargo(5,1)
 *                                                              Pago(5,2)
 *                                                              Produccion(5,3)
 * ===================================================================
 */

var SOLO = [];   // vacío [] = todos

// Actor único del CU8 (es el mismo del CU2/CU4/CU7; si no existe, se crea)
var ADMIN_PERSONAL = ["Administrador/Personal", "Administrador / Personal"];

var CUS = [

	// ------------------------------------------------------------- CU8
	{
		codigo: "CU8", nombre: "Reportes y Estadísticas",
		actores: ADMIN_PERSONAL,
		objetos: [
			{ id: "ui",  nombre: "InterfazCorreo",    tipo: "boundary", col: 1, fila: 0 },
			{ id: "rc",  nombre: "ReporteController", tipo: "control",  col: 2, fila: 0 },
			{ id: "lot", nombre: "Lote",              tipo: "entity",   col: 5, fila: -2 },
			{ id: "pro", nombre: "Producto",          tipo: "entity",   col: 5, fila: -1 },
			{ id: "ped", nombre: "Pedido",            tipo: "entity",   col: 5, fila: 0 },
			{ id: "enc", nombre: "Encargo",           tipo: "entity",   col: 5, fila: 1 },
			{ id: "pag", nombre: "Pago",              tipo: "entity",   col: 5, fila: 2 },
			{ id: "prd", nombre: "Produccion",        tipo: "entity",   col: 5, fila: 3 }
		],
		mensajes: [
			{ num: "1",   de: "actor", a: "ui",  texto: "reporteVentas(fechaInicio, fechaFin)" },       // REPVEN
			{ num: "1.1", de: "ui",    a: "rc",  texto: "generarVentas(fechaInicio, fechaFin)" },
			{ num: "1.2", de: "rc",    a: "ped", texto: "consultarVentasPorProducto(fechaInicio, fechaFin)" },
			{ num: "2",   de: "actor", a: "ui",  texto: "reporteInventario()" },                        // REPINV
			{ num: "2.1", de: "ui",    a: "rc",  texto: "generarInventario()" },
			{ num: "2.2", de: "rc",    a: "pro", texto: "consultarStockYNivel()" },
			{ num: "2.3", de: "rc",    a: "lot", texto: "consultarPorVencer(dias)" },
			{ num: "3",   de: "actor", a: "ui",  texto: "reportePedidos(fechaInicio, fechaFin)" },      // REPPED
			{ num: "3.1", de: "ui",    a: "rc",  texto: "generarPedidos(fechaInicio, fechaFin)" },
			{ num: "3.2", de: "rc",    a: "ped", texto: "agruparPorEstadoYOrigen(fechaInicio, fechaFin)" },
			{ num: "4",   de: "actor", a: "ui",  texto: "reporteEncargos(fechaInicio, fechaFin)" },     // REPENC
			{ num: "4.1", de: "ui",    a: "rc",  texto: "generarEncargos(fechaInicio, fechaFin)" },
			{ num: "4.2", de: "rc",    a: "enc", texto: "agruparPorEstado(fechaInicio, fechaFin)" },
			{ num: "4.3", de: "rc",    a: "enc", texto: "productosMasEncargados(fechaInicio, fechaFin)" },
			{ num: "5",   de: "actor", a: "ui",  texto: "reporteMetodosPago(fechaInicio, fechaFin)" },  // REPMET
			{ num: "5.1", de: "ui",    a: "rc",  texto: "generarMetodosPago(fechaInicio, fechaFin)" },
			{ num: "5.2", de: "rc",    a: "pag", texto: "agruparPorMetodo(fechaInicio, fechaFin)" },
			{ num: "6",   de: "actor", a: "ui",  texto: "reporteIngresos(fechaInicio, fechaFin)" },     // REPING
			{ num: "6.1", de: "ui",    a: "rc",  texto: "generarIngresos(fechaInicio, fechaFin)" },
			{ num: "6.2", de: "rc",    a: "pag", texto: "sumarPorDia(fechaInicio, fechaFin)" },
			{ num: "7",   de: "actor", a: "ui",  texto: "reporteProduccion(fechaInicio, fechaFin)" },   // REPPRD
			{ num: "7.1", de: "ui",    a: "rc",  texto: "generarProduccion(fechaInicio, fechaFin)" },
			{ num: "7.2", de: "rc",    a: "prd", texto: "agruparPorComunidad(fechaInicio, fechaFin)" }
		]
	}
];

// ================================================================
//  MOTOR (no hace falta tocar nada de aquí para abajo)
// ================================================================
var Y_CENTRO     = 460;  // altura de la fila 0 (hay 2 filas de entidades por encima)
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
