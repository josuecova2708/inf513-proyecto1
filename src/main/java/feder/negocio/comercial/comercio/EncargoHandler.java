package feder.negocio.comercial.comercio;

import feder.infra.Notificador;
import feder.negocio.*;
import feder.negocio.comercial.pedidos.PedidoServicio;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.sql.Connection;
import java.time.LocalDate;

import static feder.parser.ParamSpec.*;

/**
 * CU5 - Encargos / reservas de productos sin disponibilidad inmediata (RF11).
 * Estados: solicitado -> aceptado|rechazado -> disponible (se notifica) -> convertido (en pedido) | cancelado.
 */
public final class EncargoHandler {

    private EncargoHandler() {}

    public static void registrar(Registro r) {
        r.cmd("INSENC", "CU5", "encargos", Accion.CREAR,
                "Solicitar un encargo de un producto (modalidad encargo o ambas). Agregue mas productos con ADDENC.",
                "INSENC[\"5\",\"20\",\"2026-12-20\",\"Para fiesta de fin de ano\"]", EncargoHandler::insertar,
                entero("producto_id"), decimal("cantidad"), fecha("fecha_requerida").opcional(),
                texto("observacion", 500).opcional());
        r.cmd("ADDENC", "CU5", "encargos", Accion.CREAR, "Agregar otro producto a un encargo aun SOLICITADO.",
                "ADDENC[\"2\",\"6\",\"10\"]", EncargoHandler::agregar,
                entero("encargo_id"), entero("producto_id"), decimal("cantidad"));
        r.cmd("LISENC", "CU5", "encargos", Accion.VER, "Listar encargos por estado. El Cliente ve solo los suyos.",
                "LISENC[\"*\"]   o   LISENC[\"aceptado\"]", EncargoHandler::listar,
                opcion("estado", "solicitado", "aceptado", "rechazado", "disponible", "convertido", "cancelado").todos());
        r.cmd("VERENC", "CU5", "encargos", Accion.VER, "Ver el detalle de un encargo.",
                "VERENC[\"2\"]", EncargoHandler::ver, entero("id"));
        r.cmd("CANENC", "CU5", "encargos", Accion.ELIMINAR, "Cancelar un encargo que aun no se convirtio en pedido.",
                "CANENC[\"2\"]", EncargoHandler::cancelar, entero("id"));
        r.cmd("MODENC", "CU5", "encargos", Accion.EDITAR,
                "Cambiar estado del encargo: aceptado | rechazado | disponible (verifica stock y notifica al cliente).",
                "MODENC[\"2\",\"disponible\",\"Ya llego la cosecha\"]", EncargoHandler::cambiarEstado,
                entero("id"), opcion("estado", "aceptado", "rechazado", "disponible"), texto("observacion", 500).opcional());
        r.cmd("CONENC", "CU5", "encargos", Accion.CREAR,
                "Convertir un encargo DISPONIBLE en pedido: reserva el stock y registra la entrega.",
                "CONENC[\"2\",\"envio\",\"Calle Warnes 120\"]   o   CONENC[\"2\",\"recojo\"]", EncargoHandler::convertir,
                entero("id"), opcion("entrega", "recojo", "envio"), texto("direccion", 200).opcional());
    }

    private static Fila encargoPara(Connection con, int id, Contexto ctx) throws Exception {
        Fila e = Db.fila(con, "SELECT * FROM encargo WHERE id = ? FOR UPDATE", id);
        if (e == null) throw new ErrorNegocio("No existe el encargo " + id);
        if (!ctx.esStaff() && e.entero("cliente_id") != ctx.usuarioId)
            throw new ErrorNegocio("El encargo " + id + " no le pertenece.");
        return e;
    }

