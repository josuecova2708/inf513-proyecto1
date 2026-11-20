package feder.negocio.operacion.inventario;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import java.math.BigDecimal;

import static feder.parser.ParamSpec.*;

/**
 * CU4 - Inventario propio de FEDER: salidas, devoluciones y ajustes de lotes (RF05),
 * y alertas de inventario bajo / vencimiento (RF16).
 * Las entradas se registran al ingresar un lote (INSLOT); las salidas por venta, al preparar el pedido.
 */
public final class InventarioHandler {

    private InventarioHandler() {}

    public static void registrar(Registro r) {
        r.cmd("INSMOV", "CU4", "inventario", Accion.CREAR,
                "Registrar movimiento manual de un lote: salida (merma), devolucion (+) o ajuste (+/-).",
                "INSMOV[\"2\",\"salida\",\"5\",\"Merma por humedad\"]   o   INSMOV[\"2\",\"ajuste\",\"-1.5\",\"Conteo fisico\"]",
                InventarioHandler::movimiento,
                entero("lote_id"), opcion("tipo", "salida", "devolucion", "ajuste"),
                decimalSigno("cantidad"), texto("motivo", 200));
        r.cmd("LISMOV", "CU4", "inventario", Accion.VER, "Historial de movimientos de inventario (filtro por lote).",
                "LISMOV[\"*\"]   o   LISMOV[\"2\"]", InventarioHandler::listarMovimientos, entero("lote_id").todos());
        r.cmd("LISALE", "CU4", "inventario", Accion.VER, "Alertas de inventario (stock bajo y vencimiento).",
                "LISALE[\"activa\"]   o   LISALE[\"*\"]", InventarioHandler::listarAlertas,
                opcion("estado", "activa", "atendida").todos());
        r.cmd("ATEALE", "CU4", "inventario", Accion.EDITAR, "Marcar una alerta como atendida.",
                "ATEALE[\"3\"]", InventarioHandler::atenderAlerta, entero("id"));
    }

    private static Respuesta movimiento(Params p, Contexto ctx) throws Exception {
        int loteId = p.entero(0);
        String tipo = p.str(1);
        BigDecimal cant = p.dec(2);
        if (cant.signum() == 0) throw new ErrorNegocio("La cantidad no puede ser 0.");
        if (!tipo.equals("ajuste") && cant.signum() < 0)
            throw new ErrorNegocio("Para " + tipo + " la cantidad debe ser positiva (solo el ajuste admite negativos).");
        // efecto sobre el stock disponible del lote
        BigDecimal delta = tipo.equals("salida") ? cant.negate() : cant;

        BigDecimal nuevo = Db.tx(con -> {
            // Bloqueo de la fila: evita que una venta concurrente lea un stock desactualizado
            Fila l = Db.fila(con, "SELECT l.stock_disponible, l.estado, pr.producto_id FROM lote l " +
                    "JOIN produccion pr ON pr.id = l.produccion_id WHERE l.id = ? FOR UPDATE OF l", loteId);
            if (l == null) throw new ErrorNegocio("No existe el lote " + loteId);
            BigDecimal resultado = l.dec("stock_disponible").add(delta);
            if (resultado.signum() < 0)
                throw new ErrorNegocio("Stock insuficiente en el lote: hay " + l.dec("stock_disponible")
                        + " disponibles (lo reservado para pedidos no se puede descontar).");
            Db.update(con, "UPDATE lote SET stock_disponible = ?, version = version + 1, " +
                    "estado = CASE WHEN estado = 'vencido' THEN estado WHEN ? = 0 THEN 'agotado' ELSE 'disponible' END " +
                    "WHERE id = ?", resultado, resultado, loteId);
            Db.update(con, "INSERT INTO movimiento_inventario (lote_id, usuario_id, tipo, cantidad, motivo) " +
                    "VALUES (?,?,?,?,?)", loteId, ctx.usuarioId, tipo, tipo.equals("ajuste") ? cant : cant.abs(), p.str(3));
            Db.valor(con, "SELECT fn_revisar_stock_minimo(?)::text", l.entero("producto_id"));
            return resultado;
        });
        return Respuesta.ok("Movimiento de " + tipo.toUpperCase() + " registrado en el lote " + loteId
                + ". Stock disponible del lote: " + nuevo);
    }

    private static Respuesta listarMovimientos(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.tabla(
                "SELECT m.id, m.fecha::timestamp(0) AS fecha, l.codigo AS lote, pr2.nombre AS producto, m.tipo, " +
                "       m.cantidad, m.motivo, COALESCE(u.nombre || ' ' || u.apellido, 'sistema') AS usuario " +
                "FROM movimiento_inventario m JOIN lote l ON l.id = m.lote_id " +
                "JOIN produccion pr ON pr.id = l.produccion_id JOIN producto pr2 ON pr2.id = pr.producto_id " +
                "LEFT JOIN usuario u ON u.id = m.usuario_id " +
                "WHERE (?::int IS NULL OR m.lote_id = ?) ORDER BY m.fecha DESC LIMIT 100", p.entero(0), p.entero(0)));
    }

    private static Respuesta listarAlertas(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.tabla(
                "SELECT a.id, a.fecha::timestamp(0) AS fecha, a.tipo, pr.nombre AS producto, a.mensaje, a.estado " +
                "FROM alerta_inventario a JOIN producto pr ON pr.id = a.producto_id " +
                "WHERE (?::text IS NULL OR a.estado = ?) ORDER BY a.estado, a.fecha DESC LIMIT 100",
                p.str(0), p.str(0)));
    }

    private static Respuesta atenderAlerta(Params p, Contexto ctx) throws Exception {
        int n = Db.tx(con -> Db.update(con,
                "UPDATE alerta_inventario SET estado = 'atendida' WHERE id = ? AND estado = 'activa'", p.entero(0)));
        if (n == 0) throw new ErrorNegocio("No existe una alerta ACTIVA con id " + p.entero(0));
        return Respuesta.ok("Alerta " + p.entero(0) + " marcada como atendida.");
    }
}
