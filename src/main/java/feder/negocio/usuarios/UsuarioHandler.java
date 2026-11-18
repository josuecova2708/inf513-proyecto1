package feder.negocio.usuarios;

import feder.negocio.*;
import feder.parser.ComandoSpec.Accion;
import feder.parser.ParamSpec;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Contexto;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;

import static feder.parser.ParamSpec.*;

/** CU1 - Gestion de usuarios, roles y permisos (RF01). */
public final class UsuarioHandler {

    static final String[] ROLES   = { "administrador", "personal", "productor", "cliente" };
    static final String[] MODULOS = { "usuarios", "perfil", "comunidades", "emprendimientos", "ofertas",
            "produccion", "lotes", "catalogo", "inventario", "carrito", "encargos", "pedidos", "pagos",
            "entregas", "notificaciones", "finanzas", "reportes" };

    private static final Edicion EDICION_ADMIN = new Edicion("usuario")
            .campo("nombre", texto("nombre", 80))
            .campo("apellido", texto("apellido", 80))
            .campo("ci", ci("ci"))
            .campo("email", email("email"))
            .campo("telefono", telefono("telefono").opcional())
            .campo("direccion", texto("direccion", 200).opcional())
            .campo("rol", "rol_id", opcion("rol", ROLES))
            .campo("estado", opcion("estado", "activo", "inactivo"))
            .campo("password", "password_hash", texto("password", 72));

    private static final Edicion EDICION_PERFIL = new Edicion("usuario")
            .campo("nombre", texto("nombre", 80))
            .campo("apellido", texto("apellido", 80))
            .campo("telefono", telefono("telefono").opcional())
            .campo("direccion", texto("direccion", 200).opcional())
            .campo("password", "password_hash", texto("password", 72));

    private UsuarioHandler() {}

    public static void registrar(Registro r) {
        r.cmd("REGCLI", "CU1", "usuarios", Accion.REGISTRO,
                "Registrarse como Cliente de FEDER con el correo desde el que escribe.",
                "REGCLI[\"7894561\",\"Ana\",\"Rojas\",\"71234567\",\"miClave123\",\"Av. Banzer 4to anillo\"]",
                UsuarioHandler::registrarCliente,
                ci("ci"), texto("nombre", 80), texto("apellido", 80), telefono("telefono"),
                texto("password", 72), texto("direccion", 200).opcional());

        r.cmd("VERPRF", "CU1", "perfil", Accion.VER, "Ver mis datos de usuario.",
                "VERPRF", UsuarioHandler::verPerfil);
        r.cmd("MODPRF", "CU1", "perfil", Accion.EDITAR, "Modificar un dato de mi perfil.",
                "MODPRF[\"telefono\",\"76543210\"]", UsuarioHandler::modPerfil,
                opcion("campo", EDICION_PERFIL.nombres()), texto("valor", 200).opcional());

        r.cmd("LISUSU", "CU1", "usuarios", Accion.VER, "Listar usuarios (opcionalmente por rol).",
                "LISUSU[\"*\"]   o   LISUSU[\"productor\"]", UsuarioHandler::listar,
                opcion("rol", ROLES).todos());
        r.cmd("INSUSU", "CU1", "usuarios", Accion.CREAR, "Registrar un usuario con cualquier rol.",
                "INSUSU[\"personal\",\"6543210\",\"Luis\",\"Vaca\",\"luis.vaca@gmail.com\",\"70011223\",\"clave123\",\"Calle Sucre 12\"]",
                UsuarioHandler::insertar,
                opcion("rol", ROLES), ci("ci"), texto("nombre", 80), texto("apellido", 80), email("email"),
                telefono("telefono").opcional(), texto("password", 72), texto("direccion", 200).opcional());
        r.cmd("MODUSU", "CU1", "usuarios", Accion.EDITAR, "Modificar un dato de un usuario.",
                "MODUSU[\"5\",\"rol\",\"personal\"]", UsuarioHandler::modificar,
                entero("id"), opcion("campo", EDICION_ADMIN.nombres()), texto("valor", 200).opcional());
        r.cmd("ELIUSU", "CU1", "usuarios", Accion.ELIMINAR, "Dar de baja (inactivar) un usuario.",
                "ELIUSU[\"5\"]", UsuarioHandler::eliminar, entero("id"));

        r.cmd("LISROL", "CU1", "usuarios", Accion.VER, "Listar roles y cuantos usuarios tiene cada uno.",
                "LISROL", UsuarioHandler::listarRoles);
        r.cmd("LISPRM", "CU1", "usuarios", Accion.VER, "Ver la matriz de permisos (ver/crear/editar/eliminar) por rol.",
                "LISPRM[\"cliente\"]", UsuarioHandler::listarPermisos, opcion("rol", ROLES).todos());
        r.cmd("ASIPRM", "CU1", "usuarios", Accion.EDITAR, "Conceder o quitar un permiso a un rol sobre un modulo.",
                "ASIPRM[\"personal\",\"finanzas\",\"ver\",\"no\"]", UsuarioHandler::asignarPermiso,
                opcion("rol", ROLES), opcion("modulo", MODULOS),
                opcion("accion", "ver", "crear", "editar", "eliminar"), opcion("valor", "si", "no"));
    }

