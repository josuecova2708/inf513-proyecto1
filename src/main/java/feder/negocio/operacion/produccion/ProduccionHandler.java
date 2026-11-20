package feder.negocio.operacion.produccion;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.time.LocalDate;

import static feder.parser.ParamSpec.*;

/**
 * CU3 - Produccion agricola reportada por los productores y su validacion por FEDER (RF03).
 * Flujo: Productor INSPRC (reportada) -> Personal VALPRC (validada|rechazada) -> Personal INSLOT (ingresa stock).
 */
public final class ProduccionHandler {

    private ProduccionHandler() {}

    public static void registrar(Registro r) {
        r.cmd("INSPRC", "CU3", "produccion", Accion.CREAR,
                "Reportar produccion: disponible (ya cosechada) o futura. El Productor solo para su emprendimiento.",
                "INSPRC[\"1\",\"3\",\"disponible\",\"500\",\"kg\",\"2026-11-10\",\"2026-11-15\",\"8.50\",\"Parcela norte, San Julian\"]",
                ProduccionHandler::insertar,
                entero("emprendimiento_id"), entero("producto_id"), opcion("tipo", "disponible", "futura"),
                decimal("cantidad"), texto("unidad_medida", 20), fecha("fecha_produccion").opcional(),
                fecha("fecha_disponible").opcional(), decimal("precio_propuesto").opcional(),
                texto("procedencia", 200).opcional());
        r.cmd("LISPRC", "CU3", "produccion", Accion.VER,
                "Listar produccion reportada por estado. El Productor ve solo la suya.",
                "LISPRC[\"reportada\"]   o   LISPRC[\"*\"]", ProduccionHandler::listar,
                opcion("estado", "reportada", "validada", "rechazada").todos());
        r.cmd("VALPRC", "CU3", "produccion", Accion.EDITAR,
                "Validar o rechazar una produccion reportada (Personal de FEDER).",
                "VALPRC[\"4\",\"validada\",\"Calidad verificada en campo\"]", ProduccionHandler::validar,
                entero("id"), opcion("decision", "validada", "rechazada"), texto("observacion", 500).opcional());
        r.cmd("REPPRD", "CU8", "produccion", Accion.VER,
                "Reporte de produccion por comunidad y emprendimiento en un rango de fechas (Productor: solo la suya).",
                "REPPRD[\"2026-01-01\",\"2026-12-31\"]", ProduccionHandler::reporte,
                fecha("fecha_inicio"), fecha("fecha_fin"));
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int empId = p.entero(0);
        if (ctx.esProductor() && (ctx.emprendimientoId == null || ctx.emprendimientoId != empId)) {
            throw new ErrorNegocio("Solo puede reportar produccion de su propio emprendimiento"
                    + (ctx.emprendimientoId != null ? " (id " + ctx.emprendimientoId + ")." : "; aun no tiene uno asignado."));
        }
        LocalDate fProd = p.fecha(5), fDisp = p.fecha(6);
        if (fProd != null && fDisp != null && fDisp.isBefore(fProd)) {
            throw new ErrorNegocio("La fecha de disponibilidad no puede ser anterior a la de produccion.");
        }
        if ("futura".equals(p.str(2)) && fDisp == null) {
            throw new ErrorNegocio("Para produccion futura indique la fecha_disponible estimada.");
        }
        Object[] r = Db.tx(con -> {
            Fila emp = Db.fila(con, "SELECT nombre, estado FROM emprendimiento WHERE id = ?", empId);
            if (emp == null) throw new ErrorNegocio("No existe el emprendimiento " + empId);
            if (!"activo".equals(emp.str("estado"))) throw new ErrorNegocio("El emprendimiento esta inactivo.");
            Fila prod = Db.fila(con, "SELECT nombre, estado FROM producto WHERE id = ?", p.entero(1));
            if (prod == null) throw new ErrorNegocio("No existe el producto " + p.entero(1) + ". Vea LISPRO[\"*\"].");
            boolean ofrece = Db.existe(con, "SELECT 1 FROM emprendimiento_producto " +
                    "WHERE emprendimiento_id = ? AND producto_id = ?", empId, p.entero(1));
            int id = Db.<Integer>valor(con,
                    "INSERT INTO produccion (emprendimiento_id, producto_id, tipo, cantidad, unidad_medida, " +
                    "fecha_produccion, fecha_disponible, precio_propuesto, procedencia) " +
                    "VALUES (?,?,?,?,?,?,?,?,?) RETURNING id",
                    empId, p.entero(1), p.str(2), p.dec(3), p.str(4), fProd, fDisp, p.dec(7), p.str(8));
            return new Object[] { id, prod.str("nombre"), ofrece };
        });
        return Respuesta.ok("Produccion registrada con id " + r[0] + " (" + p.dec(3) + " " + p.str(4) + " de "
                + r[1] + ", tipo " + p.str(2) + "). Estado: REPORTADA, pendiente de validacion por FEDER."
                + (!(Boolean) r[2] ? "\nNota: este producto aun no figura en sus ofertas; puede agregarlo con INSOFE." : ""));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT pr.id, e.nombre AS emprendimiento, p.nombre AS producto, pr.tipo, pr.cantidad, " +
                "       pr.unidad_medida AS unidad, pr.fecha_produccion AS cosecha, pr.fecha_disponible AS disponible, " +
                "       pr.precio_propuesto AS precio, pr.estado, " +
                "       COALESCE((SELECT sum(l.cantidad_inicial) FROM lote l WHERE l.produccion_id = pr.id), 0) AS en_lotes " +
                "FROM produccion pr JOIN emprendimiento e ON e.id = pr.emprendimiento_id " +
                "JOIN producto p ON p.id = pr.producto_id " +
                "WHERE (?::text IS NULL OR pr.estado = ?) ";
        if (ctx.esProductor()) {
            return Respuesta.ok(Db.tabla(sql + "AND e.usuario_id = ? ORDER BY pr.id DESC",
                    p.str(0), p.str(0), ctx.usuarioId));
        }
        return Respuesta.ok(Db.tabla(sql + "ORDER BY pr.id DESC LIMIT 200", p.str(0), p.str(0)));
    }

