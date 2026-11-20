package feder.negocio.comercial.comercio;

import feder.negocio.*;
import feder.negocio.comercial.pedidos.PedidoServicio;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.math.BigDecimal;
import java.sql.Connection;

import static feder.parser.ParamSpec.*;

/** CU5 - Carrito de compras y venta inmediata (RF09, RF10). Solo el Cliente, sobre su propio carrito. */
public final class CarritoHandler {

    private CarritoHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISCAR", "CU5", "carrito", Accion.VER, "Ver mi carrito con el total y el stock actual.",
                "LISCAR", CarritoHandler::listar);
        r.cmd("INSCAR", "CU5", "carrito", Accion.CREAR,
                "Agregar un producto al carrito (suma a lo que ya tenia). Verifica stock y modalidad.",
                "INSCAR[\"3\",\"2.5\"]", CarritoHandler::agregar, entero("producto_id"), decimal("cantidad"));
        r.cmd("MODCAR", "CU5", "carrito", Accion.EDITAR, "Cambiar la cantidad de un producto del carrito.",
                "MODCAR[\"3\",\"4\"]", CarritoHandler::modificar, entero("producto_id"), decimal("cantidad"));
        r.cmd("ELICAR", "CU5", "carrito", Accion.ELIMINAR, "Quitar un producto del carrito.",
                "ELICAR[\"3\"]", CarritoHandler::quitar, entero("producto_id"));
        r.cmd("VACCAR", "CU5", "carrito", Accion.ELIMINAR, "Vaciar el carrito.",
                "VACCAR", CarritoHandler::vaciar);
        r.cmd("CONCAR", "CU5", "carrito", Accion.CREAR,
                "Confirmar la compra: reserva el stock (sin sobreventa) y genera el pedido. Luego pague con INSPAG.",
                "CONCAR[\"envio\",\"Av. Busch 3er anillo #45\",\"7894561\",\"Ana Rojas\",\"Porton verde\"]   o   CONCAR[\"recojo\"]",
                CarritoHandler::confirmar,
                opcion("entrega", "recojo", "envio"), texto("direccion", 200).opcional(),
                ci("nit_ci").opcional(), texto("razon_social", 120).opcional(), texto("referencia", 200).opcional());
    }

    private static String contenido(Connection con, int carrito) throws Exception {
        Fila t = Db.fila(con, "SELECT p.subtotal, count(dp.id) AS items FROM pedido p " +
                "LEFT JOIN detalle_pedido dp ON dp.pedido_id = p.id WHERE p.id = ? GROUP BY p.id, p.subtotal", carrito);
        return "MI CARRITO\n" + Db.tabla(con,
                "SELECT pr.id, pr.nombre AS producto, dp.cantidad, pr.unidad_medida AS unidad, dp.precio_unitario AS precio, " +
                "       dp.subtotal, d.stock_disponible AS disponible, " +
                "       CASE WHEN dp.cantidad > d.stock_disponible THEN 'SIN STOCK SUFICIENTE' ELSE 'ok' END AS estado " +
                "FROM detalle_pedido dp JOIN producto pr ON pr.id = dp.producto_id " +
                "JOIN v_disponibilidad_producto d ON d.producto_id = pr.id WHERE dp.pedido_id = ? ORDER BY pr.nombre", carrito)
                + "\nProductos: " + t.str("items") + "   TOTAL: Bs " + t.str("subtotal")
                + "\nPara comprar: CONCAR[\"recojo\"]  o  CONCAR[\"envio\",\"direccion\"]  (envio: + Bs " + PedidoServicio.COSTO_ENVIO + ")";
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.con(con -> {
            Integer id = Db.valor(con, "SELECT p.id FROM pedido p WHERE p.cliente_id = ? AND p.estado = 'pendiente' " +
                    "AND p.origen = 'inmediato' AND NOT " + PedidoServicio.ES_PEDIDO_REAL + " LIMIT 1", ctx.usuarioId);
            return id == null ? "Su carrito esta vacio. Vea el catalogo con LISPRO[\"*\"] y agregue con INSCAR[\"producto_id\",\"cantidad\"]."
                              : contenido(con, id);
        }));
    }

    /** Valida el producto para venta inmediata y que la cantidad total no supere el stock. */
    private static Fila validarProducto(Connection con, int productoId, BigDecimal cantidadTotal) throws Exception {
        Fila pr = Db.fila(con, "SELECT p.nombre, p.estado, p.modalidad_venta, p.precio_venta, p.unidad_medida, " +
                "d.stock_disponible FROM producto p JOIN v_disponibilidad_producto d ON d.producto_id = p.id " +
                "WHERE p.id = ?", productoId);
        if (pr == null || !"activo".equals(pr.str("estado")))
            throw new ErrorNegocio("No existe el producto " + productoId + " en el catalogo.");
        if ("encargo".equals(pr.str("modalidad_venta")))
            throw new ErrorNegocio(pr.str("nombre") + " solo se vende por ENCARGO. Use INSENC[\"" + productoId + "\",\"cantidad\"].");
        if (cantidadTotal.compareTo(pr.dec("stock_disponible")) > 0)
            throw new ErrorNegocio("Stock insuficiente de " + pr.str("nombre") + ": hay " + pr.dec("stock_disponible")
                    + " " + pr.str("unidad_medida") + " disponibles y su carrito tendria " + cantidadTotal + "."
                    + ("ambas".equals(pr.str("modalidad_venta")) ? "\nPuede encargar el resto con INSENC." : ""));
        return pr;
    }

    private static Respuesta agregar(Params p, Contexto ctx) throws Exception {
        int productoId = p.entero(0);
        BigDecimal cant = p.dec(1);
        if (cant.signum() <= 0) throw new ErrorNegocio("La cantidad debe ser mayor a 0.");
        return Respuesta.ok(Db.tx(con -> {
            Integer carrito = PedidoServicio.carritoId(con, ctx.usuarioId);
            if (carrito == null) carrito = PedidoServicio.nuevoPedido(con, ctx.usuarioId, "inmediato", null);
            BigDecimal actual = Db.valor(con, "SELECT cantidad FROM detalle_pedido WHERE pedido_id = ? AND producto_id = ?",
                    carrito, productoId);
            Fila pr = validarProducto(con, productoId, cant.add(actual == null ? BigDecimal.ZERO : actual));
            Db.update(con, "INSERT INTO detalle_pedido (pedido_id, producto_id, cantidad, precio_unitario) VALUES (?,?,?,?) " +
                    "ON CONFLICT (pedido_id, producto_id) DO UPDATE SET cantidad = detalle_pedido.cantidad + EXCLUDED.cantidad, " +
                    "precio_unitario = EXCLUDED.precio_unitario", carrito, productoId, cant, pr.dec("precio_venta"));
            PedidoServicio.recalcular(con, carrito);
            return "Agregado: " + cant + " " + pr.str("unidad_medida") + " de " + pr.str("nombre") + ".\n\n" + contenido(con, carrito);
        }));
    }

    private static Respuesta modificar(Params p, Contexto ctx) throws Exception {
        int productoId = p.entero(0);
        BigDecimal cant = p.dec(1);
        if (cant.signum() <= 0) throw new ErrorNegocio("La cantidad debe ser mayor a 0 (para quitarlo use ELICAR).");
        return Respuesta.ok(Db.tx(con -> {
            Integer carrito = PedidoServicio.carritoId(con, ctx.usuarioId);
            if (carrito == null || !Db.existe(con, "SELECT 1 FROM detalle_pedido WHERE pedido_id = ? AND producto_id = ?",
                    carrito, productoId))
                throw new ErrorNegocio("El producto " + productoId + " no esta en su carrito.");
            Fila pr = validarProducto(con, productoId, cant);
            Db.update(con, "UPDATE detalle_pedido SET cantidad = ?, precio_unitario = ? WHERE pedido_id = ? AND producto_id = ?",
                    cant, pr.dec("precio_venta"), carrito, productoId);
            PedidoServicio.recalcular(con, carrito);
            return contenido(con, carrito);
        }));
    }

    private static Respuesta quitar(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.tx(con -> {
            Integer carrito = PedidoServicio.carritoId(con, ctx.usuarioId);
            int n = carrito == null ? 0 : Db.update(con,
                    "DELETE FROM detalle_pedido WHERE pedido_id = ? AND producto_id = ?", carrito, p.entero(0));
            if (n == 0) throw new ErrorNegocio("El producto " + p.entero(0) + " no esta en su carrito.");
            if (!Db.existe(con, "SELECT 1 FROM detalle_pedido WHERE pedido_id = ?", carrito)) {
                Db.update(con, "DELETE FROM pedido WHERE id = ?", carrito);
                return "Producto quitado. Su carrito quedo vacio.";
            }
            PedidoServicio.recalcular(con, carrito);
            return "Producto quitado.\n\n" + contenido(con, carrito);
        }));
    }

    private static Respuesta vaciar(Params p, Contexto ctx) throws Exception {
        int n = Db.tx(con -> {
            Integer carrito = PedidoServicio.carritoId(con, ctx.usuarioId);
            return carrito == null ? 0 : Db.update(con, "DELETE FROM pedido WHERE id = ?", carrito);
        });
        return Respuesta.ok(n == 0 ? "Su carrito ya estaba vacio." : "Carrito vaciado.");
    }

    private static Respuesta confirmar(Params p, Contexto ctx) throws Exception {
        String entrega = p.str(0);
        return Respuesta.ok(Db.tx(con -> {
            Integer carrito = PedidoServicio.carritoId(con, ctx.usuarioId);
            if (carrito == null || !Db.existe(con, "SELECT 1 FROM detalle_pedido WHERE pedido_id = ?", carrito))
                throw new ErrorNegocio("Su carrito esta vacio. Agregue productos con INSCAR.");
            Db.update(con, "UPDATE pedido SET fecha = now(), nit_ci = ?, razon_social = ? WHERE id = ?",
                    p.str(2), p.str(3), carrito);
            PedidoServicio.reservarYEntregar(con, carrito, entrega, p.str(1), p.str(4));
            String horas = PedidoServicio.MINUTOS_RESERVA >= 60
                    ? (PedidoServicio.MINUTOS_RESERVA / 60) + " horas" : PedidoServicio.MINUTOS_RESERVA + " minutos";
            BigDecimal total = Db.valor(con, "SELECT total FROM pedido WHERE id = ?", carrito);
            return "¡Compra registrada! El stock quedo RESERVADO a su nombre por " + horas + ".\n\n"
                    + PedidoServicio.resumen(con, carrito)
                    + "\nEntrega: " + entrega + "\n\nPara completar la compra registre su pago:\n"
                    + "  INSPAG[\"" + carrito + "\",\"efectivo|tarjeta|qr\",\"" + total + "\",\"referencia\"]\n"
                    + "Si no se paga a tiempo, la reserva se libera y el pedido se cancela automaticamente.";
        }));
    }
}
