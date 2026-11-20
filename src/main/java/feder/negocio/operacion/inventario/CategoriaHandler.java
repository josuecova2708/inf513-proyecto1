package feder.negocio.operacion.inventario;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import static feder.parser.ParamSpec.*;

/** CU4 - Categorias del catalogo. */
public final class CategoriaHandler {

    private static final Edicion EDICION = new Edicion("categoria")
            .campo("nombre", texto("nombre", 80))
            .campo("descripcion", texto("descripcion", 200).opcional());

    private CategoriaHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISCAT", "CU4", "catalogo", Accion.VER, "Listar categorias de productos.",
                "LISCAT", CategoriaHandler::listar);
        r.cmd("INSCAT", "CU4", "catalogo", Accion.CREAR, "Registrar una categoria.",
                "INSCAT[\"Frutas\",\"Frutas frescas de temporada\"]", CategoriaHandler::insertar,
                texto("nombre", 80), texto("descripcion", 200).opcional());
        r.cmd("MODCAT", "CU4", "catalogo", Accion.EDITAR, "Modificar una categoria.",
                "MODCAT[\"1\",\"nombre\",\"Frutas tropicales\"]", CategoriaHandler::modificar,
                entero("id"), opcion("campo", EDICION.nombres()), texto("valor", 200).opcional());
        r.cmd("ELICAT", "CU4", "catalogo", Accion.ELIMINAR, "Eliminar una categoria sin productos.",
                "ELICAT[\"5\"]", CategoriaHandler::eliminar, entero("id"));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.tabla("SELECT c.id, c.nombre, c.descripcion, " +
                "count(p.id) FILTER (WHERE p.estado = 'activo') AS productos " +
                "FROM categoria c LEFT JOIN producto p ON p.categoria_id = c.id GROUP BY c.id ORDER BY c.nombre"));
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int id = Db.tx(con -> Db.<Integer>valor(con,
                "INSERT INTO categoria (nombre, descripcion) VALUES (?,?) RETURNING id", p.str(0), p.str(1)));
        return Respuesta.ok("Categoria registrada con id " + id + ".");
    }

    private static Respuesta modificar(Params p, Contexto ctx) throws Exception {
        Object v = EDICION.convertir(p.str(1), p.str(2));
        Db.tx(con -> { EDICION.aplicar(con, p.entero(0), p.str(1), v); return null; });
        return Respuesta.ok("Categoria " + p.entero(0) + " actualizada: " + p.str(1) + " = " + v);
    }

    private static Respuesta eliminar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        int n = Db.tx(con -> {
            if (Db.existe(con, "SELECT 1 FROM producto WHERE categoria_id = ?", id))
                throw new ErrorNegocio("La categoria " + id + " tiene productos; reasignelos antes de eliminarla.");
            return Db.update(con, "DELETE FROM categoria WHERE id = ?", id);
        });
        if (n == 0) throw new ErrorNegocio("No existe la categoria " + id);
        return Respuesta.ok("Categoria " + id + " eliminada.");
    }
}
