!INC Local Scripts.EAConstants-JScript

/* Seleccione un paquete en Enterprise Architect y ejecute el script.
 * Cada ejecución crea un diagrama y elementos nuevos en el paquete. */

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

function colocar(diagrama, elemento, izquierda, arriba, ancho, alto)
{
	// AddNew recibe las coordenadas verticales como números positivos.
	var posicion = "l=" + izquierda + ";r=" + (izquierda + ancho) +
		";t=" + arriba + ";b=" + (arriba + alto) + ";";
	var objeto = diagrama.DiagramObjects.AddNew(posicion, "");
	objeto.ElementID = elemento.ElementID;
	// Blanco solo para los casos de uso; los actores conservan su apariencia.
	if (elemento.Type == "UseCase") objeto.BackgroundColor = 16777215;
	guardar(objeto, "la posición de " + elemento.Name);
}

function asociar(actor, caso)
{
	var asociacion = actor.Connectors.AddNew("", "Association");
	asociacion.SupplierID = caso.ElementID;
	// El actor es el origen y el caso de uso es el destino de la flecha.
	asociacion.Direction = "Source -> Destination";
	guardar(asociacion, "la asociación de " + actor.Name + " con " + caso.Name);
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
		var diagrama = paquete.Diagrams.AddNew("Diagrama general de casos de uso - FEDER", "Use Case");
		guardar(diagrama, "el diagrama");
		var nombres = [
			"CU1. Gestión de Usuarios",
			"CU2. Gestión de Productores y Comunidades Agrícolas",
			"CU3. Gestión de Producción Agrícola, Asignar Lotes y Controlar Trazabilidad de Origen",
			"CU4. Gestión de Catálogo Unificado, Inventarios, Alertas y Control de Concurrencia",
			"CU5. Gestión de Comercio Electrónico, Carrito de Compras, Ventas Inmediatas y Encargos",
			"CU6. Gestión de Procesar Pedidos, Pagos Multicanal, Entregas y Notificaciones al Cliente",
			"CU7. Gestión de Indicadores Financieros",
			"CU8. Reportes y Estadísticas"
		];
		var casos = [];
		for (var i = 0; i < nombres.length; i++)
		{
			casos[i] = crearElemento(paquete, nombres[i], "UseCase", "");
			colocar(diagrama, casos[i], 290 + (i % 2) * 390,
				60 + Math.floor(i / 2) * 145, 350, 110);
		}
		var actores = [
			crearElemento(paquete, "Administrador", "Actor", "Administra cuentas y permisos, y supervisa las operaciones."),
			crearElemento(paquete, "Personal de FEDER", "Actor", "Opera el sistema según los permisos asignados."),
			crearElemento(paquete, "Productor/Agricultor", "Actor", "Gestiona su cuenta y participa en el registro de su producción."),
			crearElemento(paquete, "Cliente", "Actor", "Gestiona su cuenta, realiza compras y consulta sus pedidos.")
		];
		colocar(diagrama, actores[0], 45, 100, 155, 120);
		colocar(diagrama, actores[1], 45, 420, 155, 120);
		colocar(diagrama, actores[2], 1120, 100, 165, 120);
		colocar(diagrama, actores[3], 1120, 420, 165, 120);

		// Índices basados en cero: CU1 = 0, ..., CU8 = 7.
		var relaciones = [
			[0, 1, 2, 3, 6, 7],
			[0, 1, 2, 3, 4, 5, 6, 7],
			[0, 2],
			[0, 4, 5]
		];
		for (var a = 0; a < actores.length; a++)
			for (var j = 0; j < relaciones[a].length; j++)
				asociar(actores[a], casos[relaciones[a][j]]);

		paquete.Elements.Refresh();
		paquete.Diagrams.Refresh();
		diagrama.DiagramObjects.Refresh();
		Repository.RefreshModelView(paquete.PackageID);
		Repository.OpenDiagram(diagrama.DiagramID);
		Session.Output("Diagrama de FEDER creado correctamente: 4 actores, 8 casos de uso y 19 asociaciones.");
	}
	catch (error)
	{
		Session.Output("Error al generar el diagrama de FEDER: " + error.message +
			". Revise el paquete seleccionado; podrían haberse creado elementos parcialmente.");
	}
}

main();
