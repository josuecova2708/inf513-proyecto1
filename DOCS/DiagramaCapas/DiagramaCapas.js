!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: DiagramaCapas_Grupo14
 * Author: Grupo 14 - INF513
 * Purpose: Generar el Diagrama de Capas del sistema
 *          "Moron Diseño y Decoraciones de Interiores" (MoronCorreo).
 * Date: 2026-08-04
 *
 * FORMATO (igual al diagrama guia):
 *   - Cada capa es un elemento Boundary (rectangulo con el titulo arriba).
 *   - Dentro de cada capa van paquetes con el look por defecto de EA.
 *   - Las flechas son Dependencias punteadas ENTRE PAQUETES (no entre capas):
 *       Lector POP3 -> ProcesadorComandos -> los 8 casos de uso -> PostgreSQL.
 *
 * CAPAS:
 *   1. Presentacion  -> Lector POP3, Emisor SMTP, TablaFormato
 *   2. Aplicacion    -> ProcesadorComandos (hub que despacha)
 *   3. Dominio       -> los 8 casos de uso, un handler cada uno (el CU va en las notas)
 *   4. Persistencia  -> PostgreSQL y PagoFacil (API REST)
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
var LIMPIAR_ANTES   = true;                              // ver ATENCION arriba
var NOMBRE_PAQUETE  = "Diagrama de Capas - Grupo 14";    // paquete contenedor en el arbol
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
        new Array("Emisor SMTP",
                  "moroncorreo.app.CorreoService (enviarRespuesta): arma el correo de vuelta y lo envia por SMTP."),
        new Array("Lector de Correos POP3",
                  "moroncorreo.app.CorreoService (procesar): baja los mensajes por POP3 y toma el Subject como comando."),
        new Array("TablaFormato",
                  "moroncorreo.negocio.TablaFormato: convierte el ResultSet en la tabla de texto que viaja en el cuerpo del correo.")
    ));

    capas[1] = new Array("Capa de Aplicación", new Array(
        new Array("ProcesadorComandos",
                  "moroncorreo.app.ProcesadorComandos (lo arranca Main): parsea COMANDO[\"p1\",\"p2\"], resuelve el rol del remitente " +
                  "con AutorizacionHandler, aplica la matriz de permisos y despacha al handler del caso de uso.")
    ));

    capas[2] = new Array("Capa de Dominio", new Array(
        new Array("Usuarios",
                  "CU1 - Usuarios. negocio.administracion.UsuarioHandler: LISUSR, INSUSR, MODUSR, ELIUSR (solo PROPIETARIO)."),
        new Array("Productos",
                  "CU2 - Productos. negocio.comercial.ProductoHandler: LISPROD, INSPROD, MODPROD, ELIPROD."),
        new Array("Pedidos",
                  "CU3 - Pedidos. negocio.comercial.PedidoHandler: INSPED, INSDET, LISPED, MODPED (el CLIENTE solo sobre lo suyo)."),
        new Array("Inventario",
                  "CU4 - Inventario. negocio.operacion.InventarioHandler: LISINV, INSINV, VALINV (PROPIETARIO y VENDEDOR)."),
        new Array("Promociones",
                  "CU5 - Promociones. negocio.comercial.PromocionHandler: LISPRO, INSPRO, MODPRO, ELIPRO."),
        new Array("Ventas",
                  "CU6 - Ventas. negocio.comercial.VentaHandler: LISVEN, INSVEN, GENVEN, MODVEN (PROPIETARIO y VENDEDOR)."),
        new Array("Pagos",
                  "CU7 - Pagos. negocio.operacion.PagoHandler: LISPAG, INSPAG, MODPAG, VERPAG; contado, cuotas y QR."),
        new Array("Reportes",
                  "CU8 - Reportes. negocio.administracion.ReporteHandler: REPVEN, REPINV, REPPRO, REPCUO (solo PROPIETARIO).")
    ));

    capas[3] = new Array("Capa de Persistencia", new Array(
        new Array("PostgreSQL",
                  "Base de datos del sistema; se accede por JDBC a traves de moroncorreo.infra.ConexionDB."),
        new Array("PagoFacil (API REST)",
                  "Servicio externo de pagos QR; se consume con moroncorreo.infra.PagoFacilClient y PagoFacilConfig.")
    ));

    // ── Dependencias entre paquetes (flechas punteadas del guia) ──────────────
    // Formato: [origen, destino]
    var deps = new Array(
        // Presentacion <-> Aplicacion
        new Array("Lector de Correos POP3", "ProcesadorComandos"),
        new Array("ProcesadorComandos",     "Emisor SMTP"),
        new Array("ProcesadorComandos",     "TablaFormato"),
        // Aplicacion -> los 8 casos de uso
        new Array("ProcesadorComandos",     "Usuarios"),
        new Array("ProcesadorComandos",     "Productos"),
        new Array("ProcesadorComandos",     "Pedidos"),
        new Array("ProcesadorComandos",     "Inventario"),
        new Array("ProcesadorComandos",     "Promociones"),
        new Array("ProcesadorComandos",     "Ventas"),
        new Array("ProcesadorComandos",     "Pagos"),
        new Array("ProcesadorComandos",     "Reportes"),
        // Casos de uso -> persistencia
        new Array("Usuarios",               "PostgreSQL"),
        new Array("Productos",              "PostgreSQL"),
        new Array("Pedidos",                "PostgreSQL"),
        new Array("Inventario",             "PostgreSQL"),
        new Array("Promociones",            "PostgreSQL"),
        new Array("Ventas",                 "PostgreSQL"),
        new Array("Pagos",                  "PostgreSQL"),
        new Array("Reportes",               "PostgreSQL"),
        // Integracion externa
        new Array("Pagos",                  "PagoFacil (API REST)")
    );

    // ── Limpieza previa (permite re-ejecutar sin duplicados) ──────────────────
    if (LIMPIAR_ANTES) {
        limpiar(destino, capas);
    }

    // ── Paquete contenedor: todo el diagrama vive adentro ─────────────────────
    var cont as EA.Package;
    cont = crearPaquete(destino, NOMBRE_PAQUETE,
                        "Contenedor del diagrama de capas de MoronCorreo (generado por script).");

    // ── Crear el diagrama dentro del contenedor ───────────────────────────────
    var diag as EA.Diagram;
    diag = cont.Diagrams.AddNew(NOMBRE_DIAGRAMA, "Package");
    diag.Notes = "Diagrama de capas de MoronCorreo. Las dependencias van siempre hacia abajo: " +
                 "ninguna capa inferior conoce a las superiores.";
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
 * En EA el eje Y crece hacia abajo en negativo: t y b van negados.
 * alFondo = true manda el objeto al fondo (las capas, para que no tapen las cajas).
 */
function ubicar(diag, elementID, x, y, w, h, alFondo)
{
    var obj as EA.DiagramObject;
    obj = diag.DiagramObjects.AddNew(
        "l=" + x + ";r=" + (x + w) + ";t=" + (-y) + ";b=" + (-(y + h)) + ";", ""
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
