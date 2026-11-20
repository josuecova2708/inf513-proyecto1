!INC Local Scripts.EAConstants-JScript

/* CU6 - Clases de pedidos, pagos, entregas y notificaciones de FEDER, Grupo 28.
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
        var diagrama = paquete.Diagrams.AddNew("CU6 - Clases Pedidos, Pagos y Entregas", "Logical");
        diagrama.Notes = "Vista resumida de las clases principales de pedidos, pagos, entregas y notificaciones. " +
            "Se muestran las dependencias principales y los métodos sin parámetros.";
        guardar(diagrama, "el diagrama CU6");

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
        var pedidoHandler = crearClase(paquete, "PedidoHandler", "feder.negocio.comercial.pedidos.PedidoHandler: consulta, seguimiento y cancelación de pedidos.", [],
            [["registrar", "void", "Public"], ["listar", "Respuesta", "Private"],
             ["ver", "Respuesta", "Private"], ["cambiarEstado", "Respuesta", "Private"],
             ["cancelar", "Respuesta", "Private"]]);
        var pagoHandler = crearClase(paquete, "PagoHandler", "feder.negocio.comercial.pedidos.PagoHandler: registra y verifica pagos y administra el QR de cobro.", [],
            [["registrar", "void", "Public"], ["insertar", "Respuesta", "Private"],
             ["cambiarEstado", "Respuesta", "Private"], ["listar", "Respuesta", "Private"],
             ["subirQr", "Respuesta", "Private"], ["verQr", "Respuesta", "Private"]]);
        var entregaHandler = crearClase(paquete, "EntregaHandler", "feder.negocio.comercial.pedidos.EntregaHandler: consulta, programación y actualización de entregas.", [],
            [["registrar", "void", "Public"], ["listar", "Respuesta", "Private"],
             ["programar", "Respuesta", "Private"], ["cambiarEstado", "Respuesta", "Private"]]);
        var notificacionHandler = crearClase(paquete, "NotificacionHandler", "feder.negocio.comercial.pedidos.NotificacionHandler: consulta las notificaciones del usuario.", [],
            [["registrar", "void", "Public"], ["listar", "Respuesta", "Private"]]);
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

        // Flujo de correo a la izquierda, manejadores al centro y acceso a datos a la derecha.
        colocar(diagrama, inicio, 50, 60, 220, 95);
        colocar(diagrama, parser, 380, 80, 220, 95);
        colocar(diagrama, autorizacion, 1220, 80, 220, 130);
        colocar(diagrama, conexion, 1570, 60, 220, 150);
        colocar(diagrama, correo, 50, 330, 220, 145);
        colocar(diagrama, procesador, 380, 330, 220, 125);
        colocar(diagrama, pedidoHandler, 710, 240, 230, 165);
        colocar(diagrama, pagoHandler, 710, 490, 230, 185);
        colocar(diagrama, entregaHandler, 1050, 300, 230, 150);
        colocar(diagrama, notificacionHandler, 1050, 600, 230, 100);
        colocar(diagrama, db, 1570, 360, 220, 150);
        colocar(diagrama, conexionCorreo, 50, 800, 220, 205);
        colocar(diagrama, enviador, 380, 800, 220, 95);
        colocar(diagrama, respuesta, 710, 900, 230, 175);
        colocar(diagrama, tabla, 1570, 800, 220, 95);

        dependencia(inicio, correo);
        dependencia(inicio, conexion);
        dependencia(correo, conexionCorreo);
        dependencia(correo, enviador);
        dependencia(correo, procesador);
        dependencia(correo, respuesta);
        dependencia(procesador, parser);
        dependencia(procesador, autorizacion);
        dependencia(procesador, pedidoHandler);
        dependencia(procesador, pagoHandler);
        dependencia(procesador, entregaHandler);
        dependencia(procesador, notificacionHandler);
        dependencia(procesador, respuesta);
        dependencia(pedidoHandler, db);
        dependencia(pedidoHandler, respuesta);
        dependencia(pagoHandler, db);
        dependencia(pagoHandler, respuesta);
        dependencia(entregaHandler, db);
        dependencia(entregaHandler, respuesta);
        dependencia(notificacionHandler, db);
        dependencia(notificacionHandler, respuesta);
        dependencia(autorizacion, db);
        dependencia(db, conexion);
        dependencia(db, tabla);
        dependencia(enviador, conexionCorreo);

        paquete.Elements.Refresh();
        paquete.Diagrams.Refresh();
        diagrama.DiagramObjects.Refresh();
        Repository.RefreshModelView(paquete.PackageID);
        Repository.OpenDiagram(diagrama.DiagramID);
        Session.Output("Diagrama procedimental CU6 creado con 15 clases.");
    }
    catch (error)
    {
        Session.Output("Error al generar el diagrama CU6: " + error.message +
            ". Revise el paquete; podrían haberse creado elementos parcialmente.");
    }
}

main();
