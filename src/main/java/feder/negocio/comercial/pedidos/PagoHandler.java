package feder.negocio.comercial.pedidos;

import feder.infra.Notificador;
import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.math.BigDecimal;
import java.sql.Connection;

import static feder.parser.ParamSpec.*;

/**
 * CU6 - Pagos multicanal: QR, efectivo y tarjeta (RF13). Estados: pendiente, pagado, rechazado, anulado.
 * - Pago registrado por el CLIENTE: queda PENDIENTE hasta que el Personal lo verifique (MODPAG).
 * - Pago registrado por el PERSONAL en efectivo/tarjeta: queda PAGADO (lo recibio en mano).
 * - QR: la integracion con PagoFacil se habilitara mas adelante; por ahora queda PENDIENTE.
 */
public final class PagoHandler {

    private PagoHandler() {}

    public static void registrar(Registro r) {
        r.cmd("INSPAG", "CU6", "pagos", Accion.CREAR,
                "Registrar un pago de un pedido (monto por defecto: el saldo pendiente).",
                "INSPAG[\"12\",\"efectivo\",\"57.00\",\"Recibo 0012\"]   o   INSPAG[\"12\",\"qr\"]", PagoHandler::insertar,
                entero("pedido_id"), opcion("metodo", "qr", "efectivo", "tarjeta"), decimal("monto").opcional(),
                texto("referencia", 100).opcional());
        r.cmd("MODPAG", "CU6", "pagos", Accion.EDITAR,
                "Verificar un pago: pagado | rechazado | anulado. Al completar el total, el pedido se confirma y se notifica.",
                "MODPAG[\"7\",\"pagado\"]", PagoHandler::cambiarEstado,
                entero("id"), opcion("estado", "pagado", "rechazado", "anulado"));
        r.cmd("LISPAG", "CU6", "pagos", Accion.VER, "Listar pagos (filtro por pedido). El Cliente ve solo los suyos.",
                "LISPAG[\"*\"]   o   LISPAG[\"12\"]", PagoHandler::listar, entero("pedido_id").todos());
    }

    private static void notificarPago(Connection con, int pagoId) throws Exception {
        Fila f = Db.fila(con, "SELECT pa.monto, mp.nombre AS metodo, pe.id AS pedido, pe.numero, pe.cliente_id, " +
                "s.saldo_pendiente FROM pago pa JOIN metodo_pago mp ON mp.id = pa.metodo_pago_id " +
                "JOIN pedido pe ON pe.id = pa.pedido_id JOIN v_saldo_pedido s ON s.pedido_id = pe.id WHERE pa.id = ?", pagoId);
        Notificador.notificar(con, f.entero("cliente_id"), f.entero("pedido"), null, "pago_confirmado",
                "Recibimos su pago de Bs " + f.dec("monto") + " (" + f.str("metodo") + ") para el pedido " + f.str("numero")
                + ". Saldo pendiente: Bs " + f.dec("saldo_pendiente") + ".");
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        int pedidoId = p.entero(0);
        String metodo = p.str(1);
        return Respuesta.ok(Db.tx(con -> {
            Fila pe = PedidoServicio.pedidoPara(con, pedidoId, ctx.usuarioId, ctx.esStaff());
            if ("cancelado".equals(pe.str("estado"))) throw new ErrorNegocio("El pedido esta cancelado.");
            Fila mp = Db.fila(con, "SELECT id, habilitado FROM metodo_pago WHERE lower(nombre) = ?", metodo);
            if (mp == null || !mp.bool("habilitado")) throw new ErrorNegocio("El metodo de pago " + metodo + " no esta habilitado.");

            BigDecimal saldo = Db.valor(con, "SELECT s.saldo_pendiente - COALESCE((SELECT sum(monto) FROM pago " +
                    "WHERE pedido_id = ? AND estado = 'pendiente'), 0) FROM v_saldo_pedido s WHERE s.pedido_id = ?",
                    pedidoId, pedidoId);
            if (saldo.signum() <= 0)
                throw new ErrorNegocio("El pedido no tiene saldo por pagar (o ya tiene pagos pendientes de verificacion). Vea LISPAG[\"" + pedidoId + "\"].");
            BigDecimal monto = p.tiene(2) ? p.dec(2) : saldo;
            if (monto.signum() <= 0) throw new ErrorNegocio("El monto debe ser mayor a 0.");
            if (monto.compareTo(saldo) > 0) throw new ErrorNegocio("El monto (Bs " + monto + ") supera el saldo por pagar (Bs " + saldo + ").");

            boolean directo = ctx.esStaff() && !metodo.equals("qr");
            int pagoId = Db.<Integer>valor(con, "INSERT INTO pago (pedido_id, metodo_pago_id, monto, estado, referencia) " +
                    "VALUES (?,?,?,?,?) RETURNING id", pedidoId, mp.entero("id"), monto, directo ? "pagado" : "pendiente", p.str(3));
            String msg = "Pago registrado (id " + pagoId + "): Bs " + monto + " por " + metodo + " para el pedido " + pe.str("numero") + ".";
            if (directo) {
                notificarPago(con, pagoId);
                msg += " Estado: PAGADO.";
                if (PedidoServicio.revisarPagoCompleto(con, pedidoId, ctx.usuarioId))
                    msg += "\nEl pedido quedo pagado por completo y paso a CONFIRMADO (se notifico al cliente).";
            } else if (metodo.equals("qr")) {
                msg += "\nEstado: PENDIENTE. El cobro por QR (PagoFacil) se habilitara proximamente; "
                        + "por ahora el Personal de FEDER verificara el pago.";
            } else {
                msg += "\nEstado: PENDIENTE de verificacion por el Personal de FEDER. Le avisaremos por correo al confirmarlo.";
            }
            return msg;
        }));
    }

