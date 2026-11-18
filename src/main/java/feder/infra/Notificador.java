package feder.infra;

import feder.negocio.Db;
import feder.negocio.Fila;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Notificaciones al cliente (RF15). Se guardan en la tabla notificacion DENTRO de la transaccion
 * del comando y el correo se envia recien cuando el comando termino bien (si hubo rollback,
 * no sale ningun correo).
 */
public final class Notificador {

    private record Pendiente(String email, String asunto, String mensaje) {}

    private static final ThreadLocal<List<Pendiente>> COLA = new ThreadLocal<>();

    private Notificador() {}

    public static void iniciar()  { COLA.set(new ArrayList<>()); }
    public static void descartar() { COLA.remove(); }

    /**
     * @param tipo pedido_confirmado | pago_confirmado | encargo_disponible | pedido_listo
     */
    public static void notificar(Connection con, int usuarioId, Integer pedidoId, Integer encargoId,
                                 String tipo, String mensaje) throws SQLException {
        Db.update(con, "INSERT INTO notificacion (usuario_id, pedido_id, encargo_id, tipo, canal, mensaje) "
                + "VALUES (?,?,?,?, 'email', ?)", usuarioId, pedidoId, encargoId, tipo,
                mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje);
        String email = Db.valor(con, "SELECT email FROM usuario WHERE id = ?", usuarioId);
        List<Pendiente> cola = COLA.get();
        if (email != null && cola != null) {
            cola.add(new Pendiente(email, "FEDER - " + titulo(tipo), mensaje));
        }
    }

    public static void enviarPendientes() {
        List<Pendiente> cola = COLA.get();
        if (cola == null) return;
        for (Pendiente p : cola) {
            enviarSeguro(p.email, p.asunto, cuerpo(p.mensaje));
        }
        cola.clear();
    }

    /** Aviso inmediato a todo el personal activo (Administrador y Personal), p. ej. alertas de stock. */
    public static void avisarStaff(String asunto, String mensaje) {
        try {
            List<Fila> staff = Db.con(c -> Db.filas(c,
                    "SELECT u.email FROM usuario u JOIN rol r ON r.id = u.rol_id "
                    + "WHERE r.nombre IN ('Administrador','Personal') AND u.estado = 'activo'"));
            for (Fila f : staff) enviarSeguro(f.str("email"), "FEDER - " + asunto, cuerpo(mensaje));
        } catch (Exception e) {
            Log.error("No se pudo avisar al personal: " + asunto, e);
        }
    }

    private static void enviarSeguro(String email, String asunto, String cuerpo) {
        try {
            EnviadorCorreo.enviar(email, asunto, cuerpo);
            Log.info("  [NOTIFICACION] " + asunto + " -> " + email);
        } catch (Exception e) {
            Log.error("No se pudo enviar la notificacion a " + email, e);
        }
    }

    private static String titulo(String tipo) {
        return switch (tipo) {
            case "pedido_confirmado"  -> "Pedido confirmado";
            case "pago_confirmado"    -> "Pago confirmado";
            case "encargo_disponible" -> "Su encargo esta disponible";
            case "pedido_listo"       -> "Pedido listo para entrega";
            default                   -> "Notificacion";
        };
    }

    private static String cuerpo(String mensaje) {
        return "=== FEDER - Comercio de productos agricolas ===\n\n" + mensaje
                + "\n\n---\nEste es un aviso automatico de " + ConexionCorreo.CORREO
                + "\nEnvie HELP para ver sus comandos.";
    }
}
