package feder.negocio.comercial.pedidos;

import feder.infra.Adjuntos;
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
 * - QR: el Administrador sube la imagen del QR de cobro de FEDER (SUBQR con la imagen adjunta). Al pagar por QR
 *   el cliente recibe ese QR con el monto y el numero de pedido; el pago queda PENDIENTE, se avisa al Personal
 *   y este lo verifica manualmente en la cuenta (MODPAG pagado | rechazado).
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
        r.cmd("SUBQR", "CU6", "qr", Accion.CREAR,
                "Subir o reemplazar el QR de cobro de FEDER: envie este comando con la imagen del QR (PNG o JPG) ADJUNTA al correo.",
                "SUBQR   (con la imagen qr.png adjunta)", PagoHandler::subirQr);
        r.cmd("VERQR", "CU6", "qr", Accion.VER, "Ver el QR de cobro de FEDER vigente.",
                "VERQR", PagoHandler::verQr);
    }

    // ------------------------------------------------------------------ QR de cobro

    private static Adjuntos.Archivo qrActivo(Connection con) throws Exception {
        Fila f = Db.fila(con, "SELECT imagen, tipo_mime FROM qr_pago WHERE activo ORDER BY id DESC LIMIT 1");
        if (f == null) return null;
        String mime = f.str("tipo_mime");
        return new Adjuntos.Archivo(mime.equals("image/jpeg") ? "qr_feder.jpg" : "qr_feder.png", mime, (byte[]) f.get("imagen"));
    }

    private static Respuesta subirQr(Params p, Contexto ctx) throws Exception {
        Adjuntos.Archivo img = Adjuntos.primeraImagen();
        if (img == null)
            throw new ErrorNegocio("No se encontro ninguna imagen adjunta.\n"
                    + "Envie SUBQR en el asunto y ADJUNTE la imagen del QR (PNG o JPG, maximo 2 MB).");
        int id = Db.tx(con -> {
            Db.update(con, "UPDATE qr_pago SET activo = false WHERE activo");
            return Db.<Integer>valor(con, "INSERT INTO qr_pago (imagen, tipo_mime, nombre_archivo, subido_por) "
                    + "VALUES (?,?,?,?) RETURNING id", img.datos(), img.mime(), img.nombre(), ctx.usuarioId);
        });
        return Respuesta.ok("QR de cobro actualizado (id " + id + ", " + img.nombre() + ", " + img.kb() + " KB).\n"
                + "Desde ahora los clientes que paguen con INSPAG[\"pedido\",\"qr\"] recibiran este QR.\n"
                + "Revise abajo que la imagen se vea correctamente.", img);
    }

    private static Respuesta verQr(Params p, Contexto ctx) throws Exception {
        Adjuntos.Archivo img = Db.con(PagoHandler::qrActivo);
        if (img == null) throw new ErrorNegocio("Todavia no hay un QR de cobro cargado. El Administrador debe enviarlo con SUBQR.");
        return Respuesta.ok("QR de cobro de FEDER.\n"
                + "Para pagar un pedido registre primero el pago con INSPAG[\"pedido\",\"qr\"]: le indicaremos el monto exacto.", img);
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
        Adjuntos.Archivo[] qr = new Adjuntos.Archivo[1];
        String[] avisoStaff = new String[1];
        String msgFinal = Db.tx(con -> {
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

            if (metodo.equals("qr")) {
                qr[0] = qrActivo(con);
                if (qr[0] == null)
                    throw new ErrorNegocio("El cobro por QR aun no esta disponible (no hay QR cargado). "
                            + "Use INSPAG[\"" + pedidoId + "\",\"efectivo\"] o tarjeta.");
            }

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
                msg += "\n\nCOMO PAGAR"
                        + "\n1. Escanee el QR de abajo desde la app de su banco."
                        + "\n2. Monto exacto: Bs " + monto
                        + "\n3. En la glosa / descripcion escriba: " + pe.str("numero")
                        + "\n\nEstado: PENDIENTE. El Personal de FEDER verificara el deposito y le avisaremos por correo."
                        + "\nPuede ver el estado con LISPAG[\"" + pedidoId + "\"].";
                String cliente = Db.valor(con, "SELECT u.nombre || ' ' || u.apellido || ' (' || u.email || ')' "
                        + "FROM pedido pe JOIN usuario u ON u.id = pe.cliente_id WHERE pe.id = ?", pedidoId);
                avisoStaff[0] = "Hay un pago por QR pendiente de verificacion:\n\n"
                        + "Pago:    " + pagoId + "\nPedido:  " + pe.str("numero") + " (id " + pedidoId + ")"
                        + "\nCliente: " + cliente + "\nMonto:   Bs " + monto
                        + "\nGlosa esperada: " + pe.str("numero")
                        + "\n\nRevise la cuenta de FEDER y responda con:"
                        + "\n  MODPAG[\"" + pagoId + "\",\"pagado\"]      si el deposito llego"
                        + "\n  MODPAG[\"" + pagoId + "\",\"rechazado\"]   si no llego o el monto no coincide";
            } else {
                msg += "\nEstado: PENDIENTE de verificacion por el Personal de FEDER. Le avisaremos por correo al confirmarlo.";
            }
            return msg;
        });
        if (avisoStaff[0] != null) Notificador.avisarStaff("Pago QR por verificar", avisoStaff[0]);   // ya confirmado en BD
        return qr[0] != null ? Respuesta.ok(msgFinal, qr[0]) : Respuesta.ok(msgFinal);
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
