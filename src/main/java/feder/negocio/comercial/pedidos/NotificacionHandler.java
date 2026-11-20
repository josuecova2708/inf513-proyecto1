package feder.negocio.pedidos;

import feder.negocio.Db;
import feder.negocio.Respuesta;
import feder.parser.ComandoSpec.Accion;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

/** CU6 - Historial de notificaciones recibidas (RF15). */
public final class NotificacionHandler {

    private NotificacionHandler() {}

    public static void registrar(Registro r) {
        r.cmd("LISNOT", "CU6", "notificaciones", Accion.VER, "Ver mis ultimas notificaciones (se marcan como leidas).",
                "LISNOT", NotificacionHandler::listar);
    }

    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        String tabla = Db.tx(con -> {
            String t = Db.tabla(con, "SELECT n.fecha_envio::timestamp(0) AS fecha, n.tipo, " +
                    "CASE WHEN n.leida THEN '' ELSE 'NUEVA' END AS nueva, left(n.mensaje, 90) AS mensaje " +
                    "FROM notificacion n WHERE n.usuario_id = ? ORDER BY n.fecha_envio DESC LIMIT 30", ctx.usuarioId);
            Db.update(con, "UPDATE notificacion SET leida = true WHERE usuario_id = ? AND NOT leida", ctx.usuarioId);
            return t;
        });
        return Respuesta.ok("MIS NOTIFICACIONES\n" + tabla);
    }
}
