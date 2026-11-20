package feder.negocio.administracion.finanzas;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static feder.parser.ParamSpec.*;

/** CU7 - Indicadores financieros. */
public final class IndicadorHandler {

    /** Estados en los que un pedido cuenta como venta efectiva. */
    static final String VENDIDO = "('confirmado','en_preparacion','listo_entrega','entregado')";

    private IndicadorHandler() {}

    public static void registrar(Registro r) {
        r.cmd("INDFIN", "CU7", "finanzas", Accion.VER,
                "Indicadores del periodo: ventas, ticket promedio, cobrado, por cobrar, costo, margen bruto e inventario valorizado.",
                "INDFIN[\"2026-11-01\",\"2026-11-30\"]", IndicadorHandler::indicadores,
                fecha("fecha_inicio"), fecha("fecha_fin"));
        r.cmd("LISSAL", "CU7", "finanzas", Accion.VER, "Cuentas por cobrar: pedidos activos con saldo pendiente.",
                "LISSAL", IndicadorHandler::saldos);
    }

    private static Respuesta indicadores(Params p, Contexto ctx) throws Exception {
        LocalDate fi = p.fecha(0), ff = p.fecha(1);
        if (ff.isBefore(fi)) throw new ErrorNegocio("La fecha fin no puede ser anterior a la fecha inicio.");
        return Respuesta.ok(Db.con(con -> {
            Fila v = Db.fila(con, "SELECT count(*) AS pedidos, COALESCE(sum(total),0) AS ventas, " +
                    "COALESCE(round(avg(total),2),0) AS ticket FROM pedido p " +
                    "WHERE estado IN " + VENDIDO + " AND fecha >= ? AND fecha < ?::date + 1", fi, ff);
            int cancelados = Db.entero(con, "SELECT count(*) FROM pedido p WHERE estado = 'cancelado' AND "
                    + "EXISTS (SELECT 1 FROM entrega en WHERE en.pedido_id = p.id) AND fecha >= ? AND fecha < ?::date + 1", fi, ff);
            BigDecimal cobrado = Db.valor(con, "SELECT COALESCE(sum(monto),0) FROM pago WHERE estado = 'pagado' " +
                    "AND fecha >= ? AND fecha < ?::date + 1", fi, ff);
            BigDecimal porCobrar = Db.valor(con, "SELECT COALESCE(sum(s.saldo_pendiente),0) FROM v_saldo_pedido s " +
                    "JOIN pedido p ON p.id = s.pedido_id WHERE p.estado NOT IN ('cancelado') AND s.saldo_pendiente > 0 " +
                    "AND EXISTS (SELECT 1 FROM entrega en WHERE en.pedido_id = p.id)");
            Fila m = Db.fila(con, "SELECT COALESCE(sum(rl.cantidad * dp.precio_unitario),0) AS ingreso, " +
                    "COALESCE(sum(rl.cantidad * COALESCE(l.costo_unitario,0)),0) AS costo " +
                    "FROM reserva_lote rl JOIN detalle_pedido dp ON dp.id = rl.detalle_pedido_id " +
                    "JOIN pedido p ON p.id = dp.pedido_id JOIN lote l ON l.id = rl.lote_id " +
                    "WHERE rl.estado = 'confirmada' AND p.fecha >= ? AND p.fecha < ?::date + 1", fi, ff);
            Fila inv = Db.fila(con, "SELECT COALESCE(sum((l.stock_disponible + l.stock_reservado) * COALESCE(l.costo_unitario,0)),0) AS costo, " +
                    "COALESCE(sum((l.stock_disponible + l.stock_reservado) * pr.precio_venta),0) AS venta " +
                    "FROM lote l JOIN produccion p ON p.id = l.produccion_id JOIN producto pr ON pr.id = p.producto_id " +
                    "WHERE l.estado <> 'vencido'");

            BigDecimal ingreso = m.dec("ingreso").setScale(2, RoundingMode.HALF_UP), costo = m.dec("costo").setScale(2, RoundingMode.HALF_UP), margen = ingreso.subtract(costo);
            String pct = ingreso.signum() == 0 ? "-" :
                    margen.multiply(BigDecimal.valueOf(100)).divide(ingreso, 1, RoundingMode.HALF_UP) + " %";

            return "INDICADORES FINANCIEROS  del " + fi + " al " + ff + "\n\n"
                    + String.format("Pedidos vendidos ............ %s%n", v.str("pedidos"))
                    + String.format("Pedidos cancelados .......... %d%n", cancelados)
                    + String.format("Ventas totales .............. Bs %s%n", v.str("ventas"))
                    + String.format("Ticket promedio ............. Bs %s%n", v.str("ticket"))
                    + String.format("Cobrado en el periodo ....... Bs %s%n", cobrado)
                    + String.format("Cuentas por cobrar (hoy) .... Bs %s%n", porCobrar)
                    + "\nRentabilidad de lo despachado (lotes con costo registrado):\n"
                    + String.format("  Ingreso por productos ..... Bs %s%n", ingreso)
                    + String.format("  Costo de lo vendido ....... Bs %s%n", costo)
                    + String.format("  Margen bruto .............. Bs %s  (%s)%n", margen, pct)
                    + "\nInventario valorizado (hoy):\n"
                    + String.format("  A costo ................... Bs %s%n", inv.dec("costo").setScale(2, RoundingMode.HALF_UP))
                    + String.format("  A precio de venta ......... Bs %s%n", inv.dec("venta").setScale(2, RoundingMode.HALF_UP))
                    + "\nCobros por metodo de pago en el periodo:\n"
                    + Db.tabla(con, "SELECT mp.nombre AS metodo, count(pa.id) AS pagos, COALESCE(sum(pa.monto),0) AS monto " +
                    "FROM metodo_pago mp LEFT JOIN pago pa ON pa.metodo_pago_id = mp.id AND pa.estado = 'pagado' " +
                    "AND pa.fecha >= ? AND pa.fecha < ?::date + 1 GROUP BY mp.id ORDER BY mp.id", fi, ff);
        }));
    }

    private static Respuesta saldos(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok("CUENTAS POR COBRAR\n" + Db.tabla(
                "SELECT s.pedido_id, s.numero, u.nombre || ' ' || u.apellido AS cliente, p.estado, p.fecha::date AS fecha, " +
                "       s.total, s.cobrado, s.saldo_pendiente AS saldo " +
                "FROM v_saldo_pedido s JOIN pedido p ON p.id = s.pedido_id JOIN usuario u ON u.id = p.cliente_id " +
                "WHERE p.estado <> 'cancelado' AND s.saldo_pendiente > 0 " +
                "AND EXISTS (SELECT 1 FROM entrega en WHERE en.pedido_id = p.id) ORDER BY p.fecha"));
    }
}
