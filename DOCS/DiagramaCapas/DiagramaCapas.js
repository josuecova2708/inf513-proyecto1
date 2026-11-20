!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: DiagramaCapas_Grupo28
 * Author: Grupo 28 - INF513
 * Purpose: Generar el Diagrama de Capas del sistema
 *          FEDER (FederCorreo).
 * Date: 2026-08-04
 *
 * FORMATO (igual al diagrama guia):
 *   - Cada capa es un elemento Boundary (rectangulo con el titulo arriba).
 *   - Dentro de cada capa van paquetes con el look por defecto de EA.
 *   - Las flechas son Dependencias punteadas ENTRE PAQUETES (no entre capas):
 *       CorreoService -> ProcesadorComandos -> negocio -> acceso a datos.
 *
 * CAPAS:
 *   1. Presentacion  -> correo entrante, respuesta SMTP y formato HTML
 *   2. Aplicacion    -> procesamiento, analisis y autorizacion de comandos
 *   3. Negocio       -> funcionalidades de los ocho casos de uso
 *   4. Persistencia  -> Db, ConexionDB y PostgreSQL
 *
 * USO:
 *   1. Seleccionar en el Project Browser donde se quiere el diagrama (por ejemplo
 *      el modelo "Model"). Si no hay nada seleccionado se usa el primer modelo.
 *   2. Ejecutar el script: crea el paquete NOMBRE_PAQUETE y mete TODO adentro
 *      (diagrama, capas y paquetes), asi no queda nada suelto en el arbol.
 *
 * NOTA: los paquetes se crean todos dentro del mismo paquete que contiene al
 * diagrama. Es a proposito: si vivieran en subpaquetes, EA les dibuja debajo el
 * texto "(from Capa de ...)". Ademas se apaga ShowNS en el estilo del diagrama.
 *
 * ATENCION: con LIMPIAR_ANTES = true el script BORRA, en el destino, el paquete
 * NOMBRE_PAQUETE de la corrida anterior (con todo su contenido) y los restos de
 * versiones viejas del script (paquetes "Capa de ...", sus boundaries y el
 * diagrama homonimo). Ponerlo en false si no se desea que borre nada.
 */

// ── Configuracion ─────────────────────────────────────────────────────────────
var LIMPIAR_ANTES   = false;                             // ver ATENCION arriba
var NOMBRE_PAQUETE  = "Diagrama de Capas - Grupo 28";    // paquete contenedor en el arbol
var NOMBRE_DIAGRAMA = "Diagrama de capas";               // marco: "pkg Diagrama de capas"

// Geometria (unidades de diagrama de EA)
var CAJA_W      = 120;  // ancho de cada paquete
var CAJA_H      = 70;   // alto de cada paquete
var GAP_X       = 18;   // separacion horizontal entre paquetes
var CAPA_X      = 40;   // x de las capas
var CAPA_Y0     = 40;   // y de la primera capa
var CAPA_H      = 170;  // alto de cada capa
var GAP_CAPAS   = 4;    // las capas van casi pegadas, como en el guia
var OFFSET_CAJA = 50;   // cuanto bajan las cajas dentro de la capa (deja el titulo
                        // arriba y aire abajo para que se vean las flechas)
var MAX_COLS    = 8;    // el dominio tiene 8 cajas en una sola fila
var CAPA_W      = 70 + MAX_COLS * CAJA_W + (MAX_COLS - 1) * GAP_X;   // = 1156