    private static Fila productoEncargable(Connection con, int productoId) throws Exception {
        Fila pr = Db.fila(con, "SELECT nombre, estado, modalidad_venta, precio_venta, unidad_medida FROM producto WHERE id = ?",
                productoId);
        if (pr == null || !"activo".equals(pr.str("estado")))
            throw new ErrorNegocio("No existe el producto " + productoId + " en el catalogo.");
        if ("inmediata".equals(pr.str("modalidad_venta")))
            throw new ErrorNegocio(pr.str("nombre") + " es de venta inmediata: compruelo con INSCAR[\"" + productoId + "\",\"cantidad\"].");
        return pr;
    }

    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        LocalDate req = p.fecha(2);
        if (req != null && req.isBefore(LocalDate.now()))
            throw new ErrorNegocio("La fecha requerida no puede ser pasada.");
        if (!ctx.esCliente())
            throw new ErrorNegocio("Los encargos se registran a nombre del cliente que escribe; use una cuenta de Cliente.");
        Object[] r = Db.tx(con -> {
            Fila pr = productoEncargable(con, p.entero(0));
            int id = Db.<Integer>valor(con, "INSERT INTO encargo (cliente_id, fecha_requerida, observacion) " +
                    "VALUES (?,?,?) RETURNING id", ctx.usuarioId, req, p.str(3));
            Db.update(con, "INSERT INTO detalle_encargo (encargo_id, producto_id, cantidad, precio_estimado) VALUES (?,?,?,?)",
                    id, p.entero(0), p.dec(1), pr.dec("precio_venta"));
            return new Object[] { id, pr.str("nombre"), pr.str("unidad_medida") };
        });
        return Respuesta.ok("Encargo registrado con id " + r[0] + ": " + p.dec(1) + " " + r[2] + " de " + r[1] + ".\n"
                + "Estado: SOLICITADO. FEDER lo revisara y le avisaremos por correo cuando este disponible.\n"
                + "Puede agregar mas productos con ADDENC[\"" + r[0] + "\",\"producto_id\",\"cantidad\"].");
    }

    private static Respuesta agregar(Params p, Contexto ctx) throws Exception {
        Db.tx(con -> {
            Fila e = encargoPara(con, p.entero(0), ctx);
            if (!"solicitado".equals(e.str("estado")))
                throw new ErrorNegocio("Solo se pueden agregar productos a encargos SOLICITADOS (este esta " + e.str("estado") + ").");
            Fila pr = productoEncargable(con, p.entero(1));
            Db.update(con, "INSERT INTO detalle_encargo (encargo_id, producto_id, cantidad, precio_estimado) VALUES (?,?,?,?) " +
                    "ON CONFLICT (encargo_id, producto_id) DO UPDATE SET cantidad = detalle_encargo.cantidad + EXCLUDED.cantidad",
                    p.entero(0), p.entero(1), p.dec(2), pr.dec("precio_venta"));
            return null;
        });
        return ver(new Params(new Object[] { p.entero(0) }), ctx);
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String sql = "SELECT e.id, u.nombre || ' ' || u.apellido AS cliente, e.fecha_solicitud::date AS solicitado, " +
                "       e.fecha_requerida AS requerido, e.estado, count(de.producto_id) AS productos, " +
                "       COALESCE(sum(de.cantidad * de.precio_estimado), 0) AS total_estimado " +
                "FROM encargo e JOIN usuario u ON u.id = e.cliente_id LEFT JOIN detalle_encargo de ON de.encargo_id = e.id " +
                "WHERE (?::text IS NULL OR e.estado = ?) ";
        return Respuesta.ok(ctx.esStaff()
                ? Db.tabla(sql + "GROUP BY e.id, u.id ORDER BY e.id DESC LIMIT 200", p.str(0), p.str(0))
                : Db.tabla(sql + "AND e.cliente_id = ? GROUP BY e.id, u.id ORDER BY e.id DESC", p.str(0), p.str(0), ctx.usuarioId));
    }

    private static Respuesta ver(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.con(con -> {
            Fila e = encargoPara(con, p.entero(0), ctx);
            return "Encargo " + e.str("id") + "  |  estado: " + e.str("estado").toUpperCase()
                    + "  |  requerido: " + (e.get("fecha_requerida") == null ? "-" : e.str("fecha_requerida"))
                    + (e.str("observacion") != null ? "\nObservacion: " + e.str("observacion") : "") + "\n"
                    + Db.tabla(con, "SELECT pr.id, pr.nombre AS producto, de.cantidad, pr.unidad_medida AS unidad, " +
                    "de.precio_estimado AS precio, de.cantidad * de.precio_estimado AS subtotal, " +
                    "d.stock_disponible AS disponible_hoy FROM detalle_encargo de JOIN producto pr ON pr.id = de.producto_id " +
                    "JOIN v_disponibilidad_producto d ON d.producto_id = pr.id WHERE de.encargo_id = ?", p.entero(0));
        }));
    }

    private static Respuesta cancelar(Params p, Contexto ctx) throws Exception {
        Db.tx(con -> {
            Fila e = encargoPara(con, p.entero(0), ctx);
            String est = e.str("estado");
            if (est.equals("convertido") || est.equals("cancelado") || est.equals("rechazado"))
                throw new ErrorNegocio("El encargo esta " + est + " y ya no se puede cancelar.");
            Db.update(con, "UPDATE encargo SET estado = 'cancelado' WHERE id = ?", p.entero(0));
            return null;
        });
        return Respuesta.ok("Encargo " + p.entero(0) + " cancelado.");
    }

    private static Respuesta cambiarEstado(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String nuevo = p.str(1);
        Db.tx(con -> {
            Fila e = encargoPara(con, id, ctx);
            String actual = e.str("estado");
            boolean valido = switch (nuevo) {
                case "aceptado", "rechazado" -> actual.equals("solicitado");
                case "disponible"            -> actual.equals("aceptado") || actual.equals("solicitado");
                default -> false;
            };
            if (!valido) throw new ErrorNegocio("No se puede pasar el encargo de " + actual + " a " + nuevo + ".");
            if (nuevo.equals("disponible")) {
                StringBuilder falta = new StringBuilder();
                for (Fila f : Db.filas(con, "SELECT pr.nombre, de.cantidad, d.stock_disponible FROM detalle_encargo de " +
                        "JOIN producto pr ON pr.id = de.producto_id JOIN v_disponibilidad_producto d ON d.producto_id = pr.id " +
                        "WHERE de.encargo_id = ?", id)) {
                    if (f.dec("cantidad").compareTo(f.dec("stock_disponible")) > 0)
                        falta.append("\n  - ").append(f.str("nombre")).append(": requiere ").append(f.dec("cantidad"))
                             .append(", hay ").append(f.dec("stock_disponible"));
                }
                if (falta.length() > 0) throw new ErrorNegocio("Aun no hay stock para este encargo:" + falta);
            }
            Db.update(con, "UPDATE encargo SET estado = ?, observacion = COALESCE(?, observacion) WHERE id = ?",
                    nuevo, p.str(2), id);
            if (nuevo.equals("disponible")) {
                Notificador.notificar(con, e.entero("cliente_id"), null, id, "encargo_disponible",
                        "¡Su encargo " + id + " ya esta DISPONIBLE!\n\nConfirmelo para generar su pedido:\n"
                        + "  CONENC[\"" + id + "\",\"recojo\"]   o   CONENC[\"" + id + "\",\"envio\",\"su direccion\"]\n\n"
                        + "Detalle: VERENC[\"" + id + "\"]");
            }
            return null;
        });
        return Respuesta.ok("Encargo " + id + " -> " + nuevo.toUpperCase()
                + (nuevo.equals("disponible") ? ". Se notifico al cliente por correo." : "."));
    }

    private static Respuesta convertir(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        return Respuesta.ok(Db.tx(con -> {
            Fila e = encargoPara(con, id, ctx);
            if (!"disponible".equals(e.str("estado")))
                throw new ErrorNegocio("El encargo esta " + e.str("estado") + "; solo se convierten encargos DISPONIBLES.");
            int pedido = PedidoServicio.nuevoPedido(con, e.entero("cliente_id"), "encargo", id);
            // Precio vigente del catalogo al momento de convertir
            Db.update(con, "INSERT INTO detalle_pedido (pedido_id, producto_id, cantidad, precio_unitario) " +
                    "SELECT ?, de.producto_id, de.cantidad, pr.precio_venta FROM detalle_encargo de " +
                    "JOIN producto pr ON pr.id = de.producto_id WHERE de.encargo_id = ?", pedido, id);
            PedidoServicio.recalcular(con, pedido);
            if (ctx.esStaff()) Db.update(con, "UPDATE pedido SET gestionado_por = ? WHERE id = ?", ctx.usuarioId, pedido);
            PedidoServicio.reservarYEntregar(con, pedido, p.str(1), p.str(2), null);
            Db.update(con, "UPDATE encargo SET estado = 'convertido' WHERE id = ?", id);
            return "Encargo " + id + " convertido en pedido (id " + pedido + "). Stock reservado.\n\n"
                    + PedidoServicio.resumen(con, pedido)
                    + "\n\nRegistre el pago con INSPAG[\"" + pedido + "\",\"efectivo|tarjeta|qr\"]";
        }));
    }
}
