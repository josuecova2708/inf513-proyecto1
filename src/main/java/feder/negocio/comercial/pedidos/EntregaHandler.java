package feder.negocio.comercial.pedidos;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.time.LocalDateTime;

import static feder.parser.ParamSpec.*;

/** CU6 - Entregas por recojo o envio (RF14). Estados: pendiente, en_camino, entregada, fallida. */
public final class EntregaHandler {

    private EntregaHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISENT", "CU6", "entregas", Accion.VER, "Listar entregas por estado. El Cliente ve solo las suyas.",
                "LISENT[\"*\"]   o   LISENT[\"pendiente\"]", EntregaHandler::listar,
                opcion("estado", "pendiente", "en_camino", "entregada", "fallida").todos());
        r.cmd("PROENT", "CU6", "entregas", Accion.EDITAR, "Programar fecha y hora de entrega de un pedido.",
                "PROENT[\"12\",\"2026-11-25 15:30\"]", EntregaHandler::programar,
                entero("pedido_id"), fechaHora("fecha_programada"));
        r.cmd("MODENT", "CU6", "entregas", Accion.EDITAR,
                "Actualizar la entrega: en_camino | entregada (cierra el pedido) | fallida.",
                "MODENT[\"12\",\"entregada\",\"Ana Rojas\"]", EntregaHandler::cambiarEstado,
                entero("pedido_id"), opcion("estado", "en_camino", "entregada", "fallida"), texto("receptor", 120).opcional());
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT pe.id AS pedido_id, pe.numero, u.nombre || ' ' || u.apellido AS cliente, en.tipo, en.direccion, " +
                "       en.fecha_programada::timestamp(0) AS programada, en.estado, pe.estado AS estado_pedido " +
                "FROM entrega en JOIN pedido pe ON pe.id = en.pedido_id JOIN usuario u ON u.id = pe.cliente_id " +
                "WHERE pe.estado <> 'cancelado' AND (?::text IS NULL OR en.estado = ?) ";
        return Respuesta.ok(ctx.esStaff()
                ? Db.tabla(sql + "ORDER BY en.fecha_programada NULLS LAST, pe.id LIMIT 200", p.str(0), p.str(0))
                : Db.tabla(sql + "AND pe.cliente_id = ? ORDER BY pe.id DESC", p.str(0), p.str(0), ctx.usuarioId));
    }

    private static Respuesta programar(Params p, Contexto ctx) throws Exception {
        LocalDateTime cuando = p.fechaHora(1);
        if (cuando.isBefore(LocalDateTime.now())) throw new ErrorNegocio("La fecha programada no puede ser pasada.");
        Db.tx(con -> {
            Fila pe = PedidoServicio.pedidoPara(con, p.entero(0), ctx.usuarioId, true);
            if (pe.str("estado").equals("cancelado") || pe.str("estado").equals("entregado"))
                throw new ErrorNegocio("El pedido esta " + pe.str("estado") + ".");
            Db.update(con, "UPDATE entrega SET fecha_programada = ?, despachado_por = ? WHERE pedido_id = ?",
                    cuando, ctx.usuarioId, p.entero(0));
            return null;
        });
        return Respuesta.ok("Entrega del pedido " + p.entero(0) + " programada para " + cuando.toString().replace('T', ' ') + ".");
    }

    private static Respuesta cambiarEstado(Params p, Contexto ctx) throws Exception {
        int pedidoId = p.entero(0);
        String nuevo = p.str(1);
        String nota = Db.tx(con -> {
            Fila pe = PedidoServicio.pedidoPara(con, pedidoId, ctx.usuarioId, true);
            Fila en = Db.fila(con, "SELECT tipo, estado FROM entrega WHERE pedido_id = ? FOR UPDATE", pedidoId);
            if (!"listo_entrega".equals(pe.str("estado")))
                throw new ErrorNegocio("El pedido esta " + pe.str("estado").toUpperCase()
                        + "; primero debe estar LISTO_ENTREGA (MODPED).");
            switch (nuevo) {
                case "en_camino" -> {
                    if (!"envio".equals(en.str("tipo"))) throw new ErrorNegocio("Solo las entregas por envio pasan a en_camino.");
                    Db.update(con, "UPDATE entrega SET estado = 'en_camino', despachado_por = ? WHERE pedido_id = ?",
                            ctx.usuarioId, pedidoId);
                    return "";
                }
                case "entregada" -> {
                    Db.update(con, "UPDATE entrega SET estado = 'entregada', fecha_entrega = now(), receptor = ?, " +
                            "despachado_por = ? WHERE pedido_id = ?", p.str(2), ctx.usuarioId, pedidoId);
                    Db.update(con, "UPDATE pedido SET estado = 'entregado' WHERE id = ?", pedidoId);
                    return " El pedido paso a ENTREGADO.";
                }
                default -> {
                    Db.update(con, "UPDATE entrega SET estado = 'fallida' WHERE pedido_id = ?", pedidoId);
                    return " Reprograme con PROENT.";
                }
            }
        });
        return Respuesta.ok("Entrega del pedido " + pedidoId + " -> " + nuevo.toUpperCase() + "." + nota);
    }
}
