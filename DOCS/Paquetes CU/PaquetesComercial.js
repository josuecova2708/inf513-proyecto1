!INC Local Scripts.EAConstants-JScript

/* Seleccione un paquete en Enterprise Architect y ejecute el script.
 * Cada ejecución crea un paquete, sus casos de uso y un diagrama nuevos. */

function guardar(objeto, descripcion)
{
	if (!objeto.Update())
		throw new Error("No se pudo guardar " + descripcion + ": " + objeto.GetLastError());
}

function colocar(diagrama, elemento, izquierda, arriba, ancho, alto, color)
{
	var posicion = "l=" + izquierda + ";r=" + (izquierda + ancho) +
		";t=" + arriba + ";b=" + (arriba + alto) + ";";
	var objeto = diagrama.DiagramObjects.AddNew(posicion, "");
	objeto.ElementID = elemento.ElementID;
	objeto.BackgroundColor = color;
	guardar(objeto, "la posición de " + elemento.Name);
}

function main()
{
	var padre = Repository.GetTreeSelectedPackage();
	if (padre == null)
	{
		Session.Output("Seleccione un paquete en el navegador antes de ejecutar el script.");
		return;
	}

	try
	{
		var nombrePaquete = "Gestión Comercial";
		var nombres = [
			"CU5. Gestión de Comercio Electrónico, Carrito de Compras, Ventas Inmediatas y Encargos",
			"CU6. Gestión de Procesar Pedidos, Pagos Multicanal, Entregas y Notificaciones al Cliente"
		];
		var paquete = padre.Packages.AddNew(nombrePaquete, "");
		guardar(paquete, "el paquete " + nombrePaquete);
		var diagrama = padre.Diagrams.AddNew(nombrePaquete, "Use Case");
		guardar(diagrama, "el diagrama " + nombrePaquete);
		// Un paquete se representa en el diagrama mediante su elemento asociado.
		var elementoPaquete = paquete.Element;
		colocar(diagrama, elementoPaquete, 60, 150, 270, 170, 13166075);

		for (var i = 0; i < nombres.length; i++)
		{
			var caso = padre.Elements.AddNew(nombres[i], "UseCase");
			guardar(caso, nombres[i]);
			colocar(diagrama, caso, 470, 45 + i * 155, 380, 115, 16777215);
			var relacion = elementoPaquete.Connectors.AddNew("", "Association");
			relacion.SupplierID = caso.ElementID;
			relacion.Direction = "Source -> Destination";
			guardar(relacion, "la relación con " + nombres[i]);
		}
		padre.Elements.Refresh();
		padre.Packages.Refresh();
		padre.Diagrams.Refresh();
		diagrama.DiagramObjects.Refresh();
		Repository.RefreshModelView(padre.PackageID);
		Repository.OpenDiagram(diagrama.DiagramID);
		Session.Output("Diagrama creado: " + nombrePaquete);
	}
	catch (error)
	{
		Session.Output("Error al generar el diagrama: " + error.message +
			". Revise el paquete seleccionado; podrían haberse creado elementos parcialmente.");
	}
}

main();
