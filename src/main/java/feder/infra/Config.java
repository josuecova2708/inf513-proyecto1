package feder.infra;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/**
 * Lee config.properties. Orden de busqueda:
 *   1. -Dconfig=/ruta/config.properties
 *   2. ./config.properties (directorio actual)
 *   3. config.properties junto al jar
 *   4. $HOME/config.properties
 * MODE (DEV | SERVER | TEST) se pasa con -DMODE=...; en SERVER los hosts son localhost.
 */
public final class Config {

    public static final String MODE = System.getProperty("MODE", "DEV").toUpperCase();
    private static final Properties P = cargar();

    private Config() {}

    private static Properties cargar() {
        Properties p = new Properties();
        for (Path ruta : candidatos()) {
            if (ruta != null && Files.isRegularFile(ruta)) {
                try (Reader r = Files.newBufferedReader(ruta, StandardCharsets.UTF_8)) {
                    p.load(r);
                    System.out.println("Config: " + ruta.toAbsolutePath());
                    return p;
                } catch (IOException e) {
                    System.out.println("No se pudo leer " + ruta + ": " + e.getMessage());
                }
            }
        }
        System.out.println("ADVERTENCIA: no se encontro config.properties; se usan valores por defecto.");
        return p;
    }

    private static Path[] candidatos() {
        Path jarDir = null;
        try {
            jarDir = Paths.get(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                         .getParent().resolve("config.properties");
        } catch (Exception ignored) {}
        String explicito = System.getProperty("config");
        return new Path[] {
            explicito != null ? Paths.get(explicito) : null,
            Paths.get("config.properties"),
            jarDir,
            Paths.get(System.getProperty("user.home"), "config.properties")
        };
    }

    public static String get(String clave, String porDefecto) {
        return P.getProperty(clave, porDefecto).trim();
    }

    public static int getInt(String clave, int porDefecto) {
        try { return Integer.parseInt(get(clave, String.valueOf(porDefecto))); }
        catch (NumberFormatException e) { return porDefecto; }
    }

    /** En modo SERVER el programa corre dentro del servidor tecnoweb: todo va por localhost. */
    public static String host(String clave, String porDefecto) {
        return "SERVER".equals(MODE) ? "localhost" : get(clave, porDefecto);
    }
}
