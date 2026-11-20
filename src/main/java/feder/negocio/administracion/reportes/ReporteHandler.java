package feder.negocio.reportes;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.time.LocalDate;

import static feder.parser.ParamSpec.*;

/**
 * CU8 - Reportes y estadisticas por rango de fechas (RF17).
 * (El reporte de produccion por agricultor/comunidad es REPPRD, en ProduccionHandler.)
 */
public final class ReporteHandler {

    private static final String VENDIDO = "('confirmado','en_preparacion','listo_entrega','entregado')";

    private ReporteHandler() {}

    public static void registrar(Registro r) {
        r.cmd("REPVEN", "CU8", "reportes", Accion.VER, "Ventas por producto en un rango de fechas.",
                "REPVEN[\"2026-11-01\",\"2026-11-30\"]", ReporteHandler::ventas, fecha("fecha_inicio"), fecha("fecha_fin"));
        r.cmd("REPINV", "CU8", "reportes", Accion.VER,
                "Inventario actual por producto: stock, reservado, minimo, lotes por vencer y valor.",
                "REPINV", ReporteHandler::inventario);
        r.cmd("REPPED", "CU8", "reportes", Accion.VER, "Pedidos por estado y por origen (inmediato/encargo).",
                "REPPED[\"2026-11-01\",\"2026-11-30\"]", ReporteHandler::pedidos, fecha("fecha_inicio"), fecha("fecha_fin"));
        r.cmd("REPENC", "CU8", "reportes", Accion.VER, "Encargos por estado y productos mas encargados.",
                "REPENC[\"2026-11-01\",\"2026-11-30\"]", ReporteHandler::encargos, fecha("fecha_inicio"), fecha("fecha_fin"));
        r.cmd("REPMET", "CU8", "reportes", Accion.VER, "Pagos por metodo (QR, efectivo, tarjeta) y estado.",
                "REPMET[\"2026-11-01\",\"2026-11-30\"]", ReporteHandler::metodos, fecha("fecha_inicio"), fecha("fecha_fin"));
        r.cmd("REPING", "CU8", "reportes", Accion.VER, "Ingresos cobrados por dia en un rango de fechas.",
                "REPING[\"2026-11-01\",\"2026-11-30\"]", ReporteHandler::ingresos, fecha("fecha_inicio"), fecha("fecha_fin"));
    }

    private static void rango(LocalDate fi, LocalDate ff) {
        if (ff.isBefore(fi)) throw new ErrorNegocio("La fecha fin no puede ser anterior a la fecha inicio.");
    }

    private static String titulo(String t, Params p) {
        return t + "  del " + p.fecha(0) + " al " + p.fecha(1) + "\n\n";
    }

    private static Respuesta ventas(Params p, Contexto ctx) throws Exception {
        rango(p.fecha(0), p.fecha(1));
        return Respuesta.ok(titulo("REPORTE DE VENTAS POR PRODUCTO", p) + Db.tabla(
                "SELECT c.nombre AS categoria, pr.nombre AS producto, sum(dp.cantidad) AS cantidad, pr.unidad_medida AS unidad, " +
                "       count(DISTINCT pe.id) AS pedidos, sum(dp.subtotal) AS monto_bs " +
                "FROM detalle_pedido dp JOIN pedido pe ON pe.id = dp.pedido_id JOIN producto pr ON pr.id = dp.producto_id " +
                "JOIN categoria c ON c.id = pr.categoria_id " +
                "WHERE pe.estado IN " + VENDIDO + " AND pe.fecha >= ? AND pe.fecha < ?::date + 1 " +
                "GROUP BY c.nombre, pr.id ORDER BY monto_bs DESC", p.fecha(0), p.fecha(1))
                + "\n\nTotal vendido: Bs " + Db.con(con -> Db.valor(con,
                "SELECT COALESCE(sum(total),0) FROM pedido WHERE estado IN " + VENDIDO +
                " AND fecha >= ? AND fecha < ?::date + 1", p.fecha(0), p.fecha(1))));
    }