function main()
{
    // ── Destino: lo seleccionado en el Browser, o el primer modelo ────────────
    var destino as EA.Package;
    destino = Repository.GetTreeSelectedPackage();

    if (destino == null) {
        if (Repository.Models.Count == 0) {
            Session.Output("No hay ningun modelo abierto.");
            return;
        }
        destino = Repository.Models.GetAt(0);
    }

    // ── Definicion de las capas ───────────────────────────────────────────────
    // Formato de capa:    [nombre, paquetes]
    // Formato de paquete: [nombre, notas]
    var capas = new Array();

    capas[0] = new Array("Capa de Presentación", new Array(
        new Array("CorreoService", "feder.app.CorreoService: recibe correos, procesa el asunto y prepara la respuesta."),
        new Array("EnviadorCorreo", "feder.infra.EnviadorCorreo: envía respuestas y avisos por SMTP."),
        new Array("PlantillaHtml", "feder.infra.PlantillaHtml: formatea las respuestas HTML.")
    ));

    capas[1] = new Array("Capa de Aplicación", new Array(
        new Array("ProcesadorComandos", "feder.app.ProcesadorComandos: coordina análisis, autorización y ejecución."),
        new Array("Parser y Registro", "feder.parser: analiza el asunto, registra comandos y valida parámetros."),
        new Array("Autorización", "feder.seguridad: identifica el remitente y comprueba permisos por rol.")
    ));

    capas[2] = new Array("Capa de Negocio", new Array(
        new Array("CU1 Usuarios", "UsuarioHandler y AyudaHandler: cuentas, perfiles, roles y permisos."),
        new Array("CU2 Productores", "ComunidadHandler y EmprendimientoHandler: comunidades, productores y ofertas."),
        new Array("CU3 Producción", "ProduccionHandler y LoteHandler: producción, lotes y trazabilidad."),
        new Array("CU4 Inventario", "CategoriaHandler, ProductoHandler e InventarioHandler: catálogo, stock y alertas."),
        new Array("CU5 Comercio", "CarritoHandler y EncargoHandler: carrito, compras y encargos."),
        new Array("CU6 Pedidos", "PedidoHandler, PagoHandler, EntregaHandler y NotificacionHandler: pedidos, pagos y entregas."),
        new Array("CU7 Finanzas", "IndicadorHandler: indicadores financieros y saldos."),
        new Array("CU8 Reportes", "ReporteHandler y reporte de producción: reportes y estadísticas.")
    ));

    capas[3] = new Array("Capa de Persistencia", new Array(
        new Array("Db", "feder.negocio.Db: consultas preparadas, sesiones y transacciones."),
        new Array("ConexionDB", "feder.infra.ConexionDB: obtiene conexiones JDBC."),
        new Array("PostgreSQL", "Base de datos FEDER db_grupo28sa; almacena la información del sistema.")
    ));

    // ── Dependencias entre paquetes (flechas punteadas del guia) ──────────────
    // Formato: [origen, destino]
    var deps = new Array(
        new Array("CorreoService", "ProcesadorComandos"),
        new Array("CorreoService", "EnviadorCorreo"),
        new Array("CorreoService", "PlantillaHtml"),
        new Array("ProcesadorComandos", "Parser y Registro"),
        new Array("ProcesadorComandos", "Autorización"),
        new Array("ProcesadorComandos", "CU1 Usuarios"),
        new Array("ProcesadorComandos", "CU2 Productores"),
        new Array("ProcesadorComandos", "CU3 Producción"),
        new Array("ProcesadorComandos", "CU4 Inventario"),
        new Array("ProcesadorComandos", "CU5 Comercio"),
        new Array("ProcesadorComandos", "CU6 Pedidos"),
        new Array("ProcesadorComandos", "CU7 Finanzas"),
        new Array("ProcesadorComandos", "CU8 Reportes"),
        new Array("Autorización", "Db"),
        new Array("CU1 Usuarios", "Db"),
        new Array("CU2 Productores", "Db"),
        new Array("CU3 Producción", "Db"),
        new Array("CU4 Inventario", "Db"),
        new Array("CU5 Comercio", "Db"),
        new Array("CU6 Pedidos", "Db"),
        new Array("CU7 Finanzas", "Db"),
        new Array("CU8 Reportes", "Db"),
        new Array("Db", "ConexionDB"),
        new Array("ConexionDB", "PostgreSQL")
    );

    // ── Limpieza previa (permite re-ejecutar sin duplicados) ──────────────────
    if (LIMPIAR_ANTES) {
        limpiar(destino, capas);
    }

    // ── Paquete contenedor: todo el diagrama vive adentro ─────────────────────
    var cont as EA.Package;
    cont = crearPaquete(destino, NOMBRE_PAQUETE,
                        "Contenedor del diagrama de capas de FederCorreo (generado por script).");

    // ── Crear el diagrama dentro del contenedor ───────────────────────────────
    var diag as EA.Diagram;
    diag = cont.Diagrams.AddNew(NOMBRE_DIAGRAMA, "Logical");
    diag.Notes = "Vista conceptual de FEDER: agrupa clases por responsabilidad. " +
                 "Las flechas muestran dependencias principales, no una arquitectura estrictamente por capas.";
    diag.Update();
    cont.Diagrams.Refresh();
    apagarNamespace(diag);   // que no aparezca el "(from ...)" debajo de las cajas

    // ── Capas (Boundary) + paquetes ───────────────────────────────────────────
    var elMap = new Object();     // nombre de paquete -> EA.Element
    var y = CAPA_Y0;
    var totalPaquetes = 0;

    for (var i = 0; i < capas.length; i++) {
        var nomCapa = capas[i][0];
        var hijos   = capas[i][1];

        // La capa: elemento Boundary, dibujado al fondo
        var capaEl as EA.Element;
        capaEl = cont.Elements.AddNew(nomCapa, "Boundary");
        capaEl.Update();
        cont.Elements.Refresh();
        ubicar(diag, capaEl.ElementID, CAPA_X, y, CAPA_W, CAPA_H, true);

        // Las cajas de la capa: una sola fila centrada
        var anchoFila = hijos.length * CAJA_W + (hijos.length - 1) * GAP_X;
        var x0 = CAPA_X + Math.floor((CAPA_W - anchoFila) / 2);

        for (var j = 0; j < hijos.length; j++) {
            var nombre = hijos[j][0];
            var notas  = hijos[j][1];

            // Se crean en el mismo paquete del diagrama: asi EA no dibuja "(from ...)"
            var hijoPkg as EA.Package;
            hijoPkg = crearPaquete(cont, nombre, notas);

            var hijoEl as EA.Element;
            hijoEl = elementoDe(hijoPkg);
            if (hijoEl == null) {
                Session.Output("No se pudo obtener el elemento del paquete: " + nombre);
                return;
            }

            var x = x0 + j * (CAJA_W + GAP_X);
            ubicar(diag, hijoEl.ElementID, x, y + OFFSET_CAJA, CAJA_W, CAJA_H, false);

            elMap[nombre] = hijoEl;
            totalPaquetes++;
        }

        y = y + CAPA_H + GAP_CAPAS;
    }
    diag.DiagramObjects.Refresh();

    // ── Dependencias punteadas entre paquetes ─────────────────────────────────
    var totalDeps = 0;
    for (var d = 0; d < deps.length; d++) {
        var origen as EA.Element;
        var dest as EA.Element;
        origen = elMap[deps[d][0]];
        dest   = elMap[deps[d][1]];

        if (origen == null || dest == null) {
            Session.Output("Dependencia omitida (falta un paquete): " + deps[d][0] + " -> " + deps[d][1]);
            continue;
        }

        var conn as EA.Connector;
        conn = origen.Connectors.AddNew("", "Dependency");
        conn.SupplierID = dest.ElementID;
        conn.Direction  = "Source -> Destination";
        conn.Update();
        origen.Connectors.Refresh();
        totalDeps++;
    }

    Repository.RefreshModelView(destino.PackageID);
    Repository.ReloadDiagram(diag.DiagramID);
    Repository.OpenDiagram(diag.DiagramID);

    Session.Output("Diagrama de capas generado dentro de \"" + NOMBRE_PAQUETE + "\": " +
                   capas.length + " capas, " + totalPaquetes + " paquetes, " +
                   totalDeps + " dependencias.");
}