    static String hash(String password) {
        if (password.length() < 6) throw new ErrorNegocio("La contraseña debe tener al menos 6 caracteres.");
        return BCrypt.hashpw(password, BCrypt.gensalt(10));
    }

    static int rolId(Connection con, String rol) throws Exception {
        Integer id = Db.valor(con, "SELECT id FROM rol WHERE lower(nombre) = ?", rol);
        if (id == null) throw new ErrorNegocio("No existe el rol " + rol);
        return id;
    }

    private static final String SELECT_USU =
            "SELECT u.id, r.nombre AS rol, u.ci, u.nombre, u.apellido, u.email, u.telefono, u.direccion, " +
            "       u.estado, to_char(u.fecha_registro, 'YYYY-MM-DD') AS registro " +
            "FROM usuario u JOIN rol r ON r.id = u.rol_id ";

    // REGCLI
    private static Respuesta registrarCliente(Params p, Contexto ctx) throws Exception {
        if (ctx.inactivo) throw new ErrorNegocio("Su correo ya esta registrado pero la cuenta esta inactiva.");
        String hash = hash(p.str(4));
        int id = Db.tx(con -> {
            int rol = rolId(con, "cliente");
            return Db.<Integer>valor(con,
                    "INSERT INTO usuario (rol_id, nombre, apellido, ci, email, telefono, password_hash, direccion) " +
                    "VALUES (?,?,?,?,?,?,?,?) RETURNING id",
                    rol, p.str(1), p.str(2), p.str(0), ctx.email.toLowerCase(), p.str(3), hash, p.str(5));
        });
        return Respuesta.ok("¡Bienvenido/a a FEDER, " + p.str(1) + "! Quedo registrado/a como Cliente (id " + id + ").\n\n"
                + "Primeros pasos:\n"
                + "  LISPRO[\"*\"]                 ver el catalogo\n"
                + "  INSCAR[\"producto_id\",\"cantidad\"]  agregar al carrito\n"
                + "  CONCAR[\"recojo\"]            confirmar la compra\n"
                + "  HELP                        ver todos sus comandos");
    }

    // VERPRF
    private static Respuesta verPerfil(Params p, Contexto ctx) throws Exception {
        String datos = Db.tabla(SELECT_USU + "WHERE u.id = ?", ctx.usuarioId);
        if (ctx.esProductor()) {
            datos += "\n\nEmprendimiento asociado:\n" + Db.tabla(
                    "SELECT e.id, e.nombre, c.nombre AS comunidad, e.rubro FROM emprendimiento e " +
                    "JOIN comunidad c ON c.id = e.comunidad_id WHERE e.usuario_id = ?", ctx.usuarioId);
        }
        return Respuesta.ok(datos);
    }

    // MODPRF
    private static Respuesta modPerfil(Params p, Contexto ctx) throws Exception {
        String campo = p.str(0);
        Object valor = EDICION_PERFIL.convertir(campo, p.str(1));
        if (campo.equals("password")) valor = hash((String) valor);
        Object v = valor;
        Db.tx(con -> { EDICION_PERFIL.aplicar(con, ctx.usuarioId, campo, v); return null; });
        return Respuesta.ok("Perfil actualizado: " + campo + (campo.equals("password") ? " cambiada." : " = " + valor));
    }

    // LISUSU
    private static Respuesta listar(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(p.tiene(0)
                ? Db.tabla(SELECT_USU + "WHERE lower(r.nombre) = ? ORDER BY u.id", p.str(0))
                : Db.tabla(SELECT_USU + "ORDER BY r.id, u.id"));
    }

    // INSUSU
    private static Respuesta insertar(Params p, Contexto ctx) throws Exception {
        String hash = hash(p.str(6));
        int id = Db.tx(con -> Db.<Integer>valor(con,
                "INSERT INTO usuario (rol_id, ci, nombre, apellido, email, telefono, password_hash, direccion) " +
                "VALUES (?,?,?,?,?,?,?,?) RETURNING id",
                rolId(con, p.str(0)), p.str(1), p.str(2), p.str(3), p.str(4), p.str(5), hash, p.str(7)));
        String extra = p.str(0).equals("productor")
                ? "\nPara vincularlo a su emprendimiento: MODEMP[\"emprendimiento_id\",\"usuario\",\"" + p.str(4) + "\"]"
                : "";
        return Respuesta.ok("Usuario registrado correctamente con id " + id + " (rol " + p.str(0) + ")." + extra);
    }