    private static Respuesta inventario(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.con(con -> "REPORTE DE INVENTARIO (" + LocalDate.now() + ")\n\n" + Db.tabla(con,
                "SELECT d.producto_id AS id, d.nombre AS producto, pr.unidad_medida AS unidad, d.stock_disponible AS disponible, " +
                "       d.stock_reservado AS reservado, d.stock_minimo AS minimo, " +
                "       CASE WHEN d.stock_disponible < d.stock_minimo THEN 'BAJO' ELSE 'OK' END AS nivel, " +
                "       (SELECT count(*) FROM lote l JOIN produccion x ON x.id = l.produccion_id WHERE x.producto_id = d.producto_id " +
                "          AND l.estado = 'disponible' AND l.fecha_vencimiento <= CURRENT_DATE + 15) AS lotes_por_vencer, " +
                "       round(d.stock_disponible * pr.precio_venta, 2) AS valor_venta_bs " +
                "FROM v_disponibilidad_producto d JOIN producto pr ON pr.id = d.producto_id " +
                "WHERE pr.estado = 'activo' ORDER BY nivel, d.nombre")
                + "\n\nLotes que vencen en los proximos 15 dias:\n" + Db.tabla(con,
                "SELECT l.codigo, pr.nombre AS producto, l.stock_disponible AS disponible, l.fecha_vencimiento AS vence " +
                "FROM lote l JOIN produccion x ON x.id = l.produccion_id JOIN producto pr ON pr.id = x.producto_id " +
                "WHERE l.estado = 'disponible' AND l.fecha_vencimiento <= CURRENT_DATE + 15 ORDER BY l.fecha_vencimiento")));
    }

    private static Respuesta pedidos(Params p, Contexto ctx) throws Exception {
        rango(p.fecha(0), p.fecha(1));
        return Respuesta.ok(titulo("REPORTE DE PEDIDOS", p) + Db.tabla(
                "SELECT p.estado, count(*) FILTER (WHERE p.origen = 'inmediato') AS inmediatos, " +
                "       count(*) FILTER (WHERE p.origen = 'encargo') AS de_encargo, count(*) AS total, " +
                "       sum(p.total) AS monto_bs " +
                "FROM pedido p WHERE EXISTS (SELECT 1 FROM entrega en WHERE en.pedido_id = p.id) " +
                "AND p.fecha >= ? AND p.fecha < ?::date + 1 GROUP BY p.estado ORDER BY total DESC", p.fecha(0), p.fecha(1))
                + "\n\nPor tipo de entrega:\n" + Db.tabla(
                "SELECT en.tipo, en.estado, count(*) AS pedidos FROM entrega en JOIN pedido p ON p.id = en.pedido_id " +
                "WHERE p.fecha >= ? AND p.fecha < ?::date + 1 GROUP BY en.tipo, en.estado ORDER BY 1, 2", p.fecha(0), p.fecha(1)));
    }

    private static Respuesta encargos(Params p, Contexto ctx) throws Exception {
        rango(p.fecha(0), p.fecha(1));
        return Respuesta.ok(titulo("REPORTE DE ENCARGOS", p) + Db.tabla(
                "SELECT estado, count(*) AS encargos FROM encargo WHERE fecha_solicitud >= ? AND fecha_solicitud < ?::date + 1 " +
                "GROUP BY estado ORDER BY encargos DESC", p.fecha(0), p.fecha(1))
                + "\n\nProductos mas encargados:\n" + Db.tabla(
                "SELECT pr.nombre AS producto, sum(de.cantidad) AS cantidad, pr.unidad_medida AS unidad, count(*) AS encargos " +
                "FROM detalle_encargo de JOIN encargo e ON e.id = de.encargo_id JOIN producto pr ON pr.id = de.producto_id " +
                "WHERE e.fecha_solicitud >= ? AND e.fecha_solicitud < ?::date + 1 GROUP BY pr.id ORDER BY cantidad DESC LIMIT 15",
                p.fecha(0), p.fecha(1)));
    }

    private static Respuesta metodos(Params p, Contexto ctx) throws Exception {
        rango(p.fecha(0), p.fecha(1));
        return Respuesta.ok(titulo("REPORTE DE METODOS DE PAGO", p) + Db.tabla(
                "SELECT mp.nombre AS metodo, " +
                "       count(pa.id) FILTER (WHERE pa.estado = 'pagado') AS pagados, " +
                "       COALESCE(sum(pa.monto) FILTER (WHERE pa.estado = 'pagado'), 0) AS monto_pagado, " +
                "       count(pa.id) FILTER (WHERE pa.estado = 'pendiente') AS pendientes, " +
                "       count(pa.id) FILTER (WHERE pa.estado IN ('rechazado','anulado')) AS rechazados_anulados " +
                "FROM metodo_pago mp LEFT JOIN pago pa ON pa.metodo_pago_id = mp.id " +
                "AND pa.fecha >= ? AND pa.fecha < ?::date + 1 GROUP BY mp.id ORDER BY mp.id", p.fecha(0), p.fecha(1)));
    }

    private static Respuesta ingresos(Params p, Contexto ctx) throws Exception {
        rango(p.fecha(0), p.fecha(1));
        return Respuesta.ok(titulo("REPORTE DE INGRESOS", p) + Db.tabla(
                "SELECT pa.fecha::date AS dia, count(*) AS pagos, sum(pa.monto) AS ingreso_bs " +
                "FROM pago pa WHERE pa.estado = 'pagado' AND pa.fecha >= ? AND pa.fecha < ?::date + 1 " +
                "GROUP BY 1 ORDER BY 1", p.fecha(0), p.fecha(1))
                + "\n\nTotal ingresado: Bs " + Db.con(con -> Db.valor(con,
                "SELECT COALESCE(sum(monto),0) FROM pago WHERE estado = 'pagado' AND fecha >= ? AND fecha < ?::date + 1",
                p.fecha(0), p.fecha(1))));
    }
}