// ── Utilidades ────────────────────────────────────────────────────────────────

/**
 * Crea un paquete hijo con sus notas y devuelve el EA.Package ya releido.
 * OJO: en un paquete recien creado, ElementID / Element todavia no estan
 * sincronizados; hay que volver a leerlo con GetPackageByID antes de usarlos
 * (si no, GetElementByID recibe 0 y EA responde "Internal application error").
 */
function crearPaquete(padre, nombre, notas)
{
    var p as EA.Package;
    p = padre.Packages.AddNew(nombre, "Package");
    p.Update();
    padre.Packages.Refresh();

    var pkgOk as EA.Package;
    pkgOk = Repository.GetPackageByID(p.PackageID);
    pkgOk.Notes = notas;
    pkgOk.Update();

    return pkgOk;
}

/** Devuelve el EA.Element que representa al paquete (con fallback por ElementID). */
function elementoDe(pk)
{
    var el as EA.Element;
    el = null;

    try { el = pk.Element; } catch (e1) { el = null; }

    if (el == null && pk.ElementID > 0) {
        try { el = Repository.GetElementByID(pk.ElementID); } catch (e2) { el = null; }
    }
    return el;
}

/** Apaga "Show Namespace" del diagrama: sin el texto "(from Paquete)" bajo las cajas. */
function apagarNamespace(diag)
{
    try {
        var estilo = diag.StyleEx;
        if (estilo == null) { estilo = ""; }

        if (estilo.indexOf("ShowNS=") >= 0) {
            estilo = estilo.replace(/ShowNS=[^;]*;/, "ShowNS=0;");
        } else {
            estilo = estilo + "ShowNS=0;";
        }

        diag.StyleEx = estilo;
        diag.Update();
    } catch (e) { }
}

