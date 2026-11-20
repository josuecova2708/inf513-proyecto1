!INC Local Scripts.EAConstants-JScript

/* CU4 - Clases de catálogo, inventario y alertas de FEDER, Grupo 28.
 * Seleccione un paquete en Enterprise Architect y ejecute el script.
 * Cada ejecución crea un diagrama y clases nuevas en el paquete seleccionado. */

function guardar(objeto, descripcion)
{
    if (!objeto.Update())
        throw new Error("No se pudo guardar " + descripcion + ": " + objeto.GetLastError());
}

// Los miembros se definen como [nombre, tipo, visibilidad].
function crearClase(paquete, nombre, notas, atributos, operaciones)
{
    var clase = paquete.Elements.AddNew(nombre, "Class");
    clase.Notes = notas;
    guardar(clase, nombre);
    var i, miembro;
    for (i = 0; i < atributos.length; i++)
    {
        miembro = clase.Attributes.AddNew(atributos[i][0], atributos[i][1]);
        miembro.Visibility = atributos[i][2];
        miembro.Pos = i;
        guardar(miembro, nombre + "." + atributos[i][0]);
    }
    clase.Attributes.Refresh();
    for (i = 0; i < operaciones.length; i++)
    {
        miembro = clase.Methods.AddNew(operaciones[i][0], operaciones[i][1]);
        miembro.Visibility = operaciones[i][2];
        miembro.Pos = i;
        guardar(miembro, nombre + "." + operaciones[i][0]);
    }
    clase.Methods.Refresh();
    return clase;
}

function colocar(diagrama, clase, x, y, ancho, alto)
{
    var posicion = "l=" + x + ";r=" + (x + ancho) +
        ";t=" + y + ";b=" + (y + alto) + ";";
    var objeto = diagrama.DiagramObjects.AddNew(posicion, "");
    objeto.ElementID = clase.ElementID;
    // Color beige claro, en formato BGR utilizado por Enterprise Architect.
    objeto.BackgroundColor = 15726075;
    guardar(objeto, "la posición de " + clase.Name);
}

function dependencia(origen, destino)
{
    var enlace = origen.Connectors.AddNew("", "Dependency");
    enlace.SupplierID = destino.ElementID;
    enlace.Stereotype = "use";
    guardar(enlace, "la dependencia de " + origen.Name + " a " + destino.Name);
}

