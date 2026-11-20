!INC Local Scripts.EAConstants-JScript

/* CU1: consulta y registro de usuarios por correo.
 * Seleccione un paquete en Enterprise Architect antes de ejecutar el script.
 * Cada ejecución crea un diagrama y elementos nuevos. */

function guardar(objeto, descripcion)
{
	if (!objeto.Update())
		throw new Error("No se pudo guardar " + descripcion + ": " + objeto.GetLastError());
}

function crearElemento(paquete, nombre, tipo, notas)
{
	var elemento = paquete.Elements.AddNew(nombre, tipo);
	if (notas) elemento.Notes = notas;
	guardar(elemento, nombre);
	return elemento;
}

function colocar(diagrama, elemento, x, y, ancho, alto)
{
	var posicion = "l=" + x + ";r=" + (x + ancho) +
		";t=" + y + ";b=" + (y + alto) + ";";
	var objeto = diagrama.DiagramObjects.AddNew(posicion, "");
	objeto.ElementID = elemento.ElementID;
	if (elemento.Type != "Actor") objeto.BackgroundColor = 16777215;
	guardar(objeto, "la posición de " + elemento.Name);
}

function enlazar(origen, destino, mensajes)
{
	// El texto numerado describe los mensajes que viajan por esta asociación.
	var enlace = origen.Connectors.AddNew(mensajes, "Association");
	enlace.SupplierID = destino.ElementID;
	enlace.Direction = "Source -> Destination";
	guardar(enlace, "el enlace de " + origen.Name + " a " + destino.Name);
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
		var diagrama = paquete.Diagrams.AddNew("Comunicación CU1 - Gestión de Usuarios", "Analysis");
		diagrama.Notes = "Flujos alternativos de LISUSU e INSUSU para un Administrador autorizado. " +
			"Los números 1 y 2 identifican solicitudes separadas. Los textos de las asociaciones " +
			"describen los mensajes; no son objetos Message de Enterprise Architect.";
		guardar(diagrama, "el diagrama CU1");

		var admin = crearElemento(paquete, "Administrador", "Actor", "Envía comandos por correo.");
		var correo = crearElemento(paquete, ":CorreoService", "Object", "Recibe el correo y solicita su procesamiento.");
		var procesador = crearElemento(paquete, ":ProcesadorComandos", "Object", "Analiza, autoriza y ejecuta el comando.");
		var parser = crearElemento(paquete, ":ParserAsunto", "Object", "Analiza el asunto del correo.");
		var autorizacion = crearElemento(paquete, ":Autorizacion", "Object", "Obtiene el contexto y comprueba los permisos.");
		var usuario = crearElemento(paquete, ":UsuarioHandler", "Object", "Gestiona los comandos LISUSU e INSUSU.");
		var db = crearElemento(paquete, ":Db", "Object", "Ejecuta consultas y transacciones JDBC.");
		var conexion = crearElemento(paquete, ":ConexionDB", "Object", "Proporciona la conexión JDBC.");
		var bd = crearElemento(paquete, "Base de datos", "Object", "Almacena usuarios, roles y permisos.");
		var enviador = crearElemento(paquete, ":EnviadorCorreo", "Object", "Envía la respuesta al remitente.");

		colocar(diagrama, admin, 20, 260, 120, 130);
		colocar(diagrama, correo, 235, 260, 160, 75);
		colocar(diagrama, procesador, 520, 260, 190, 75);
		colocar(diagrama, usuario, 910, 260, 170, 75);
		colocar(diagrama, db, 1250, 260, 130, 75);
		colocar(diagrama, bd, 1540, 260, 160, 75);
		colocar(diagrama, parser, 400, 50, 160, 75);
		colocar(diagrama, autorizacion, 690, 50, 175, 75);
		colocar(diagrama, conexion, 1260, 55, 160, 75);
		colocar(diagrama, enviador, 250, 510, 180, 75);

		enlazar(admin, correo, "1: correo LISUSU[\"*\"] / 2: correo INSUSU[...]");
		enlazar(correo, procesador, "1.1 / 2.1: procesar(asunto, remitente)");
		enlazar(procesador, parser, "1.2 / 2.2: parsear(asunto)");
		enlazar(procesador, db, "1.3 / 2.3: iniciarSesion()");
		enlazar(db, conexion, "1.3.1 / 2.3.1: getConnection()");
		enlazar(procesador, autorizacion, "1.4 / 2.4: contextoDe, permisosDe y puede");
		enlazar(autorizacion, db, "1.4.1 / 2.4.1: consultar usuario y permisos");
		enlazar(procesador, usuario, "1.5 / 2.5: ejecutar(params, contexto)");
		enlazar(usuario, db, "1.6: tabla(SELECT) / 2.6: tx(INSERT)");
		enlazar(db, bd, "1.7: consultar usuarios / 2.7: registrar usuario");
		enlazar(correo, enviador, "1.8 / 2.8: enviar respuesta");
		enlazar(enviador, admin, "1.9 / 2.9: correo con resultado");

		paquete.Elements.Refresh();
		paquete.Diagrams.Refresh();
		diagrama.DiagramObjects.Refresh();
		Repository.RefreshModelView(paquete.PackageID);
		Repository.OpenDiagram(diagrama.DiagramID);
		Session.Output("Diagrama de comunicación CU1 creado correctamente.");
	}
	catch (error)
	{
		Session.Output("Error al generar el diagrama CU1: " + error.message +
			". Revise el paquete seleccionado; podrían haberse creado elementos parcialmente.");
	}
}

main();