/**
 * Ubica el elemento (por ElementID) en el diagrama.
 * AddNew recibe las coordenadas verticales como números positivos.
 * alFondo = true manda el objeto al fondo (las capas, para que no tapen las cajas).
 */
function ubicar(diag, elementID, x, y, w, h, alFondo)
{
    var obj as EA.DiagramObject;
    obj = diag.DiagramObjects.AddNew(
        "l=" + x + ";r=" + (x + w) + ";t=" + y + ";b=" + (y + h) + ";", ""
    );
    obj.ElementID = elementID;

    // Sin colores: se deja el look por defecto de EA, igual que el diagrama guia.
    if (alFondo) {
        try { obj.Sequence = 100; } catch (e1) { }   // z-order: numero alto = mas al fondo
    }

    obj.Update();
    return obj;
}

/**
 * Borra lo que genera este script en el destino:
 *   - el paquete contenedor NOMBRE_PAQUETE (con todo adentro),
 *   - restos de versiones viejas: paquetes "Capa de ...", sus boundaries y el
 *     diagrama suelto con el nombre NOMBRE_DIAGRAMA.
 */
function limpiar(destino, capas)
{
    var borrados = 0;
    var n;

    for (var i = destino.Packages.Count - 1; i >= 0; i--) {
        var sub as EA.Package;
        sub = destino.Packages.GetAt(i);

        var borrar = (sub.Name == NOMBRE_PAQUETE);
        if (!borrar) {
            for (n = 0; n < capas.length; n++) {
                if (sub.Name == capas[n][0]) { borrar = true; break; }
            }
        }

        if (borrar) {
            destino.Packages.DeleteAt(i, false);
            borrados++;
        }
    }
    destino.Packages.Refresh();

    for (var e = destino.Elements.Count - 1; e >= 0; e--) {
        var el as EA.Element;
        el = destino.Elements.GetAt(e);
        if (el.Type == "Boundary") {
            for (n = 0; n < capas.length; n++) {
                if (el.Name == capas[n][0]) {
                    destino.Elements.DeleteAt(e, false);
                    borrados++;
                    break;
                }
            }
        }
    }
    destino.Elements.Refresh();

    for (var k = destino.Diagrams.Count - 1; k >= 0; k--) {
        var dg as EA.Diagram;
        dg = destino.Diagrams.GetAt(k);
        if (dg.Name == NOMBRE_DIAGRAMA) {
            destino.Diagrams.DeleteAt(k, false);
            borrados++;
        }
    }
    destino.Diagrams.Refresh();

    if (borrados > 0) {
        Session.Output("Limpieza previa: " + borrados + " elemento(s) anterior(es) eliminado(s).");
    }
}

main();