    // MODUSU
    private static Respuesta modificar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        String campo = p.str(1);
        Object valor = EDICION_ADMIN.convertir(campo, p.str(2));
        if (id == ctx.usuarioId && (campo.equals("rol") || campo.equals("estado"))) {
            throw new ErrorNegocio("No puede cambiar su propio rol ni su estado (evita quedarse sin acceso).");
        }
        Object v0 = valor;
        Db.tx(con -> {
            Object v = v0;
            if (campo.equals("rol")) v = rolId(con, (String) v0);
            if (campo.equals("password")) v = hash((String) v0);
            EDICION_ADMIN.aplicar(con, id, campo, v);
            return null;
        });
        return Respuesta.ok("Usuario " + id + " actualizado: " + campo
                + (campo.equals("password") ? " cambiada." : " = " + valor));
    }

    // ELIUSU
    private static Respuesta eliminar(Params p, Contexto ctx) throws Exception {
        int id = p.entero(0);
        if (id == ctx.usuarioId) throw new ErrorNegocio("No puede darse de baja a si mismo.");
        int n = Db.tx(con -> Db.update(con, "UPDATE usuario SET estado = 'inactivo' WHERE id = ?", id));
        if (n == 0) throw new ErrorNegocio("No existe el usuario " + id);
        return Respuesta.ok("Usuario " + id + " dado de baja (estado inactivo). Sus datos historicos se conservan.");
    }

    // LISROL
    private static Respuesta listarRoles(Params p, Contexto ctx) throws Exception {
        return Respuesta.ok(Db.tabla(
                "SELECT r.id, r.nombre, r.descripcion, " +
                "       count(u.id) FILTER (WHERE u.estado = 'activo') AS usuarios_activos " +
                "FROM rol r LEFT JOIN usuario u ON u.rol_id = r.id GROUP BY r.id ORDER BY r.id"));
    }

    // LISPRM
    private static Respuesta listarPermisos(Params p, Contexto ctx) throws Exception {
        String sql =
                "SELECT r.nombre AS rol, pe.modulo, " +
                "       CASE WHEN rp.ver THEN 'SI' ELSE '-' END AS ver, " +
                "       CASE WHEN rp.crear THEN 'SI' ELSE '-' END AS crear, " +
                "       CASE WHEN rp.editar THEN 'SI' ELSE '-' END AS editar, " +
                "       CASE WHEN rp.eliminar THEN 'SI' ELSE '-' END AS eliminar " +
                "FROM rol_permiso rp JOIN rol r ON r.id = rp.rol_id JOIN permiso pe ON pe.id = rp.permiso_id ";
        return Respuesta.ok(p.tiene(0)
                ? Db.tabla(sql + "WHERE lower(r.nombre) = ? ORDER BY pe.id", p.str(0))
                : Db.tabla(sql + "ORDER BY r.id, pe.id"));
    }

    // ASIPRM
    private static Respuesta asignarPermiso(Params p, Contexto ctx) throws Exception {
        String rol = p.str(0), modulo = p.str(1), accion = p.str(2);
        boolean valor = p.str(3).equals("si");
        if (rol.equals("administrador") && modulo.equals("usuarios")) {
            throw new ErrorNegocio("Los permisos del Administrador sobre 'usuarios' no se pueden cambiar (evita quedar sin acceso).");
        }
        Db.tx(con -> {
            int rid = rolId(con, rol);
            Integer pid = Db.valor(con, "SELECT id FROM permiso WHERE modulo = ?", modulo);
            if (pid == null) throw new ErrorNegocio("No existe el modulo " + modulo);
            // accion sale de una lista cerrada (ver|crear|editar|eliminar), no del texto libre del usuario
            Db.update(con, "INSERT INTO rol_permiso (rol_id, permiso_id, ver, crear, editar, eliminar) " +
                    "VALUES (?, ?, false, false, false, false) ON CONFLICT DO NOTHING", rid, pid);
            Db.update(con, "UPDATE rol_permiso SET " + accion + " = ? WHERE rol_id = ? AND permiso_id = ?",
                    valor, rid, pid);
            return null;
        });
        return Respuesta.ok("Permiso actualizado: " + rol + " -> " + modulo + "." + accion + " = " + (valor ? "SI" : "NO"));
    }
}
