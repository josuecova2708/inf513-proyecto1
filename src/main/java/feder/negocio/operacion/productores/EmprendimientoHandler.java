package feder.negocio.operacion.productores;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.sql.Connection;
import java.util.Set;

import static feder.parser.ParamSpec.*;

/**
 * CU2 - Productores (emprendimientos) y los productos que ofrecen (RF02).
 * El Productor solo ve y modifica SU emprendimiento (datos de contacto y ofertas).
 */
public final class EmprendimientoHandler {

    static final String[] RUBROS = { "agricola", "artesania", "alimentos_procesados", "textil", "otro" };

    private static final Edicion EDICION = new Edicion("emprendimiento")
            .campo("nombre", texto("nombre", 120))
            .campo("comunidad_id", entero("comunidad_id"))
            .campo("rubro", opcion("rubro", RUBROS))
            .campo("responsable", texto("responsable", 120))
            .campo("ci_nit", ci("ci_nit").opcional())
            .campo("telefono", telefono("telefono").opcional())
            .campo("direccion", texto("direccion", 200).opcional())
            .campo("latitud", decimalSigno("latitud").opcional())
            .campo("longitud", decimalSigno("longitud").opcional())
            .campo("estado", opcion("estado", "activo", "inactivo"))
            .campo("usuario", "usuario_id", email("usuario").opcional());

    /** Campos que el propio Productor puede cambiar de su emprendimiento. */
    private static final Set<String> CAMPOS_PRODUCTOR =
            Set.of("responsable", "telefono", "direccion", "latitud", "longitud");

