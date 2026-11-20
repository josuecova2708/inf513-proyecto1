!INC Local Scripts.EAConstants-JScript

/*
 * Script Name: DiagramaDespliegue_Grupo28
 * Author: Grupo 28 - INF513
 * Purpose: Generar el Diagrama de Despliegue del sistema
 *          FEDER (FederCorreo).
 * Date: 2026-08-04
 *
 * NODOS (datos reales del proyecto):
 *   - «device» www.tecnoweb.org.bo -> «executionEnvironment» JRE 21
 *       -> «artifact» FederCorreo-1.0.jar (shade, main feder.app.Main)
 *   - «database» Database Server www.tecnoweb.org.bo -> Fedora Linux
 *       -> «component» Base de Datos db_grupo28sa (PostgreSQL, puerto 5432)
 *   - «device» Router
 *   - 4 puestos: ADMINISTRADOR, PERSONAL DE FEDER, PRODUCTOR y CLIENTE.
 *     Cada uno con «executionEnvironment» Linux, Windows, Android y los
 *     componentes Cliente de correo y Navegador Web.
 *   - «device» Servidor de correo POP3/SMTP.
 *
 * USO:
 *   1. Seleccionar en el Project Browser donde se quiere el diagrama (por ejemplo
 *      el modelo "Model"). Si no hay nada seleccionado se usa el primer modelo.
 *   2. Ejecutar el script: crea el paquete NOMBRE_PAQUETE y mete TODO adentro.
 *
 * ATENCION: con LIMPIAR_ANTES = true el script BORRA, en el destino, el paquete
 * NOMBRE_PAQUETE de la corrida anterior con todo su contenido, y el diagrama
 * suelto que se llame NOMBRE_DIAGRAMA. Ponerlo en false si no se desea.
 */

// ── Configuracion ─────────────────────────────────────────────────────────────
var LIMPIAR_ANTES   = false;                                 // ver ATENCION arriba
var NOMBRE_PAQUETE  = "Diagrama de Despliegue - Grupo 28";  // contenedor en el arbol
var NOMBRE_DIAGRAMA = "Diagrama de Despliegue";              // marco: "deployment ..."

// ── Colores (mismos tonos del diagrama guia) ─────────────────────────────────
function bgr(r, g, b) { return b * 65536 + g * 256 + r; }    // EA guarda BGR

var COLOR_DEVICE   = bgr(246, 235, 221);   // #F6EBDD  nodo «device» (beige)
var COLOR_ENTORNO  = bgr(218, 232, 252);   // #DAE8FC  executionEnvironment
var COLOR_BD       = bgr(181, 214, 236);   // #B5D6EC  nodo «database»
var COLOR_ARTIFACT = bgr(213, 232, 212);   // #D5E8D4  el .jar
var SIN_COLOR      = -1;                   // -1 = look por defecto de EA

// ── Z-order (Sequence): numero MAS BAJO = mas adelante ───────────────────────
// Hay que fijarlo si o si: sin esto EA ordena los objetos como se le antoja y
// los hijos pueden quedar dibujados DETRAS del nodo padre (nodos que se ven vacios).
var Z_NODO    = 30;   // los «device» / «database» de afuera, al fondo
var Z_ENTORNO = 20;   // los «executionEnvironment», en el medio
var Z_INTERNO = 10;   // artifacts y components, al frente

