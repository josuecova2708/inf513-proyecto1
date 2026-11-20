package feder.negocio.comercial.pedidos;

import feder.infra.Notificador;
import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.math.BigDecimal;

import static feder.parser.ParamSpec.*;

/**
 * CU6 - Procesar pedidos y sus estados (RF12):
 *   pendiente -> confirmado -> en_preparacion -> listo_entrega -> entregado   (| cancelado)
 */
public final class PedidoHandler {

    static final String[] ESTADOS = { "pendiente", "confirmado", "en_preparacion", "listo_entrega", "entregado", "cancelado" };

    private PedidoHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISPED", "CU6", "pedidos", Accion.VER, "Listar pedidos por estado. El Cliente ve solo los suyos.",
                "LISPED[\"*\"]   o   LISPED[\"confirmado\"]", PedidoHandler::listar, opcion("estado", ESTADOS).todos());
        r.cmd("VERPED", "CU6", "pedidos", Accion.VER, "Detalle de un pedido: productos, pagos y entrega.",
                "VERPED[\"12\"]", PedidoHandler::ver, entero("id"));
        r.cmd("MODPED", "CU6", "pedidos", Accion.EDITAR,
                "Avanzar el estado del pedido (confirmado, en_preparacion, listo_entrega, entregado). Notifica al cliente.",
                "MODPED[\"12\",\"en_preparacion\"]", PedidoHandler::cambiarEstado,
                entero("id"), opcion("estado", "confirmado", "en_preparacion", "listo_entrega", "entregado"));
        r.cmd("CANPED", "CU6", "pedidos", Accion.ELIMINAR,
                "Cancelar un pedido y liberar su stock reservado (Cliente: solo pendientes propios).",
                "CANPED[\"12\"]", PedidoHandler::cancelar, entero("id"));
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT p.id, p.numero, u.nombre || ' ' || u.apellido AS cliente, p.origen, " +
                "       p.fecha::timestamp(0) AS fecha, p.estado, p.total, s.cobrado, s.saldo_pendiente AS saldo, " +
                "       en.tipo AS entrega, en.estado AS estado_entrega " +
                "FROM pedido p JOIN usuario u ON u.id = p.cliente_id JOIN v_saldo_pedido s ON s.pedido_id = p.id " +
                "JOIN entrega en ON en.pedido_id = p.id WHERE (?::text IS NULL OR p.estado = ?) ";
        return Respuesta.ok(ctx.esStaff()
                ? Db.tabla(sql + "ORDER BY p.id DESC LIMIT 200", p.str(0), p.str(0))
                : Db.tabla(sql + "AND p.cliente_id = ? ORDER BY p.id DESC", p.str(0), p.str(0), ctx.usuarioId));
    }

    private static Respuesta ver(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        return Respuesta.ok(Db.con(con -> {
            Fila pe = PedidoServicio.pedidoPara(con, id, ctx.usuarioId, ctx.esStaff());
            Fila cli = Db.fila(con, "SELECT nombre || ' ' || apellido AS n, email FROM usuario WHERE id = ?", pe.entero("cliente_id"));
            return "Cliente: " + cli.str("n") + " <" + cli.str("email") + ">   Origen: " + pe.str("origen")
                    + "   Fecha: " + pe.str("fecha").substring(0, 16)
                    + (pe.str("nit_ci") != null ? "\nFactura: NIT/CI " + pe.str("nit_ci") + " - " + pe.str("razon_social") : "")
                    + "\n\n" + PedidoServicio.resumen(con, id)
                    + "\n\nPagos:\n" + Db.tabla(con, "SELECT pa.id, mp.nombre AS metodo, pa.monto, pa.estado, " +
                    "pa.fecha::timestamp(0) AS fecha, pa.referencia FROM pago pa JOIN metodo_pago mp ON mp.id = pa.metodo_pago_id " +
                    "WHERE pa.pedido_id = ? ORDER BY pa.id", id)
                    + "\n\nEntrega:\n" + Db.tabla(con, "SELECT tipo, direccion, referencia, fecha_programada::timestamp(0) AS programada, " +
                    "fecha_entrega::timestamp(0) AS entregada, receptor, estado FROM entrega WHERE pedido_id = ?", id);
        }));
    }

    private static Respuesta cambiarEstado(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String nuevo = p.str(1);
        String extra = Db.tx(con -> {
            Fila pe = PedidoServicio.pedidoPara(con, id, ctx.usuarioId, true);
            String actual = pe.str("estado");
            String esperado = switch (nuevo) {
                case "confirmado"     -> "pendiente";
                case "en_preparacion" -> "confirmado";
                case "listo_entrega"  -> "en_preparacion";
                case "entregado"      -> "listo_entrega";
                default               -> "";
            };
            if (!actual.equals(esperado))
                throw new ErrorNegocio("El pedido " + pe.str("numero") + " esta " + actual.toUpperCase()
                        + "; solo puede pasar a " + nuevo.toUpperCase() + " desde " + esperado.toUpperCase() + ".");
            int cliente = pe.entero("cliente_id");
            String numero = pe.str("numero");
            Db.update(con, "UPDATE pedido SET gestionado_por = ? WHERE id = ?", ctx.usuarioId, id);
            switch (nuevo) {
                case "confirmado" -> {
                    Db.update(con, "UPDATE pedido SET estado = 'confirmado' WHERE id = ?", id);
                    Notificador.notificar(con, cliente, id, null, "pedido_confirmado",
                            "Su pedido " + numero + " fue CONFIRMADO por FEDER.\n\n" + PedidoServicio.resumen(con, id));
                    BigDecimal saldo = Db.valor(con, "SELECT saldo_pendiente FROM v_saldo_pedido WHERE pedido_id = ?", id);
                    return saldo.signum() > 0 ? "Atencion: el pedido aun tiene saldo pendiente de Bs " + saldo + " (pago contra entrega)." : "";
                }
                case "en_preparacion" -> {
                    // Convierte las reservas en salidas de inventario y revisa el stock minimo
                    Db.valor(con, "SELECT fn_confirmar_reservas(?, ?)::text", id, ctx.usuarioId);
                    return "Las reservas se confirmaron y se registraron las SALIDAS de inventario por lote.";
                }
                case "listo_entrega" -> {
                    Db.update(con, "UPDATE pedido SET estado = 'listo_entrega' WHERE id = ?", id);
                    Fila en = Db.fila(con, "SELECT tipo, direccion FROM entrega WHERE pedido_id = ?", id);
                    String como = "recojo".equals(en.str("tipo"))
                            ? "Puede pasar a recogerlo por las oficinas de FEDER."
                            : "Sera enviado a: " + en.str("direccion") + ".";
                    Notificador.notificar(con, cliente, id, null, "pedido_listo",
                            "Su pedido " + numero + " esta LISTO PARA ENTREGA. " + como);
                    return "Se notifico al cliente que su pedido esta listo.";
                }
                default -> { // entregado
                    Db.update(con, "UPDATE pedido SET estado = 'entregado' WHERE id = ?", id);
                    Db.update(con, "UPDATE entrega SET estado = 'entregada', fecha_entrega = COALESCE(fecha_entrega, now()), " +
                            "despachado_por = COALESCE(despachado_por, ?) WHERE pedido_id = ?", ctx.usuarioId, id);
                    return "";
                }
            }
        });
        return Respuesta.ok("Pedido " + id + " -> " + nuevo.toUpperCase() + ". " + extra);
    }

    private static Respuesta cancelar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String nota = Db.tx(con -> {
            Fila pe = PedidoServicio.pedidoPara(con, id, ctx.usuarioId, ctx.esStaff());
            String est = pe.str("estado");
            boolean permitido = ctx.esStaff() ? (est.equals("pendiente") || est.equals("confirmado")) : est.equals("pendiente");
            if (!permitido)
                throw new ErrorNegocio("El pedido esta " + est.toUpperCase() + " y no puede cancelarse"
                        + (ctx.esStaff() ? "." : " por el cliente; comuniquese con FEDER."));
            Db.valor(con, "SELECT fn_liberar_reservas(?)::text", id);
            Db.update(con, "UPDATE pedido SET estado = 'cancelado' WHERE id = ?", id);
            Db.update(con, "UPDATE pago SET estado = 'anulado' WHERE pedido_id = ? AND estado = 'pendiente'", id);
            BigDecimal pagado = Db.valor(con, "SELECT cobrado FROM v_saldo_pedido WHERE pedido_id = ?", id);
            return pagado.signum() > 0
                    ? "\nATENCION: el pedido tenia pagos confirmados por Bs " + pagado + " que deben devolverse (anule con MODPAG)."
                    : "";
        });
        return Respuesta.ok("Pedido " + id + " CANCELADO. El stock reservado volvio a estar disponible." + nota);
    }
}