    private EmprendimientoHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISEMP", "CU2", "emprendimientos", Accion.VER,
                "Listar productores/emprendimientos (filtro por comunidad). El Productor ve solo el suyo.",
                "LISEMP[\"*\"]   o   LISEMP[\"1\"]", EmprendimientoHandler::listar,
                entero("comunidad_id").todos());
        r.cmd("INSEMP", "CU2", "emprendimientos", Accion.CREAR,
                "Registrar un productor/emprendimiento; opcionalmente vincular la cuenta (email) de un usuario Productor.",
                "INSEMP[\"1\",\"Arrocera Los Angeles\",\"agricola\",\"Pedro Choque\",\"4567890\",\"71122334\",\"Km 5 carretera\",\"pedro@gmail.com\"]",
                EmprendimientoHandler::insertar,
                entero("comunidad_id"), texto("nombre", 120), opcion("rubro", RUBROS), texto("responsable", 120),
                ci("ci_nit").opcional(), telefono("telefono").opcional(), texto("direccion", 200).opcional(),
                email("email_usuario").opcional(), decimalSigno("latitud").opcional(), decimalSigno("longitud").opcional());
        r.cmd("MODEMP", "CU2", "emprendimientos", Accion.EDITAR,
                "Modificar un dato del emprendimiento. El Productor solo: responsable, telefono, direccion, latitud, longitud.",
                "MODEMP[\"2\",\"telefono\",\"76655443\"]", EmprendimientoHandler::modificar,
                entero("id"), opcion("campo", EDICION.nombres()), texto("valor", 200).opcional());
        r.cmd("ELIEMP", "CU2", "emprendimientos", Accion.ELIMINAR, "Dar de baja (inactivar) un emprendimiento.",
                "ELIEMP[\"2\"]", EmprendimientoHandler::eliminar, entero("id"));

        r.cmd("LISOFE", "CU2", "ofertas", Accion.VER,
                "Productos que ofrece cada emprendimiento (precio de referencia y temporada).",
                "LISOFE[\"*\"]   o   LISOFE[\"2\"]", EmprendimientoHandler::listarOfertas,
                entero("emprendimiento_id").todos());
        r.cmd("INSOFE", "CU2", "ofertas", Accion.CREAR,
                "Registrar (o actualizar) un producto ofrecido por un emprendimiento.",
                "INSOFE[\"2\",\"1\",\"9.50\",\"marzo-junio\"]", EmprendimientoHandler::insertarOferta,
                entero("emprendimiento_id"), entero("producto_id"), decimal("precio_referencia").opcional(),
                texto("temporada", 50).opcional());
        r.cmd("ELIOFE", "CU2", "ofertas", Accion.ELIMINAR, "Quitar un producto de lo ofrecido por un emprendimiento.",
                "ELIOFE[\"2\",\"1\"]", EmprendimientoHandler::eliminarOferta,
                entero("emprendimiento_id"), entero("producto_id"));
    }

    /** El Productor solo puede operar sobre su propio emprendimiento. */
    static void verificarPropio(Contexto ctx, int emprendimientoId) {
        if (ctx.esProductor() && (ctx.emprendimientoId == null || ctx.emprendimientoId != emprendimientoId)) {
            throw new ErrorNegocio("Solo puede operar sobre su propio emprendimiento"
                    + (ctx.emprendimientoId != null ? " (id " + ctx.emprendimientoId + ")." : "; aun no tiene uno asignado."));
        }
    }

    private static Integer usuarioProductor(Connection con, String email) throws Exception {
        if (email == null) return null;
        Fila u = Db.fila(con, "SELECT u.id, r.nombre AS rol FROM usuario u JOIN rol r ON r.id = u.rol_id " +
                "WHERE lower(u.email) = lower(?)", email);
        if (u == null) throw new ErrorNegocio("No existe un usuario con el correo " + email
                + ". Primero creelo con INSUSU[\"productor\",...].");
        if (!"Productor".equals(u.str("rol"))) throw new ErrorNegocio("El usuario " + email + " no tiene rol Productor.");
        return u.entero("id");
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT e.id, e.nombre, e.rubro, e.responsable, c.nombre AS comunidad, c.municipio, " +
                "       e.telefono, u.email AS cuenta, e.estado " +
                "FROM emprendimiento e JOIN comunidad c ON c.id = e.comunidad_id " +
                "LEFT JOIN usuario u ON u.id = e.usuario_id WHERE 1=1 ";
        if (ctx.esProductor()) {
            return Respuesta.ok(Db.tabla(sql + "AND e.usuario_id = ?", ctx.usuarioId));
        }
        return Respuesta.ok(p.tiene(0)
                ? Db.tabla(sql + "AND e.comunidad_id = ? ORDER BY e.id", p.entero(0))
                : Db.tabla(sql + "ORDER BY c.nombre, e.id"));
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int id = Db.tx(con -> Db.<Integer>valor(con,
                "INSERT INTO emprendimiento (comunidad_id, nombre, rubro, responsable, ci_nit, telefono, direccion, " +
                "usuario_id, latitud, longitud) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id",
                p.entero(0), p.str(1), p.str(2), p.str(3), p.str(4), p.str(5), p.str(6),
                usuarioProductor(con, p.str(7)), p.dec(8), p.dec(9)));
        return Respuesta.ok("Emprendimiento registrado correctamente con id " + id + "."
                + (p.tiene(7) ? "\nCuenta vinculada: " + p.str(7) + " (ya puede reportar produccion con INSPRC)." : ""));
    }

    private static Respuesta modificar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String campo = p.str(1);
        verificarPropio(ctx, id);
        if (ctx.esProductor() && !CAMPOS_PRODUCTOR.contains(campo)) {
            throw new ErrorNegocio("Como Productor solo puede modificar: " + String.join(", ", CAMPOS_PRODUCTOR));
        }
        Object valor = EDICION.convertir(campo, p.str(2));
        Db.tx(con -> {
            Object v = campo.equals("usuario") ? usuarioProductor(con, (String) valor) : valor;
            EDICION.aplicar(con, id, campo, v);
            return null;
        });
        return Respuesta.ok("Emprendimiento " + id + " actualizado: " + campo + " = " + valor);
    }

    private static Respuesta eliminar(Params p, Contexto ctx) throws Exception {
        int n = Db.tx(con -> Db.update(con, "UPDATE emprendimiento SET estado = 'inactivo' WHERE id = ?", p.entero(0)));
        if (n == 0) throw new ErrorNegocio("No existe el emprendimiento " + p.entero(0));
        return Respuesta.ok("Emprendimiento " + p.entero(0) + " dado de baja (inactivo).");
    }

    private static Respuesta listarOfertas(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT e.id AS emp_id, e.nombre AS emprendimiento, pr.id AS prod_id, pr.nombre AS producto, " +
                "       ep.precio_referencia, ep.temporada " +
                "FROM emprendimiento_producto ep JOIN emprendimiento e ON e.id = ep.emprendimiento_id " +
                "JOIN producto pr ON pr.id = ep.producto_id WHERE 1=1 ";
        if (ctx.esProductor()) {
            return Respuesta.ok(Db.tabla(sql + "AND e.usuario_id = ? ORDER BY pr.nombre", ctx.usuarioId));
        }
        return Respuesta.ok(p.tiene(0)
                ? Db.tabla(sql + "AND e.id = ? ORDER BY pr.nombre", p.entero(0))
                : Db.tabla(sql + "ORDER BY e.nombre, pr.nombre"));
    }

    private static Respuesta insertarOferta(Params p, Contexto ctx) throws Exception {
        verificarPropio(ctx, p.entero(0));
        Db.tx(con -> Db.update(con,
                "INSERT INTO emprendimiento_producto (emprendimiento_id, producto_id, precio_referencia, temporada) " +
                "VALUES (?,?,?,?) ON CONFLICT (emprendimiento_id, producto_id) DO UPDATE " +
                "SET precio_referencia = EXCLUDED.precio_referencia, temporada = EXCLUDED.temporada",
                p.entero(0), p.entero(1), p.dec(2), p.str(3)));
        return Respuesta.ok("Oferta registrada: emprendimiento " + p.entero(0) + " ofrece el producto " + p.entero(1) + ".");
    }

    private static Respuesta eliminarOferta(Params p, Contexto ctx) throws Exception {
        verificarPropio(ctx, p.entero(0));
        int n = Db.tx(con -> Db.update(con,
                "DELETE FROM emprendimiento_producto WHERE emprendimiento_id = ? AND producto_id = ?",
                p.entero(0), p.entero(1)));
        if (n == 0) throw new ErrorNegocio("Ese emprendimiento no ofrece ese producto.");
        return Respuesta.ok("Oferta eliminada.");
    }
}
