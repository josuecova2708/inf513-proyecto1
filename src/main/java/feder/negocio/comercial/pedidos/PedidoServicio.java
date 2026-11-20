package feder.negocio.comercial.pedidos;

import feder.infra.Config;
import feder.infra.Notificador;
import feder.negocio.Db;
import feder.negocio.ErrorNegocio;
import feder.negocio.Fila;

import java.math.BigDecimal;
import java.sql.Connection;

/**
 * Logica compartida de pedidos.
 *
 * CARRITO (RF09) sin tabla extra: es el pedido del cliente en estado 'pendiente' que todavia
 * NO tiene entrega registrada. Al confirmar la compra (CONCAR) se reserva el stock por lotes
 * (fn_reservar_pedido, FEFO con FOR UPDATE) y se crea la entrega: desde ahi es un pedido real.
 */
public final class PedidoServicio {

    public static final BigDecimal COSTO_ENVIO = new BigDecimal(Config.get("envio.costo", "15.00"));
    public static final int MINUTOS_RESERVA = Config.getInt("reserva.minutos", 1440);

    /** Condicion SQL: el pedido p ya es un pedido real (no un carrito). */
    public static final String ES_PEDIDO_REAL = "EXISTS (SELECT 1 FROM entrega en WHERE en.pedido_id = p.id)";

    private PedidoServicio() {}

    /** Crea un pedido con numero correlativo PED-000123. */
    public static int nuevoPedido(Connection con, int clienteId, String origen, Integer encargoId) throws Exception {
        return Db.<Integer>valor(con,
                "WITH s AS (SELECT nextval(pg_get_serial_sequence('pedido', 'id'))::int AS id) " +
                "INSERT INTO pedido (id, numero, cliente_id, origen, encargo_id) " +
                "SELECT id, 'PED-' || lpad(id::text, 6, '0'), ?, ?, ? FROM s RETURNING id",
                clienteId, origen, encargoId);
    }

    /** Id del carrito del cliente (bloqueado FOR UPDATE), o null si no tiene. */
    public static Integer carritoId(Connection con, int clienteId) throws Exception {
        return Db.valor(con, "SELECT p.id FROM pedido p WHERE p.cliente_id = ? AND p.estado = 'pendiente' " +
                "AND p.origen = 'inmediato' AND NOT " + ES_PEDIDO_REAL + " ORDER BY p.id LIMIT 1 FOR UPDATE", clienteId);
    }

    public static void recalcular(Connection con, int pedidoId) throws Exception {
        Db.update(con, "UPDATE pedido SET subtotal = COALESCE((SELECT sum(dp.subtotal) FROM detalle_pedido dp " +
                "WHERE dp.pedido_id = ?), 0) WHERE id = ?", pedidoId, pedidoId);
    }

    /**
     * Reserva el stock de todas las lineas y registra la entrega. Primero verifica la disponibilidad
     * para dar un mensaje claro; la funcion fn_reservar_pedido (con FOR UPDATE) es la defensa final
     * ante compras simultaneas de la misma existencia (RF10, RF16).
     */
    public static void reservarYEntregar(Connection con, int pedidoId, String tipoEntrega, String direccion,
                                         String referencia) throws Exception {
        StringBuilder faltantes = new StringBuilder();
        for (Fila f : Db.filas(con,
                "SELECT pr.nombre, dp.cantidad, d.stock_disponible, pr.unidad_medida, pr.estado " +
                "FROM detalle_pedido dp JOIN producto pr ON pr.id = dp.producto_id " +
                "JOIN v_disponibilidad_producto d ON d.producto_id = dp.producto_id WHERE dp.pedido_id = ?", pedidoId)) {
            if (!"activo".equals(f.str("estado"))) {
                faltantes.append("\n  - ").append(f.str("nombre")).append(": ya no esta a la venta");
            } else if (f.dec("cantidad").compareTo(f.dec("stock_disponible")) > 0) {
                faltantes.append("\n  - ").append(f.str("nombre")).append(": pidio ").append(f.dec("cantidad"))
                        .append(", hay ").append(f.dec("stock_disponible")).append(' ').append(f.str("unidad_medida"));
            }
        }
        if (faltantes.length() > 0) {
            throw new ErrorNegocio("No hay stock suficiente para confirmar:" + faltantes
                    + "\nAjuste las cantidades (MODCAR) o haga un encargo (INSENC).");
        }
        Db.valor(con, "SELECT fn_reservar_pedido(?, ?)::text", pedidoId, MINUTOS_RESERVA);

        if ("envio".equals(tipoEntrega)) {
            if (direccion == null) {
                direccion = Db.valor(con, "SELECT u.direccion FROM pedido p JOIN usuario u ON u.id = p.cliente_id " +
                        "WHERE p.id = ?", pedidoId);
                if (direccion == null) throw new ErrorNegocio("Para envio indique la direccion de entrega.");
            }
            Db.update(con, "UPDATE pedido SET costo_envio = ? WHERE id = ?", COSTO_ENVIO, pedidoId);
        }
        Db.update(con, "INSERT INTO entrega (pedido_id, tipo, direccion, referencia) VALUES (?,?,?,?)",
                pedidoId, tipoEntrega, direccion, referencia);
    }