    private static Respuesta validar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String decision = p.str(1);
        Db.tx(con -> {
            Fila f = Db.fila(con, "SELECT estado FROM produccion WHERE id = ? FOR UPDATE", id);
            if (f == null) throw new ErrorNegocio("No existe la produccion " + id);
            if (!"reportada".equals(f.str("estado")))
                throw new ErrorNegocio("La produccion " + id + " ya fue " + f.str("estado") + ".");
            Db.update(con, "UPDATE produccion SET estado = ?, validado_por = ?, fecha_validacion = now(), " +
                    "observacion = ? WHERE id = ?", decision, ctx.usuarioId, p.str(2), id);
            return null;
        });
        return Respuesta.ok("Produccion " + id + " " + decision.toUpperCase() + "."
                + (decision.equals("validada")
                    ? "\nSiguiente paso: ingresar el stock con INSLOT[\"" + id + "\",\"codigo_lote\",\"cantidad\",\"vencimiento?\",\"costo?\"]"
                    : ""));
    }

    private static Respuesta reporte(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT c.nombre AS comunidad, e.nombre AS emprendimiento, p.nombre AS producto, " +
                "  sum(pr.cantidad) FILTER (WHERE pr.estado = 'reportada') AS reportada, " +
                "  sum(pr.cantidad) FILTER (WHERE pr.estado = 'validada' AND pr.tipo = 'disponible') AS validada, " +
                "  sum(pr.cantidad) FILTER (WHERE pr.estado = 'validada' AND pr.tipo = 'futura') AS futura, " +
                "  sum(pr.cantidad) FILTER (WHERE pr.estado = 'rechazada') AS rechazada, " +
                "  COALESCE(sum((SELECT sum(rl.cantidad) FROM lote l JOIN reserva_lote rl ON rl.lote_id = l.id " +
                "      WHERE l.produccion_id = pr.id AND rl.estado = 'confirmada')), 0) AS vendida " +
                "FROM produccion pr JOIN emprendimiento e ON e.id = pr.emprendimiento_id " +
                "JOIN comunidad c ON c.id = e.comunidad_id JOIN producto p ON p.id = pr.producto_id " +
                "WHERE pr.fecha_registro >= ? AND pr.fecha_registro < ?::date + 1 ";
        String tabla = ctx.esProductor()
                ? Db.tabla(sql + "AND e.usuario_id = ? GROUP BY 1,2,3 ORDER BY 1,2,3", p.fecha(0), p.fecha(1), ctx.usuarioId)
                : Db.tabla(sql + "GROUP BY 1,2,3 ORDER BY 1,2,3", p.fecha(0), p.fecha(1));
        return Respuesta.ok("Produccion del " + p.fecha(0) + " al " + p.fecha(1) + "\n\n" + tabla);
    }
}
