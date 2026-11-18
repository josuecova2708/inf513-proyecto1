package feder.negocio.produccion;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.math.BigDecimal;

import static feder.parser.ParamSpec.*;

/** CU3 - Lotes de inventario y trazabilidad de origen (RF05, RF06). */
public final class LoteHandler {

    private LoteHandler() {}

    public static void registrar(Registro r) {
        r.cmd("INSLOT", "CU3", "lotes", Accion.CREAR,
                "Ingresar al inventario un lote de una produccion VALIDADA (registra el movimiento de entrada).",
                "INSLOT[\"4\",\"ARR-2026-001\",\"500\",\"2027-05-30\",\"6.20\"]", LoteHandler::insertar,
                entero("produccion_id"), texto("codigo", 30), decimal("cantidad"),
                fecha("fecha_vencimiento").opcional(), decimal("costo_unitario").opcional());
        r.cmd("LISLOT", "CU3", "lotes", Accion.VER,
                "Listar lotes (filtro por producto). El Productor ve solo los lotes de su produccion.",
                "LISLOT[\"*\"]   o   LISLOT[\"3\"]", LoteHandler::listar, entero("producto_id").todos());
        r.cmd("TRALOT", "CU3", "lotes", Accion.VER,
                "Trazabilidad de un lote: origen (comunidad, emprendimiento, cosecha), movimientos y pedidos donde se vendio.",
                "TRALOT[\"ARR-2026-001\"]", LoteHandler::trazarLote, texto("codigo", 30));
        r.cmd("TRAPED", "CU3", "pedidos", Accion.VER,
                "Trazabilidad de un pedido: de que lote, emprendimiento y comunidad vino cada producto.",
                "TRAPED[\"12\"]", LoteHandler::trazarPedido, entero("pedido_id"));
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int prodId = p.entero(0);
        Object[] r = Db.tx(con -> {
            Fila pr = Db.fila(con, "SELECT pr.estado, pr.cantidad, pr.producto_id, p.nombre, " +
                    "  COALESCE((SELECT sum(cantidad_inicial) FROM lote WHERE produccion_id = pr.id), 0) AS ingresado " +
                    "FROM produccion pr JOIN producto p ON p.id = pr.producto_id WHERE pr.id = ? FOR UPDATE OF pr", prodId);
            if (pr == null) throw new ErrorNegocio("No existe la produccion " + prodId);
            if (!"validada".equals(pr.str("estado")))
                throw new ErrorNegocio("La produccion " + prodId + " esta " + pr.str("estado")
                        + "; primero debe validarse con VALPRC.");
            BigDecimal restante = pr.dec("cantidad").subtract(pr.dec("ingresado"));
            if (p.dec(2).compareTo(restante) > 0)
                throw new ErrorNegocio("La cantidad excede lo validado: quedan " + restante + " por ingresar de esa produccion.");
            int loteId = Db.<Integer>valor(con, "SELECT fn_ingresar_lote(?, ?, ?, ?, ?, ?)",
                    prodId, p.str(1), p.dec(2), ctx.usuarioId, p.fecha(3), p.dec(4));
            int encargos = Db.entero(con, "SELECT count(DISTINCT e.id) FROM encargo e " +
                    "JOIN detalle_encargo de ON de.encargo_id = e.id WHERE de.producto_id = ? AND e.estado = 'aceptado'",
                    pr.entero("producto_id"));
            return new Object[] { loteId, pr.str("nombre"), encargos };
        });
        int encargos = (Integer) r[2];
        return Respuesta.ok("Lote " + p.str(1) + " ingresado (id " + r[0] + "): " + p.dec(2) + " de " + r[1]
                + ". Movimiento de ENTRADA registrado."
                + (encargos > 0 ? "\nAtencion: hay " + encargos + " encargo(s) aceptado(s) esperando este producto. "
                        + "Revise LISENC[\"aceptado\"] y marquelos con MODENC[\"id\",\"disponible\"]." : ""));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT l.id, l.codigo, p.nombre AS producto, e.nombre AS emprendimiento, l.cantidad_inicial AS inicial, " +
                "       l.stock_disponible AS disponible, l.stock_reservado AS reservado, l.fecha_ingreso AS ingreso, " +
                "       l.fecha_vencimiento AS vence, l.estado " +
                "FROM lote l JOIN produccion pr ON pr.id = l.produccion_id JOIN producto p ON p.id = pr.producto_id " +
                "JOIN emprendimiento e ON e.id = pr.emprendimiento_id " +
                "WHERE (?::int IS NULL OR pr.producto_id = ?) ";
        if (ctx.esProductor()) {
            return Respuesta.ok(Db.tabla(sql + "AND e.usuario_id = ? ORDER BY l.id DESC", p.entero(0), p.entero(0), ctx.usuarioId));
        }
        return Respuesta.ok(Db.tabla(sql + "ORDER BY l.id DESC LIMIT 200", p.entero(0), p.entero(0)));
    }