// ── Geometria de los puestos de usuario ───────────────────────────────────────
var CLI_W    = 340;   // ancho de cada «device» de usuario
var CLI_H    = 240;   // alto
var CLI_GAP  = 30;    // separacion entre ellos
var CLI_X0   = 500;   // x del primero
var CLI_Y    = 390;   // y de la fila

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

    if (LIMPIAR_ANTES) {
        limpiar(destino);
    }

    // ── Paquete contenedor: todo el diagrama vive adentro ─────────────────────
    var cont as EA.Package;
    cont = crearPaquete(destino, NOMBRE_PAQUETE,
                        "Contenedor del diagrama de despliegue de FederCorreo (generado por script).");

    // ── Diagrama de despliegue ────────────────────────────────────────────────
    var diag as EA.Diagram;
    diag = cont.Diagrams.AddNew(NOMBRE_DIAGRAMA, "Deployment");
    diag.Notes = "Despliegue de FederCorreo del Grupo 28: el .jar lee y responde correos por " +
                 "POP3/SMTP y utiliza PostgreSQL. El QR se envía por correo y el pago se verifica manualmente.";
    diag.Update();
    cont.Diagrams.Refresh();
    apagarNamespace(diag);   // que no aparezca el "(from ...)" debajo de los nodos

    var elMap = new Object();   // nombre -> EA.Element

    // ── 1. Servidor de aplicacion ─────────────────────────────────────────────
    var srv as EA.Element;
    srv = crearElemento(cont, "www.tecnoweb.org.bo", "Node", "device",
                        "Servidor de la materia donde corre el sistema. Un cron reinicia el proceso si se cae.",
                        null);
    ubicar(diag, srv, 60, 70, 340, 220, COLOR_DEVICE, Z_NODO);
    elMap["srv"] = srv;

    var jre as EA.Element;
    jre = crearElemento(cont, "Java Runtime Environment (JRE 21)", "Node", "executionEnvironment",
                        "Java 21 (maven.compiler.source/target = 21).", srv);
    ubicar(diag, jre, 90, 125, 280, 150, COLOR_ENTORNO, Z_ENTORNO);

    var jar as EA.Element;
    jar = crearElemento(cont, "FederCorreo-1.0.jar", "Artifact", "",
                        "Ejecutable con dependencias; clase principal feder.app.Main. " +
                        "Lee comandos por POP3 y responde por SMTP.", jre);
    ubicar(diag, jar, 120, 185, 200, 70, COLOR_ARTIFACT, Z_INTERNO);

    // ── 2. Servidor de base de datos ──────────────────────────────────────────
    var bdSrv as EA.Element;
    bdSrv = crearElemento(cont, "Database Server www.tecnoweb.org.bo", "Node", "database",
                          "Servidor PostgreSQL de la materia (puerto 5432).", null);
    ubicar(diag, bdSrv, 60, 390, 340, 240, COLOR_BD, Z_NODO);
    elMap["bd"] = bdSrv;

    var so as EA.Element;
    so = crearElemento(cont, "Sistema Operativo Fedora Linux", "Node", "executionEnvironment",
                       "Sistema operativo del servidor de base de datos.", bdSrv);
    ubicar(diag, so, 90, 450, 280, 160, COLOR_ENTORNO, Z_ENTORNO);

    var bd as EA.Element;
    bd = crearElemento(cont, "Base de Datos db_grupo28sa", "Component", "",
                       "Base de datos FEDER: usuarios, productores, producción, inventario, pedidos y pagos. " +
                       "Se accede por JDBC desde feder.infra.ConexionDB.", so);
    ubicar(diag, bd, 120, 510, 200, 80, SIN_COLOR, Z_INTERNO);

    // ── 3. Router ─────────────────────────────────────────────────────────────
    var router as EA.Element;
    router = crearElemento(cont, "Router", "Node", "device",
                           "Salida a Internet: por aca pasan los correos y las consultas de los usuarios.", null);
    ubicar(diag, router, 800, 170, 110, 90, COLOR_DEVICE, Z_NODO);
    elMap["router"] = router;

    // ── 4. Servidor de correo ─────────────────────────────────────────────────
    var mailSrv as EA.Element;
    mailSrv = crearElemento(cont, "Servidor de correo mail.tecnoweb.org.bo", "Node", "device",
                            "Servidor POP3 y SMTP utilizado para recibir comandos y enviar respuestas.", null);
    ubicar(diag, mailSrv, 1240, 90, 330, 160, COLOR_DEVICE, Z_NODO);
    elMap["correo"] = mailSrv;

    var mailServicio as EA.Element;
    mailServicio = crearElemento(cont, "Correo POP3 / SMTP", "Component", "",
                                  "Casilla del Grupo 28 para comandos y respuestas.", mailSrv);
    ubicar(diag, mailServicio, 1280, 155, 250, 70, SIN_COLOR, Z_INTERNO);

    // ── 5. Puestos de usuario: un «device» por actor ──────────────────────────
    // Formato: [nombre, notas]
    var actores = new Array(
        new Array("Movil/PC Administrador",
                  "Actor ADMINISTRADOR: gestiona usuarios y permisos y supervisa las operaciones."),
        new Array("Movil/PC Personal de FEDER",
                  "Actor PERSONAL: gestiona productores, producción, inventario, pedidos y pagos según sus permisos."),
        new Array("Movil/PC Productor/Agricultor",
                  "Actor PRODUCTOR: reporta su producción y consulta sus propios lotes."),
        new Array("Movil/PC Cliente",
                  "Actor CLIENTE: consulta el catálogo, compra y revisa sus pedidos y pagos.")
    );

    for (var i = 0; i < actores.length; i++) {
        var x = CLI_X0 + i * (CLI_W + CLI_GAP);

        var pc as EA.Element;
        pc = crearElemento(cont, actores[i][0], "Node", "device", actores[i][1], null);
        ubicar(diag, pc, x, CLI_Y, CLI_W, CLI_H, COLOR_DEVICE, Z_NODO);
        elMap["pc" + i] = pc;

        var so2 as EA.Element;
        so2 = crearElemento(cont, "Linux, Windows, Android", "Node", "executionEnvironment",
                            "El sistema no exige nada especial: basta un dispositivo con correo y navegador.", pc);
        ubicar(diag, so2, x + 30, CLI_Y + 70, CLI_W - 60, CLI_H - 110, COLOR_ENTORNO, Z_ENTORNO);

        var nav as EA.Element;
        nav = crearElemento(cont, "Navegador Web", "Component", "",
                            "Permite acceder al correo mediante webmail si el usuario lo prefiere.", so2);
        ubicar(diag, nav, x + 50, CLI_Y + 125, 110, 60, SIN_COLOR, Z_INTERNO);

        var mail as EA.Element;
        mail = crearElemento(cont, "Cliente de correo", "Component", "",
                             "Cliente de correo: el usuario manda COMANDO[\"p1\",\"p2\"] en el Asunto y recibe la respuesta.", so2);
        ubicar(diag, mail, x + 180, CLI_Y + 125, 110, 60, SIN_COLOR, Z_INTERNO);
    }

    diag.DiagramObjects.Refresh();

    // ── Conexiones ────────────────────────────────────────────────────────────
    // Formato: [origen, destino, etiqueta]
    var enlaces = new Array(
        new Array("srv",    "bd",        "TCP / IP"),
        new Array("srv",    "router",    "POP3 / SMTP"),
        new Array("srv",    "correo",    "POP3 / SMTP"),
        new Array("router", "pc0",       "TCP / IP"),
        new Array("router", "pc1",       "TCP / IP"),
        new Array("router", "pc2",       "TCP / IP"),
        new Array("router", "pc3",       "TCP / IP")
    );

    var totalEnlaces = 0;
    for (var k = 0; k < enlaces.length; k++) {
        var a as EA.Element;
        var b as EA.Element;
        a = elMap[enlaces[k][0]];
        b = elMap[enlaces[k][1]];

        if (a == null || b == null) { continue; }

        var conn as EA.Connector;
        conn = a.Connectors.AddNew(enlaces[k][2], "Association");
        conn.SupplierID = b.ElementID;
        conn.Direction  = "Unspecified";
        conn.Update();
        a.Connectors.Refresh();
        totalEnlaces++;
    }

    Repository.RefreshModelView(destino.PackageID);
    Repository.ReloadDiagram(diag.DiagramID);
    Repository.OpenDiagram(diag.DiagramID);

    // Control: 9 objetos fijos (servidor, JRE, jar, BD, SO, base, router y servidor de correo con servicio)
    // + 4 por cada puesto de usuario (device, entorno, navegador, gmail).
    diag.DiagramObjects.Refresh();
    var esperados = 9 + actores.length * 4;

    Session.Output("Diagrama de Despliegue generado dentro de \"" + NOMBRE_PAQUETE + "\": " +
                   actores.length + " puestos de usuario, " + totalEnlaces + " conexiones.");
    Session.Output("Objetos dibujados: " + diag.DiagramObjects.Count + " de " + esperados + " esperados.");
}

