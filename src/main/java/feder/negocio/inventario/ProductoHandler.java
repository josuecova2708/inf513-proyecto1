package feder.negocio.inventario;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import static feder.parser.ParamSpec.*;

/** CU4 - Catalogo unificado de productos y disponibilidad (RF04, RF07, RF08). */
public final class ProductoHandler {

    static final String[] MODALIDADES = { "inmediata", "encargo", "ambas" };

    private static final Edicion EDICION = new Edicion("producto")
            .campo("categoria_id", entero("categoria_id"))
            .campo("nombre", texto("nombre", 120))
            .campo("descripcion", texto("descripcion", 1000).opcional())
            .campo("imagen", texto("imagen", 255).opcional())
            .campo("presentacion", texto("presentacion", 80).opcional())
            .campo("unidad_medida", texto("unidad_medida", 20))
            .campo("precio_venta", decimal("precio_venta"))
            .campo("modalidad", "modalidad_venta", opcion("modalidad", MODALIDADES))
            .campo("stock_minimo", decimal("stock_minimo"))
            .campo("estado", opcion("estado", "activo", "inactivo"));

    private ProductoHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISPRO", "CU4", "catalogo", Accion.VER,
                "Catalogo de productos con precio, modalidad (inmediata/encargo) y stock disponible.",
                "LISPRO[\"*\"]   o   LISPRO[\"2\"] (por categoria)", ProductoHandler::listar,
                entero("categoria_id").todos());
        r.cmd("VERPRO", "CU4", "catalogo", Accion.VER,
                "Ficha completa de un producto: descripcion, disponibilidad y emprendimientos que lo producen.",
                "VERPRO[\"3\"]", ProductoHandler::ver, entero("id"));
        r.cmd("DISPRO", "CU4", "catalogo", Accion.VER,
                "Disponibilidad: stock propio de FEDER, reservado, produccion reportada y produccion futura.",
                "DISPRO[\"*\"]   o   DISPRO[\"3\"]", ProductoHandler::disponibilidad,
                entero("producto_id").todos());
        r.cmd("INSPRO", "CU4", "catalogo", Accion.CREAR, "Registrar un producto en el catalogo.",
                "INSPRO[\"2\",\"Arroz grano de oro\",\"kg\",\"9.50\",\"ambas\",\"100\",\"Bolsa 1 kg\",\"Arroz de San Julian\",\"https://.../arroz.jpg\"]",
                ProductoHandler::insertar,
                entero("categoria_id"), texto("nombre", 120), texto("unidad_medida", 20), decimal("precio_venta"),
                opcion("modalidad", MODALIDADES), decimal("stock_minimo").opcional(), texto("presentacion", 80).opcional(),
                texto("descripcion", 1000).opcional(), texto("imagen", 255).opcional());
        r.cmd("MODPRO", "CU4", "catalogo", Accion.EDITAR, "Modificar un dato de un producto.",
                "MODPRO[\"3\",\"precio_venta\",\"10.00\"]", ProductoHandler::modificar,
                entero("id"), opcion("campo", EDICION.nombres()), texto("valor", 1000).opcional());
        r.cmd("ELIPRO", "CU4", "catalogo", Accion.ELIMINAR, "Dar de baja (inactivar) un producto del catalogo.",
                "ELIPRO[\"3\"]", ProductoHandler::eliminar, entero("id"));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT p.id, p.nombre, c.nombre AS categoria, p.presentacion, p.unidad_medida AS unidad, " +
                "       p.precio_venta AS precio_bs, p.modalidad_venta AS modalidad, " +
                "       d.stock_disponible AS disponible" + (ctx.esStaff() ? ", p.estado " : " ") +
                "FROM producto p JOIN categoria c ON c.id = p.categoria_id " +
                "JOIN v_disponibilidad_producto d ON d.producto_id = p.id " +
                "WHERE (?::int IS NULL OR p.categoria_id = ?) " +
                (ctx.esStaff() ? "" : "AND p.estado = 'activo' ") +
                "ORDER BY c.nombre, p.nombre";
        String tabla = Db.tabla(sql, p.entero(0), p.entero(0));
        return Respuesta.ok("CATALOGO FEDER\n" + tabla
                + "\n\nModalidad: inmediata = compra con INSCAR | encargo = pedir con INSENC | ambas = cualquiera."
                + "\nDetalle: VERPRO[\"id\"]");
    }

    private static Respuesta ver(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.con(con -> {
            Fila f = Db.fila(con, "SELECT p.*, c.nombre AS categoria, d.stock_disponible, d.produccion_futura " +
                    "FROM producto p JOIN categoria c ON c.id = p.categoria_id " +
                    "JOIN v_disponibilidad_producto d ON d.producto_id = p.id WHERE p.id = ?", p.entero(0));
            if (f == null || (!ctx.esStaff() && !"activo".equals(f.str("estado"))))
                throw new ErrorNegocio("No existe el producto " + p.entero(0));
            StringBuilder sb = new StringBuilder();
            sb.append(f.str("nombre")).append("  (id ").append(f.str("id")).append(")\n");
            sb.append("Categoria:    ").append(f.str("categoria")).append('\n');
            sb.append("Presentacion: ").append(n(f.str("presentacion"))).append('\n');
            sb.append("Precio:       Bs ").append(f.str("precio_venta")).append(" por ").append(f.str("unidad_medida")).append('\n');
            sb.append("Modalidad:    ").append(f.str("modalidad_venta")).append('\n');
            sb.append("Disponible:   ").append(f.str("stock_disponible")).append(' ').append(f.str("unidad_medida")).append('\n');
            sb.append("Prod. futura: ").append(f.str("produccion_futura")).append('\n');
            sb.append("Imagen:       ").append(n(f.str("imagen"))).append('\n');
            sb.append("Estado:       ").append(f.str("estado")).append("\n\n");
            sb.append(n(f.str("descripcion"))).append("\n\nOrigen (emprendimientos que lo ofrecen):\n");
            sb.append(Db.tabla(con, "SELECT e.nombre AS emprendimiento, c.nombre AS comunidad, c.municipio, ep.temporada " +
                    "FROM emprendimiento_producto ep JOIN emprendimiento e ON e.id = ep.emprendimiento_id " +
                    "JOIN comunidad c ON c.id = e.comunidad_id WHERE ep.producto_id = ? AND e.estado = 'activo'", p.entero(0)));
            return sb.toString();
        }));
    }

    private static String n(String s) { return s == null ? "-" : s; }

    private static Respuesta disponibilidad(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.tabla(
                "SELECT d.producto_id AS id, d.nombre, p.unidad_medida AS unidad, d.stock_disponible AS stock_feder, " +
                "       d.stock_reservado AS reservado, d.produccion_reportada AS reportada_sin_validar, " +
                "       d.produccion_futura AS futura, d.stock_minimo AS minimo, " +
                "       CASE WHEN d.stock_disponible < d.stock_minimo THEN 'BAJO' ELSE 'OK' END AS nivel " +
                "FROM v_disponibilidad_producto d JOIN producto p ON p.id = d.producto_id " +
                "WHERE p.estado = 'activo' AND (?::int IS NULL OR d.producto_id = ?) ORDER BY d.nombre",
                p.entero(0), p.entero(0)));
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int id = Db.tx(con -> Db.<Integer>valor(con,
                "INSERT INTO producto (categoria_id, nombre, unidad_medida, precio_venta, modalidad_venta, stock_minimo, " +
                "presentacion, descripcion, imagen) VALUES (?,?,?,?,?, COALESCE(?, 0), ?,?,?) RETURNING id",
                p.entero(0), p.str(1), p.str(2), p.dec(3), p.str(4), p.dec(5), p.str(6), p.str(7), p.str(8)));
        return Respuesta.ok("Producto registrado con id " + id + ".");
    }

    private static Respuesta modificar(Params p, Contexto ctx) throws Exception {
        Object v = EDICION.convertir(p.str(1), p.str(2));
        Db.tx(con -> { EDICION.aplicar(con, p.entero(0), p.str(1), v); return null; });
        return Respuesta.ok("Producto " + p.entero(0) + " actualizado: " + p.str(1) + " = " + v
                + (p.str(1).equals("precio_venta") ? "\n(Los pedidos ya realizados conservan el precio con que se compraron.)" : ""));
    }

    private static Respuesta eliminar(Params p, Contexto ctx) throws Exception {
        int n = Db.tx(con -> Db.update(con, "UPDATE producto SET estado = 'inactivo' WHERE id = ?", p.entero(0)));
        if (n == 0) throw new ErrorNegocio("No existe el producto " + p.entero(0));
        return Respuesta.ok("Producto " + p.entero(0) + " dado de baja del catalogo (inactivo).");
    }
}