function main()
{
    var paquete = Repository.GetTreeSelectedPackage();
    if (paquete == null)
    {
        Session.Output("Seleccione un paquete en el navegador antes de ejecutar el script.");
        return;
    }

    try
    {
        var diagrama = paquete.Diagrams.AddNew("CU4 - Clases Catálogo e Inventario", "Logical");
        diagrama.Notes = "Vista de las principales clases Java del catálogo, inventario y alertas. " +
            "InventarioHandler utiliza transacciones y bloqueo de filas para movimientos concurrentes. " +
            "Los métodos se muestran sin parámetros para mantener la vista compacta.";
        guardar(diagrama, "el diagrama CU4");

        var inicio = crearClase(paquete, "Main", "feder.app.Main: punto de entrada y ciclo principal.", [],
            [["main", "void", "Public"]]);
        var parser = crearClase(paquete, "ParserAsunto", "feder.parser.ParserAsunto: interpreta el asunto del correo.", [],
            [["parsear", "Resultado", "Public"]]);
        var autorizacion = crearClase(paquete, "Autorizacion", "feder.seguridad.Autorizacion: identifica al remitente y verifica permisos.", [],
            [["contextoDe", "Contexto", "Public"], ["permisosDe", "Map", "Public"], ["puede", "boolean", "Public"]]);
        var conexion = crearClase(paquete, "ConexionDB", "feder.infra.ConexionDB: proporciona conexiones JDBC.",
            [["URL", "String", "Private"], ["USER", "String", "Private"], ["PASS", "String", "Private"]],
            [["getConnection", "Connection", "Public"], ["estaDisponible", "boolean", "Public"]]);
        var correo = crearClase(paquete, "CorreoService", "feder.app.CorreoService: procesa la bandeja y prepara respuestas.", [],
            [["procesar", "Ciclo", "Public"], ["procesarMensaje", "Resultado", "Private"],
             ["cuerpo", "String", "Package"], ["html", "String", "Package"]]);
        var procesador = crearClase(paquete, "ProcesadorComandos", "feder.app.ProcesadorComandos: analiza y despacha cada comando.",
            [["REGISTRO", "Registro", "Public"]],
            [["procesar", "Salida", "Public"], ["procesarConSesion", "Salida", "Private"]]);
        var categorias = crearClase(paquete, "CategoriaHandler", "feder.negocio.operacion.inventario.CategoriaHandler: consulta y gestión de categorías.", [],
            [["registrar", "void", "Public"], ["insertar", "Respuesta", "Private"],
             ["listar", "Respuesta", "Private"], ["modificar", "Respuesta", "Private"],
             ["eliminar", "Respuesta", "Private"]]);
        var productos = crearClase(paquete, "ProductoHandler", "feder.negocio.operacion.inventario.ProductoHandler: catálogo y disponibilidad de productos.", [],
            [["registrar", "void", "Public"], ["insertar", "Respuesta", "Private"],
             ["listar", "Respuesta", "Private"], ["ver", "Respuesta", "Private"],
             ["disponibilidad", "Respuesta", "Private"], ["modificar", "Respuesta", "Private"],
             ["eliminar", "Respuesta", "Private"]]);
        var inventario = crearClase(paquete, "InventarioHandler", "feder.negocio.operacion.inventario.InventarioHandler: movimientos de stock y alertas; usa bloqueo de filas en la transacción.", [],
            [["registrar", "void", "Public"], ["movimiento", "Respuesta", "Private"],
             ["listarMovimientos", "Respuesta", "Private"], ["listarAlertas", "Respuesta", "Private"],
             ["atenderAlerta", "Respuesta", "Private"]]);
        var db = crearClase(paquete, "Db", "feder.negocio.Db: consultas y transacciones de los handlers.", [],
            [["iniciarSesion", "Connection", "Public"], ["tx", "T", "Public"],
             ["tabla", "String", "Public"], ["update", "int", "Public"]]);
        var conexionCorreo = crearClase(paquete, "ConexionCorreo", "feder.infra.ConexionCorreo: configuración POP3 y SMTP.",
            [["HOST", "String", "Public"], ["POP3_PORT", "int", "Public"], ["SMTP_PORT", "int", "Public"],
             ["USUARIO", "String", "Public"], ["PASSWORD", "String", "Public"], ["CORREO", "String", "Public"]],
            [["getPop3Props", "Properties", "Public"], ["getSmtpProps", "Properties", "Public"]]);
        var enviador = crearClase(paquete, "EnviadorCorreo", "feder.infra.EnviadorCorreo: envía respuestas mediante SMTP.", [],
            [["enviar", "void", "Public"]]);
        var respuesta = crearClase(paquete, "Respuesta", "feder.negocio.Respuesta: resultado de un comando.",
            [["exito", "boolean", "Public"], ["mensaje", "String", "Public"], ["errorBD", "boolean", "Public"],
             ["imagen", "Adjuntos.Archivo", "Public"]],
            [["ok", "Respuesta", "Public"], ["error", "Respuesta", "Public"], ["errorBD", "Respuesta", "Public"]]);
        var tabla = crearClase(paquete, "TablaFormato", "feder.negocio.TablaFormato: convierte resultados SQL en texto.", [],
            [["formatear", "String", "Public"]]);

        // Los manejadores quedan separados para que las dependencias no
        // atraviesen los métodos de la otra clase.
        colocar(diagrama, inicio, 50, 50, 220, 95);
        colocar(diagrama, conexion, 1800, 50, 220, 150);
        colocar(diagrama, parser, 380, 200, 220, 95);
        colocar(diagrama, autorizacion, 1450, 200, 220, 130);
        colocar(diagrama, correo, 50, 390, 220, 145);
        colocar(diagrama, procesador, 380, 390, 220, 125);
        colocar(diagrama, categorias, 710, 350, 230, 165);
        colocar(diagrama, productos, 1040, 590, 240, 200);
        colocar(diagrama, inventario, 1450, 590, 250, 165);
        colocar(diagrama, db, 1800, 390, 220, 150);
        colocar(diagrama, conexionCorreo, 50, 820, 220, 205);
        colocar(diagrama, enviador, 380, 820, 220, 95);
        colocar(diagrama, respuesta, 1040, 950, 230, 175);
        colocar(diagrama, tabla, 1800, 820, 220, 95);

        dependencia(inicio, correo);
        dependencia(inicio, conexion);
        dependencia(correo, conexionCorreo);
        dependencia(correo, enviador);
        dependencia(correo, procesador);
        dependencia(correo, respuesta);
        dependencia(procesador, parser);
        dependencia(procesador, autorizacion);
        dependencia(procesador, categorias);
        dependencia(procesador, productos);
        dependencia(procesador, inventario);
        dependencia(procesador, respuesta);
        dependencia(categorias, db);
        dependencia(categorias, respuesta);
        dependencia(productos, db);
        dependencia(productos, respuesta);
        dependencia(inventario, db);
        dependencia(inventario, respuesta);
        dependencia(autorizacion, db);
        dependencia(db, conexion);
        dependencia(db, tabla);
        dependencia(enviador, conexionCorreo);

        paquete.Elements.Refresh();
        paquete.Diagrams.Refresh();
        diagrama.DiagramObjects.Refresh();
        Repository.RefreshModelView(paquete.PackageID);
        Repository.OpenDiagram(diagrama.DiagramID);
        Session.Output("Diagrama procedimental CU4 creado con 14 clases.");
    }
    catch (error)
    {
        Session.Output("Error al generar el diagrama CU4: " + error.message +
            ". Revise el paquete; podrían haberse creado elementos parcialmente.");
    }
}

main();