// ── Utilidades ────────────────────────────────────────────────────────────────

/**
 * Crea un elemento (Node, Component, Artifact...) con estereotipo y notas.
 * padreEl != null lo anida dentro de ese elemento (queda embebido en el diagrama
 * y colgando de el en el Project Browser).
 */
function crearElemento(pkg, nombre, tipo, estereotipo, notas, padreEl)
{
    var el as EA.Element;
    el = pkg.Elements.AddNew(nombre, tipo);

    if (estereotipo != "") { el.Stereotype = estereotipo; }
    el.Notes = notas;
    if (padreEl != null)   { el.ParentID = padreEl.ElementID; }

    el.Update();
    pkg.Elements.Refresh();
    return el;
}

/**
 * Crea un paquete hijo con sus notas y devuelve el EA.Package ya releido.
 * OJO: en un paquete recien creado, ElementID / Element todavia no estan
 * sincronizados; hay que volver a leerlo con GetPackageByID antes de usarlos.
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

/** Apaga "Show Namespace" del diagrama: sin el texto "(from Paquete)" bajo los nodos. */
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
 * Ubica el elemento en el diagrama.
 * AddNew recibe las coordenadas verticales como números positivos.
 * color = -1 deja el look por defecto de EA.
 * seq   = z-order; numero mas bajo se dibuja mas adelante (ver Z_NODO / Z_INTERNO).
 */
function ubicar(diag, el, x, y, w, h, color, seq)
{
    var obj as EA.DiagramObject;
    obj = diag.DiagramObjects.AddNew(
        "l=" + x + ";r=" + (x + w) + ";t=" + y + ";b=" + (y + h) + ";", ""
    );
    obj.ElementID = el.ElementID;

    if (color != -1) {
        try { obj.BackgroundColor = color; } catch (e1) { }
    }
    try { obj.Sequence = seq; } catch (e2) { }

    obj.Update();
    return obj;
}

/** Borra la corrida anterior: el paquete contenedor y el diagrama suelto homonimo. */
function limpiar(destino)
{
    var borrados = 0;

    for (var i = destino.Packages.Count - 1; i >= 0; i--) {
        var sub as EA.Package;
        sub = destino.Packages.GetAt(i);
        if (sub.Name == NOMBRE_PAQUETE) {
            destino.Packages.DeleteAt(i, false);
            borrados++;
        }
    }
    destino.Packages.Refresh();

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
