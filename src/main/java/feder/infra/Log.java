package feder.infra;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs de auditoria exigidos por el enunciado:
 *   logs/access.log -> un registro por correo procesado (remitente, comando, resultado, duracion)
 *   logs/error.log  -> excepciones con su traza, etiquetadas por origen: [BD] [POP3] [SMTP] [COMANDO] ...
 * Tambien se imprime en consola (que run.sh redirige a feder.log).
 */
public final class Log {

    /** Origen del error: permite saber de un vistazo si fallo la BD, la lectura POP3, el envio SMTP, etc. */
    public enum Tipo { BD, POP3, SMTP, COMANDO, MANTENIMIENTO, OTRO }

    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path DIR = Paths.get(Config.get("log.dir", "logs"));

    private Log() {}

    public static synchronized void acceso(String remitente, String asunto, String rol,
                                           boolean exito, String resumen, long ms) {
        String linea = String.format("%s | %s | rol=%s | %s | %s | %dms | %s",
                ahora(), remitente, rol, exito ? "EXITO" : "ERROR", limpiar(asunto), ms, limpiar(resumen));
        escribir("access.log", linea);
        System.out.println("  [ACCESS] " + linea);
    }

    /** Error sin origen especifico (equivale a Tipo.OTRO). */
    public static void error(String contexto, Throwable t) {
        error(Tipo.OTRO, contexto, t);
    }

    public static synchronized void error(Tipo tipo, String contexto, Throwable t) {
        String etiqueta = "[" + tipo + "] ";
        StringBuilder sb = new StringBuilder(ahora()).append(" | ").append(etiqueta).append(limpiar(contexto));
        if (t != null) {
            sb.append(" -> ").append(limpiar(causa(t)));
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(System.lineSeparator()).append(sw);
        }
        escribir("error.log", sb.toString());
        System.out.println(ahora() + "   [ERROR]" + etiqueta + contexto + (t != null ? " -> " + causa(t) : ""));
    }

    /** Mensaje legible de la causa raiz (credenciales, sin conexion, timeout...). */
    public static String causa(Throwable t) {
        if (t == null) return "";
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) raiz = raiz.getCause();
        String msg = raiz.getMessage() == null ? "" : raiz.getMessage().strip();
        String nombre = raiz.getClass().getSimpleName();
        String m = msg.toLowerCase();
        // jakarta.mail reporta como AuthenticationFailedException tambien los timeouts/rechazos de conexion
        if (m.contains("timed out"))          return "tiempo de espera agotado: el servidor no respondio (" + msg + ")";
        if (m.contains("connection refused")) return "sin conexion: puerto cerrado o servicio caido (" + msg + ")";
        if (m.contains("connection reset"))   return "la conexion fue cortada por el servidor (" + msg + ")";
        String pista = switch (nombre) {
            case "AuthenticationFailedException" -> "usuario/clave rechazados";
            case "ConnectException"              -> "sin conexion (servidor caido o puerto cerrado)";
            case "SocketTimeoutException"        -> "tiempo de espera agotado";
            case "UnknownHostException"          -> "no se pudo resolver el host (DNS)";
            case "NoRouteToHostException"        -> "sin ruta al host (red/VPN)";
            default -> null;
        };
        if (pista == null) return nombre + (msg.isEmpty() ? "" : ": " + msg);
        return pista + (msg.isEmpty() ? "" : " (" + msg + ")");
    }

    public static void info(String msg) {
        System.out.println(ahora() + " " + msg);
    }

    private static String ahora() { return LocalDateTime.now().format(F); }

    private static String limpiar(String s) {
        if (s == null) return "";
        String r = s.replaceAll("[\\r\\n]+", " ");
        return r.length() > 300 ? r.substring(0, 300) + "..." : r;
    }

    private static void escribir(String archivo, String linea) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(archivo), linea + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("No se pudo escribir " + archivo + ": " + e.getMessage());
        }
    }
}
