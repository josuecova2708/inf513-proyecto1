!INC Local Scripts.EAConstants-JScript

/* Seleccione un paquete en Enterprise Architect y ejecute este script.
 * Cada ejecución crea un diagrama y elementos nuevos en el paquete. */

function guardar(objeto, descripcion)
{
	if (!objeto.Update())
		throw new Error("No se pudo guardar " + descripcion + ": " + objeto.GetLastError());
}

function crearElemento(paquete, nombre, tipo)
{
	var elemento = paquete.Elements.AddNew(nombre, tipo);
	guardar(elemento, nombre);
	return elemento;
}

function colocar(diagrama, elemento, izquierda, arriba, ancho, alto)
{
	var posicion = "l=" + izquierda + ";r=" + (izquierda + ancho) +
		";t=" + arriba + ";b=" + (arriba + alto) + ";";
	var objeto = diagrama.DiagramObjects.AddNew(posicion, "");
	objeto.ElementID = elemento.ElementID;
	if (elemento.Type == "UseCase") objeto.BackgroundColor = 16777215;
	guardar(objeto, "la posición de " + elemento.Name);
}

function asociar(actor, caso)
{
	var asociacion = actor.Connectors.AddNew("", "Association");
	asociacion.SupplierID = caso.ElementID;
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
		var nombre = "CU3. Gestión de Producción Agrícola, Asignar Lotes y Controlar Trazabilidad de Origen";
		var nombresActores = ["Administrador","Personal de FEDER","Productor/Agricultor"];
		var caso = crearElemento(paquete, nombre, "UseCase");
		var diagrama = paquete.Diagrams.AddNew(nombre, "Use Case");
		guardar(diagrama, "el diagrama de " + nombre);
		colocar(diagrama, caso, 360, 110, 400, 140);

		for (var i = 0; i < nombresActores.length; i++)
		{
			var actor = crearElemento(paquete, nombresActores[i], "Actor");
			var izquierda = (i % 2 == 0) ? 60 : 950;
			var arriba = (i < 2) ? 95 : 270;
			colocar(diagrama, actor, izquierda, arriba, 150, 120);
			asociar(actor, caso);
		}

		diagrama.DiagramObjects.Refresh();
		paquete.Elements.Refresh();
		paquete.Diagrams.Refresh();
		Repository.RefreshModelView(paquete.PackageID);
		Repository.OpenDiagram(diagrama.DiagramID);
		Session.Output("Diagrama creado: " + nombre);
	}
	catch (error)
	{
		Session.Output("Error al generar el diagrama: " + error.message +
			". Revise el paquete seleccionado; podrían haberse creado elementos parcialmente.");
	}
}

main();