    private static Respuesta cambiarEstado(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String nuevo = p.str(1);
        return Respuesta.ok(Db.tx(con -> {
            Fila pa = Db.fila(con, "SELECT estado, pedido_id FROM pago WHERE id = ? FOR UPDATE", id);
            if (pa == null) throw new ErrorNegocio("No existe el pago " + id);
            String actual = pa.str("estado");
            boolean valido = actual.equals("pendiente") || (actual.equals("pagado") && nuevo.equals("anulado"));
            if (!valido) throw new ErrorNegocio("No se puede pasar el pago de " + actual + " a " + nuevo + ".");
            int pedidoId = pa.entero("pedido_id");
            if (nuevo.equals("pagado")) {
                String estPedido = Db.valor(con, "SELECT estado FROM pedido WHERE id = ?", pedidoId);
                if ("cancelado".equals(estPedido)) throw new ErrorNegocio("El pedido de este pago fue cancelado; anule el pago.");
            }
            Db.update(con, "UPDATE pago SET estado = ? WHERE id = ?", nuevo, id);
            String msg = "Pago " + id + " -> " + nuevo.toUpperCase() + ".";
            if (nuevo.equals("pagado")) {
                notificarPago(con, id);
                if (PedidoServicio.revisarPagoCompleto(con, pedidoId, ctx.usuarioId))
                    msg += "\nEl pedido quedo pagado por completo y paso a CONFIRMADO (se notifico al cliente).";
            }
            return msg;
        }));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT pa.id, pe.id AS pedido_id, pe.numero AS pedido, u.nombre || ' ' || u.apellido AS cliente, " +
                "       mp.nombre AS metodo, pa.monto, pa.estado, pa.fecha::timestamp(0) AS fecha, pa.referencia " +
                "FROM pago pa JOIN pedido pe ON pe.id = pa.pedido_id JOIN usuario u ON u.id = pe.cliente_id " +
                "JOIN metodo_pago mp ON mp.id = pa.metodo_pago_id WHERE (?::int IS NULL OR pe.id = ?) ";
        return Respuesta.ok(ctx.esStaff()
                ? Db.tabla(sql + "ORDER BY pa.id DESC LIMIT 200", p.entero(0), p.entero(0))
                : Db.tabla(sql + "AND pe.cliente_id = ? ORDER BY pa.id DESC", p.entero(0), p.entero(0), ctx.usuarioId));
    }
}
