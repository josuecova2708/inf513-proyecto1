package feder.negocio.operacion.productores;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import static feder.parser.ParamSpec.*;

/** CU2 - Comunidades agricolas (RF02). */
public final class ComunidadHandler {

    private static final Edicion EDICION = new Edicion("comunidad")
            .campo("nombre", texto("nombre", 100))
            .campo("municipio", texto("municipio", 80))
            .campo("provincia", texto("provincia", 80).opcional())
            .campo("departamento", texto("departamento", 50))
            .campo("descripcion", texto("descripcion", 500).opcional());

    private ComunidadHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISCOM", "CU2", "comunidades", Accion.VER, "Listar comunidades (filtro opcional por municipio).",
                "LISCOM[\"*\"]   o   LISCOM[\"San Julian\"]", ComunidadHandler::listar,
                texto("municipio", 80).todos());
        r.cmd("INSCOM", "CU2", "comunidades", Accion.CREAR, "Registrar una comunidad.",
                "INSCOM[\"Comunidad Los Angeles\",\"San Julian\",\"Nuflo de Chavez\",\"Productores de arroz y mani\"]",
                ComunidadHandler::insertar,
                texto("nombre", 100), texto("municipio", 80), texto("provincia", 80).opcional(),
                texto("descripcion", 500).opcional());
        r.cmd("MODCOM", "CU2", "comunidades", Accion.EDITAR, "Modificar un dato de una comunidad.",
                "MODCOM[\"1\",\"provincia\",\"Ichilo\"]", ComunidadHandler::modificar,
                entero("id"), opcion("campo", EDICION.nombres()), texto("valor", 500).opcional());
        r.cmd("ELICOM", "CU2", "comunidades", Accion.ELIMINAR, "Eliminar una comunidad sin emprendimientos.",
                "ELICOM[\"3\"]", ComunidadHandler::eliminar, entero("id"));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT c.id, c.nombre, c.municipio, c.provincia, c.departamento, " +
                "       count(e.id) AS emprendimientos " +
                "FROM comunidad c LEFT JOIN emprendimiento e ON e.comunidad_id = c.id AND e.estado = 'activo' ";
        return Respuesta.ok(p.tiene(0)
                ? Db.tabla(sql + "WHERE c.municipio ILIKE ? GROUP BY c.id ORDER BY c.id", "%" + p.str(0) + "%")
                : Db.tabla(sql + "GROUP BY c.id ORDER BY c.id"));
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int id = Db.tx(con -> Db.<Integer>valor(con,
                "INSERT INTO comunidad (nombre, municipio, provincia, descripcion) VALUES (?,?,?,?) RETURNING id",
                p.str(0), p.str(1), p.str(2), p.str(3)));
        return Respuesta.ok("Comunidad registrada correctamente con id " + id + ".");
    }

    private static Respuesta modificar(Params p, Contexto ctx) throws Exception {
        Object v = EDICION.convertir(p.str(1), p.str(2));
        Db.tx(con -> { EDICION.aplicar(con, p.entero(0), p.str(1), v); return null; });
        return Respuesta.ok("Comunidad " + p.entero(0) + " actualizada: " + p.str(1) + " = " + v);
    }

    private static Respuesta eliminar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        int n = Db.tx(con -> {
            if (Db.existe(con, "SELECT 1 FROM emprendimiento WHERE comunidad_id = ?", id))
                throw new ErrorNegocio("La comunidad " + id + " tiene emprendimientos registrados; no se puede eliminar.");
            return Db.update(con, "DELETE FROM comunidad WHERE id = ?", id);
        });
        if (n == 0) throw new ErrorNegocio("No existe la comunidad " + id);
        return Respuesta.ok("Comunidad " + id + " eliminada.");
    }
}
