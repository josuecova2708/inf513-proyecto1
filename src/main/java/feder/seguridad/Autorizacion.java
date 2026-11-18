package feder.seguridad;

import feder.negocio.Db;
import feder.negocio.Fila;
import feder.parser.ComandoSpec;
import feder.parser.ComandoSpec.Accion;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * Autenticacion por correo remitente + autorizacion por permisos de rol guardados en la BD
 * (tablas rol, permiso, rol_permiso). Cambiar un permiso con ASIPRM surte efecto sin recompilar.
 *
 * Nota para la defensa: el remitente de un correo es falsificable; para el alcance academico
 * del sistema por comandos es suficiente, pero no es autenticacion fuerte.
 */
public final class Autorizacion {

    private Autorizacion() {}

    public static Contexto contextoDe(Connection con, String email) throws Exception {
        if (email == null || email.isBlank()) return Contexto.anonimo(email, false);
        Fila f = Db.fila(con,
                "SELECT u.id, u.rol_id, r.nombre AS rol, u.nombre || ' ' || u.apellido AS nombre, u.estado, " +
                "       (SELECT e.id FROM emprendimiento e WHERE e.usuario_id = u.id) AS emp " +
                "FROM usuario u JOIN rol r ON r.id = u.rol_id WHERE lower(u.email) = lower(?)",
                email.strip());
        if (f == null) return Contexto.anonimo(email.strip(), false);
        if (!"activo".equals(f.str("estado"))) return Contexto.anonimo(email.strip(), true);
        return new Contexto(email.strip(), f.entero("id"), f.entero("rol_id"), f.str("rol"),
                f.str("nombre"), f.entero("emp"), true, false);
    }

    /** Matriz modulo -> [ver, crear, editar, eliminar] del rol. */
    public static Map<String, boolean[]> permisosDe(Connection con, int rolId) throws Exception {
        Map<String, boolean[]> m = new HashMap<>();
        for (Fila f : Db.filas(con,
                "SELECT p.modulo, rp.ver, rp.crear, rp.editar, rp.eliminar " +
                "FROM rol_permiso rp JOIN permiso p ON p.id = rp.permiso_id WHERE rp.rol_id = ?", rolId)) {
            m.put(f.str("modulo"), new boolean[] {
                    f.bool("ver"), f.bool("crear"), f.bool("editar"), f.bool("eliminar") });
        }
        return m;
    }

    /** ¿Puede el contexto ejecutar el comando? */
    public static boolean puede(Contexto ctx, Map<String, boolean[]> permisos, ComandoSpec c) {
        if (c.accion == Accion.PUBLICO)  return true;
        if (c.accion == Accion.REGISTRO) return !ctx.registrado;
        if (!ctx.registrado)             return false;
        boolean[] p = permisos.get(c.modulo);
        if (p == null) return false;
        return switch (c.accion) {
            case VER      -> p[0];
            case CREAR    -> p[1];
            case EDITAR   -> p[2];
            case ELIMINAR -> p[3];
            default       -> false;
        };
    }
}
