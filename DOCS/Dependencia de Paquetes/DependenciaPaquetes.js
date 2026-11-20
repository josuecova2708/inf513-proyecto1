!INC Local Scripts.EAConstants-JScript

/* Seleccione un paquete en el navegador de Enterprise Architect y ejecute.
 * Se crean un diagrama y tres paquetes nuevos en el paquete seleccionado. */

function guardar(objeto, descripcion)
{
	if (!objeto.Update())
		throw new Error("No se pudo guardar " + descripcion + ": " + objeto.GetLastError());
}

function crearPaquete(padre, nombre)
{
	var paquete = padre.Packages.AddNew(nombre, "");
	guardar(paquete, "el paquete " + nombre);
	return paquete;
}

function colocar(diagrama, paquete, izquierda, arriba, ancho, alto)
{
	var posicion = "l=" + izquierda + ";r=" + (izquierda + ancho) +
		";t=" + arriba + ";b=" + (arriba + alto) + ";";
	var objeto = diagrama.DiagramObjects.AddNew(posicion, "");
	objeto.ElementID = paquete.Element.ElementID;
	objeto.BackgroundColor = 16777215;
	guardar(objeto, "la posición de " + paquete.Name);
}

function depender(origen, destino)
{
	var dependencia = origen.Element.Connectors.AddNew("", "Dependency");
	dependencia.SupplierID = destino.Element.ElementID;
	guardar(dependencia, "la dependencia de " + origen.Name + " a " + destino.Name);
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
		var operacion = crearPaquete(padre, "Gestión de Operación");
		var comercial = crearPaquete(padre, "Gestión Comercial");
		var administracion = crearPaquete(padre, "Gestión de Administración");
		var diagrama = padre.Diagrams.AddNew("Dependencia de paquetes - FEDER", "Logical");
		guardar(diagrama, "el diagrama de dependencias");

		colocar(diagrama, operacion, 60, 50, 250, 130);
		colocar(diagrama, comercial, 480, 50, 250, 130);
		colocar(diagrama, administracion, 270, 280, 250, 130);

		// Las flechas siguen el sentido de la imagen de referencia.
		depender(operacion, comercial);
		depender(administracion, operacion);
		depender(administracion, comercial);

		padre.Packages.Refresh();
		padre.Diagrams.Refresh();
		diagrama.DiagramObjects.Refresh();
		Repository.RefreshModelView(padre.PackageID);
		Repository.OpenDiagram(diagrama.DiagramID);
		Session.Output("Diagrama de dependencia de paquetes de FEDER creado correctamente.");
	}
	catch (error)
	{
		Session.Output("Error al generar el diagrama: " + error.message +
			". Revise el paquete seleccionado; podrían haberse creado elementos parcialmente.");
	}
}

main();