    /** Detalle de un pedido como texto (para respuestas y notificaciones). */
    public static String resumen(Connection con, int pedidoId) throws Exception {
        Fila p = Db.fila(con, "SELECT p.numero, p.estado, p.subtotal, p.costo_envio, p.total, s.cobrado, " +
                "s.saldo_pendiente FROM pedido p JOIN v_saldo_pedido s ON s.pedido_id = p.id WHERE p.id = ?", pedidoId);
        return "Pedido " + p.str("numero") + "  |  estado: " + p.str("estado").toUpperCase() + "\n"
                + Db.tabla(con, "SELECT pr.nombre AS producto, dp.cantidad, pr.unidad_medida AS unidad, " +
                "dp.precio_unitario AS precio, dp.subtotal FROM detalle_pedido dp JOIN producto pr ON pr.id = dp.producto_id " +
                "WHERE dp.pedido_id = ? ORDER BY pr.nombre", pedidoId)
                + "\nSubtotal: Bs " + p.str("subtotal") + "   Envio: Bs " + p.str("costo_envio")
                + "   TOTAL: Bs " + p.str("total")
                + "\nCobrado:  Bs " + p.str("cobrado") + "   Saldo pendiente: Bs " + p.str("saldo_pendiente");
    }

    /**
     * Tras registrar un pago 'pagado': si el saldo llego a 0 y el pedido estaba pendiente,
     * pasa a CONFIRMADO y se notifica al cliente (RF15).
     */
    public static boolean revisarPagoCompleto(Connection con, int pedidoId, Integer gestor) throws Exception {
        Fila f = Db.fila(con, "SELECT p.estado, p.cliente_id, p.numero, s.saldo_pendiente FROM pedido p " +
                "JOIN v_saldo_pedido s ON s.pedido_id = p.id WHERE p.id = ?", pedidoId);
        if (f.dec("saldo_pendiente").signum() > 0 || !"pendiente".equals(f.str("estado"))) return false;
        Db.update(con, "UPDATE pedido SET estado = 'confirmado', gestionado_por = COALESCE(gestionado_por, ?) " +
                "WHERE id = ?", gestor, pedidoId);
        Notificador.notificar(con, f.entero("cliente_id"), pedidoId, null, "pedido_confirmado",
                "Su pedido " + f.str("numero") + " fue CONFIRMADO: el pago esta completo y lo prepararemos pronto.\n\n"
                + resumen(con, pedidoId));
        return true;
    }

    /** Verifica existencia y propiedad; devuelve la fila del pedido bloqueada. */
    public static Fila pedidoPara(Connection con, int pedidoId, int usuarioId, boolean staff) throws Exception {
        Fila p = Db.fila(con, "SELECT p.*, " + ES_PEDIDO_REAL + " AS es_real FROM pedido p WHERE p.id = ? FOR UPDATE",
                pedidoId);
        if (p == null || !p.bool("es_real")) throw new ErrorNegocio("No existe el pedido " + pedidoId + ".");
        if (!staff && p.entero("cliente_id") != usuarioId) throw new ErrorNegocio("El pedido " + pedidoId + " no le pertenece.");
        return p;
    }
}