    private static Respuesta trazarLote(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.con(con -> {
            Fila l = Db.fila(con, "SELECT l.id, e.usuario_id FROM lote l JOIN produccion pr ON pr.id = l.produccion_id " +
                    "JOIN emprendimiento e ON e.id = pr.emprendimiento_id WHERE l.codigo = ?", p.str(0));
            if (l == null) throw new ErrorNegocio("No existe el lote " + p.str(0));
            if (ctx.esProductor() && !Integer.valueOf(ctx.usuarioId).equals(l.entero("usuario_id")))
                throw new ErrorNegocio("Ese lote no pertenece a su emprendimiento.");
            int id = l.entero("id");
            return "TRAZABILIDAD DEL LOTE " + p.str(0) + "\n\n1) Origen\n" + Db.tabla(con,
                    "SELECT c.nombre AS comunidad, c.municipio, e.nombre AS emprendimiento, e.responsable, " +
                    "       p.nombre AS producto, pr.fecha_produccion AS cosecha, pr.procedencia, " +
                    "       u.nombre || ' ' || u.apellido AS validado_por, pr.fecha_validacion::date AS validado " +
                    "FROM lote l JOIN produccion pr ON pr.id = l.produccion_id " +
                    "JOIN emprendimiento e ON e.id = pr.emprendimiento_id JOIN comunidad c ON c.id = e.comunidad_id " +
                    "JOIN producto p ON p.id = pr.producto_id LEFT JOIN usuario u ON u.id = pr.validado_por " +
                    "WHERE l.id = ?", id)
                + "\n\n2) Movimientos de inventario\n" + Db.tabla(con,
                    "SELECT m.fecha::timestamp(0) AS fecha, m.tipo, m.cantidad, m.motivo, " +
                    "       COALESCE(u.nombre, 'sistema') AS registrado_por " +
                    "FROM movimiento_inventario m LEFT JOIN usuario u ON u.id = m.usuario_id " +
                    "WHERE m.lote_id = ? ORDER BY m.fecha", id)
                + "\n\n3) Pedidos que recibieron unidades de este lote\n" + Db.tabla(con,
                    "SELECT pe.numero AS pedido, pe.estado AS estado_pedido, rl.cantidad, rl.estado AS reserva " +
                    "FROM reserva_lote rl JOIN detalle_pedido dp ON dp.id = rl.detalle_pedido_id " +
                    "JOIN pedido pe ON pe.id = dp.pedido_id WHERE rl.lote_id = ? ORDER BY rl.fecha", id);
        }));
    }

    private static Respuesta trazarPedido(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.con(con -> {
            Fila pe = Db.fila(con, "SELECT numero, cliente_id FROM pedido WHERE id = ?", p.entero(0));
            if (pe == null) throw new ErrorNegocio("No existe el pedido " + p.entero(0));
            if (!ctx.esStaff() && pe.entero("cliente_id") != ctx.usuarioId)
                throw new ErrorNegocio("Ese pedido no le pertenece.");
            return "Trazabilidad del pedido " + pe.str("numero") + "\n\n" + Db.tabla(con,
                    "SELECT producto, cantidad, lote, emprendimiento, comunidad, fecha_produccion AS cosecha, estado_reserva " +
                    "FROM v_trazabilidad WHERE pedido = ? ORDER BY producto", pe.str("numero"));
        }));
    }
}
