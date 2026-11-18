package feder.app;

import feder.negocio.Db;
import feder.infra.Log;
import feder.infra.Notificador;
import feder.negocio.ErrorNegocio;
import feder.negocio.Respuesta;
import feder.negocio.comercio.*;
import feder.negocio.finanzas.*;
import feder.negocio.inventario.*;
import feder.negocio.pedidos.*;
import feder.negocio.produccion.*;
import feder.negocio.productores.*;
import feder.negocio.reportes.*;
import feder.negocio.usuarios.*;
import feder.parser.*;
import feder.parser.ComandoSpec.Accion;
import feder.seguridad.Autorizacion;
import feder.seguridad.Contexto;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Ciclo de vida de un comando:
 *   Parser (sintaxis) -> comando existe -> remitente/rol -> permiso -> validacion de tipos -> ejecucion.
 */
public final class ProcesadorComandos {

    public static final Registro REGISTRO = construirRegistro();

    /** Resultado del procesamiento, con datos extra para el log de acceso. */
    public record Salida(Respuesta respuesta, String comando, String rol) {}

    private ProcesadorComandos() {}

    private static Registro construirRegistro() {
        Registro r = new Registro();
        AyudaHandler.registrar(r);
        UsuarioHandler.registrar(r);          // CU1
        ComunidadHandler.registrar(r);        // CU2
        EmprendimientoHandler.registrar(r);
        ProduccionHandler.registrar(r);       // CU3
        LoteHandler.registrar(r);
        CategoriaHandler.registrar(r);        // CU4
        ProductoHandler.registrar(r);
        InventarioHandler.registrar(r);
        CarritoHandler.registrar(r);          // CU5
        EncargoHandler.registrar(r);
        PedidoHandler.registrar(r);           // CU6
        PagoHandler.registrar(r);
        EntregaHandler.registrar(r);
        NotificacionHandler.registrar(r);
        IndicadorHandler.registrar(r);        // CU7
        ReporteHandler.registrar(r);          // CU8
        return r;
    }

    public static Salida procesar(String asunto, String remitente) {
        // 1. Sintaxis
        ParserAsunto.Resultado pr;
        try {
            pr = ParserAsunto.parsear(asunto);
        } catch (ParserAsunto.ErrorSintaxis e) {
            return new Salida(Respuesta.error(e.getMessage()
                    + "\n\nFormato correcto: COMANDO[\"param1\",\"param2\",...]\nEnvie HELP para ver sus comandos."),
                    "?", "?");
        }

        // 2. El comando existe
        ComandoSpec spec = REGISTRO.buscar(pr.comando());
        if (spec == null) {
            String sug = REGISTRO.sugerencia(pr.comando());
            return new Salida(Respuesta.error("Comando desconocido: " + pr.comando() + "."
                    + (sug != null ? " ¿Quiso decir " + sug + "?" : "")
                    + "\nEnvie HELP para ver los comandos disponibles para usted."), pr.comando(), "?");
        }

        try {
            return procesarConSesion(spec, pr, remitente);
        } finally {
            Db.terminarSesion();
        }
    }

    private static Salida procesarConSesion(ComandoSpec spec, ParserAsunto.Resultado pr, String remitente) {
        // 3. Remitente y permisos (de la BD). Una sola conexion para todo el comando.
        Contexto ctx;
        Map<String, boolean[]> permisos;
        try {
            Connection con = Db.iniciarSesion();
            ctx = Autorizacion.contextoDe(con, remitente);
            permisos = ctx.registrado ? Autorizacion.permisosDe(con, ctx.rolId) : Map.of();
        } catch (Exception e) {
            Log.error("No se pudo resolver el remitente " + remitente, e);
            return new Salida(Respuesta.errorBD("No se pudo verificar el remitente: " + e.getMessage()),
                    spec.nombre, "?");
        }
        String rol = ctx.registrado ? ctx.rol : "NO_REGISTRADO";

        if (!Autorizacion.puede(ctx, permisos, spec)) {
            return new Salida(Respuesta.error(mensajeNoAutorizado(ctx, spec)), spec.nombre, rol);
        }

        // 4. Validacion de cantidad y tipos de parametros
        Params params;
        try {
            params = spec.validar(pr.params());
        } catch (IllegalArgumentException e) {
            return new Salida(Respuesta.error("Parametros invalidos: " + e.getMessage()
                    + "\n\nUso:     " + spec.firma()
                    + "\nEjemplo: " + spec.ejemplo
                    + "\nDetalle: HELP[\"" + spec.nombre + "\"]"), spec.nombre, rol);
        }

        // 5. Ejecucion
        Notificador.iniciar();
        try {
            Respuesta r = spec.ejecutor.ejecutar(params, ctx);
            if (r.exito) Notificador.enviarPendientes();
            return new Salida(r, spec.nombre, rol);
        } catch (ErrorNegocio e) {
            return new Salida(Respuesta.error(e.getMessage()), spec.nombre, rol);
        } catch (SQLException e) {
            if (esErrorConexion(e)) {
                Log.error("Fallo de conexion con la BD en " + spec.nombre, e);
                return new Salida(Respuesta.errorBD("Fallo de conexion con la base de datos: " + e.getMessage()),
                        spec.nombre, rol);
            }
            Log.error("Error SQL en " + spec.nombre + " (" + remitente + ")", e);
            return new Salida(Respuesta.error(traducir(e)), spec.nombre, rol);
        } catch (Exception e) {
            Log.error("Error inesperado en " + spec.nombre + " (" + remitente + ")", e);
            return new Salida(Respuesta.error("Error interno al ejecutar " + spec.nombre + ": " + e.getMessage()),
                    spec.nombre, rol);
        } finally {
            Notificador.descartar();
        }
    }

