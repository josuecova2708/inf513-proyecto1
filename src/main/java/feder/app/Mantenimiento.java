package feder.app;

import feder.infra.EnviadorCorreo;
import feder.infra.Log;
import feder.infra.Notificador;
import feder.negocio.Db;
import feder.negocio.Fila;

import java.util.List;

/**
 * Tareas periodicas (cada minuto):
 *  - Libera las reservas de pedidos no pagados a tiempo y avisa al cliente (RF10).
 *  - Marca lotes vencidos y genera alertas de vencimiento y de stock bajo (RF16).
 *  - Envia por correo al personal las alertas nuevas.
 */
public final class Mantenimiento {

    private static long ultimaEjecucion = 0;
    private static int ultimaAlertaVista = -1;

    private Mantenimiento() {}

    public static void ejecutar() {
        if (System.currentTimeMillis() - ultimaEjecucion < 60_000) return;
        ultimaEjecucion = System.currentTimeMillis();
        try {
            liberarReservasVencidas();
            revisarAlertas();
        } catch (Exception e) {
            Log.error("Error en mantenimiento periodico", e);
        }
    }

    private static void liberarReservasVencidas() throws Exception {
        List<Fila> afectados = Db.tx(con -> {
            List<Fila> lista = Db.filas(con,
                    "SELECT DISTINCT pe.id, pe.numero, u.email FROM reserva_lote rl " +
                    "JOIN detalle_pedido dp ON dp.id = rl.detalle_pedido_id JOIN pedido pe ON pe.id = dp.pedido_id " +
                    "JOIN usuario u ON u.id = pe.cliente_id " +
                    "WHERE rl.estado = 'reservada' AND rl.fecha_expiracion < now() AND pe.estado = 'pendiente'");
            if (!lista.isEmpty()) Db.valor(con, "SELECT fn_liberar_reservas_vencidas()");
            return lista;
        });
        for (Fila f : afectados) {
            Log.info("  [RESERVA VENCIDA] pedido " + f.str("numero") + " cancelado");
            try {
                EnviadorCorreo.enviar(f.str("email"), "FEDER - Pedido " + f.str("numero") + " cancelado",
                        "=== FEDER ===\n\nSu pedido " + f.str("numero") + " fue CANCELADO porque no se registro el pago "
                        + "dentro del plazo de reserva. Los productos volvieron a estar disponibles.\n"
                        + "Puede volver a comprar con INSCAR y CONCAR.");
            } catch (Exception e) {
                Log.error("No se pudo avisar la cancelacion a " + f.str("email"), e);
            }
        }
    }

    private static void revisarAlertas() throws Exception {
        List<Fila> nuevas = Db.tx(con -> {
            Db.update(con, "UPDATE lote SET estado = 'vencido', version = version + 1 " +
                    "WHERE estado = 'disponible' AND fecha_vencimiento < CURRENT_DATE");
            Db.update(con, "INSERT INTO alerta_inventario (producto_id, tipo, mensaje) " +
                    "SELECT pr.producto_id, 'vencimiento', " +
                    "       format('Lote %s de %s %s el %s (stock %s)', l.codigo, p.nombre, " +
                    "              CASE WHEN l.fecha_vencimiento < CURRENT_DATE THEN 'vencio' ELSE 'vence' END, " +
                    "              l.fecha_vencimiento, l.stock_disponible) " +
                    "FROM lote l JOIN produccion pr ON pr.id = l.produccion_id JOIN producto p ON p.id = pr.producto_id " +
                    "WHERE l.fecha_vencimiento <= CURRENT_DATE + 7 AND l.stock_disponible > 0 " +
                    "AND NOT EXISTS (SELECT 1 FROM alerta_inventario a WHERE a.tipo = 'vencimiento' " +
                    "                AND a.mensaje LIKE 'Lote ' || l.codigo || ' %')");
            Db.filas(con, "SELECT fn_revisar_stock_minimo(id)::text FROM producto WHERE estado = 'activo'");
            if (ultimaAlertaVista < 0) {   // al arrancar no se reenvian alertas antiguas
                ultimaAlertaVista = Db.entero(con, "SELECT COALESCE(max(id), 0) FROM alerta_inventario");
                return List.of();
            }
            return Db.filas(con, "SELECT id, tipo, mensaje FROM alerta_inventario WHERE id > ? ORDER BY id", ultimaAlertaVista);
        });
        if (nuevas.isEmpty()) return;
        StringBuilder sb = new StringBuilder("Se generaron nuevas alertas de inventario:\n\n");
        for (Fila f : nuevas) {
            sb.append("  [").append(f.str("tipo")).append("] ").append(f.str("mensaje")).append('\n');
            ultimaAlertaVista = Math.max(ultimaAlertaVista, f.entero("id"));
        }
        sb.append("\nRevise con LISALE[\"activa\"] y marque como atendidas con ATEALE[\"id\"].");
        Log.info("  [ALERTAS] " + nuevas.size() + " nuevas");
        Notificador.avisarStaff("Alertas de inventario", sb.toString());
    }
}
