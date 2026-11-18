package feder.infra;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Logs de auditoria exigidos por el enunciado:
 *   logs/access.log -> un registro por correo procesado (remitente, comando, resultado, duracion)
 *   logs/error.log  -> excepciones con su traza
 * Tambien se imprime en consola (que run.sh redirige a feder.log).
 */
public final class Log {

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

    public static synchronized void error(String contexto, Throwable t) {
        StringBuilder sb = new StringBuilder(ahora()).append(" | ").append(limpiar(contexto));
        if (t != null) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            sb.append(System.lineSeparator()).append(sw);
        }
        escribir("error.log", sb.toString());
        System.out.println("  [ERROR] " + contexto + (t != null ? " -> " + t : ""));
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