    private static String mensajeNoAutorizado(Contexto ctx, ComandoSpec spec) {
        if (ctx.inactivo) {
            return "Su cuenta (" + ctx.email + ") esta INACTIVA. Comuniquese con FEDER para reactivarla.";
        }
        if (!ctx.registrado) {
            return "Usted no esta registrado en FEDER (" + ctx.email + ").\n"
                    + "Por ahora solo puede usar estos comandos:\n\n"
                    + "  HELP\n"
                    + "  REGCLI[\"ci\",\"nombre\",\"apellido\",\"telefono\",\"password\",\"direccion?\"]\n\n"
                    + "Ejemplo: REGCLI[\"7894561\",\"Ana\",\"Rojas\",\"71234567\",\"miClave123\",\"Av. Banzer 4to anillo\"]\n"
                    + "Una vez registrado como Cliente podra ver el catalogo, comprar y hacer encargos.";
        }
        if (spec.accion == Accion.REGISTRO) {
            return "Usted ya esta registrado como " + ctx.rol + ". Envie HELP para ver sus comandos.";
        }
        return "No autorizado: su rol (" + ctx.rol + ") no tiene permiso para "
                + spec.accion.name().toLowerCase() + " en el modulo '" + spec.modulo + "'.\n"
                + "Envie HELP para ver los comandos que puede usar.";
    }

    /** Reemplaza las contraseñas del asunto por **** (para logs y para el asunto de la respuesta). */
    public static String ocultarClaves(String asunto) {
        try {
            ParserAsunto.Resultado r = ParserAsunto.parsear(asunto);
            int idx = switch (r.comando()) {
                case "REGCLI" -> 4;
                case "INSUSU" -> 6;
                case "MODPRF" -> r.params().size() > 1 && r.params().get(0).equalsIgnoreCase("password") ? 1 : -1;
                case "MODUSU" -> r.params().size() > 2 && r.params().get(1).equalsIgnoreCase("password") ? 2 : -1;
                default -> -1;
            };
            if (idx < 0 || idx >= r.params().size()) return asunto;
            StringBuilder sb = new StringBuilder(r.comando()).append('[');
            for (int i = 0; i < r.params().size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(i == idx ? "****" : r.params().get(i).replace("\"", "\\\"")).append('"');
            }
            return sb.append(']').toString();
        } catch (ParserAsunto.ErrorSintaxis e) {
            return asunto;
        }
    }

    static boolean esErrorConexion(SQLException e) {
        String st = e.getSQLState();
        if (st != null && (st.startsWith("08") || st.equals("53300") || st.startsWith("57P"))) return true;
        for (Throwable c = e.getCause(); c != null; c = c.getCause()) {
            if (c instanceof IOException) return true;
        }
        return false;
    }

    /** Convierte un error de PostgreSQL en un mensaje entendible para el usuario. */
    static String traducir(SQLException e) {
        String detalle = null, restriccion = null, columna = null, mensaje = e.getMessage();
        if (e instanceof PSQLException pe && pe.getServerErrorMessage() != null) {
            ServerErrorMessage sm = pe.getServerErrorMessage();
            detalle = sm.getDetail();
            restriccion = sm.getConstraint();
            columna = sm.getColumn();
            mensaje = sm.getMessage();
        }
        String st = e.getSQLState() == null ? "" : e.getSQLState();
        return switch (st) {
            case "23505" -> "Ya existe un registro con ese dato (debe ser unico)."
                    + (detalle != null ? "\nDetalle: " + detalle : "");
            case "23503" -> "Referencia invalida: el registro relacionado no existe o esta siendo usado por otros datos."
                    + (detalle != null ? "\nDetalle: " + detalle : "");
            case "23514" -> "El valor no cumple una regla de la base de datos"
                    + (restriccion != null ? " (" + restriccion + ")" : "") + ".";
            case "23502" -> "Falta un dato obligatorio" + (columna != null ? ": " + columna : "") + ".";
            case "P0001" -> mensaje + (mensaje != null && mensaje.startsWith("Stock insuficiente")
                    ? "\nOtro cliente confirmo una compra de esa misma existencia hace un instante. Revise LISCAR y DISPRO." : "");
            default -> st.startsWith("22")
                    ? "Dato invalido: " + mensaje
                    : "Error de base de datos: " + mensaje;
        };
    }
}
