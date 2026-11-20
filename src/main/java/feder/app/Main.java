package feder.app;

import feder.infra.Config;
import feder.infra.ConexionDB;
import feder.infra.Log;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Punto de entrada.
 *   -DMODE=SERVER  (en tecnoweb, todo por localhost)   ciclo infinito de lectura de correo
 *   -DMODE=DEV     (desde la PC)                        ciclo infinito de lectura de correo
 *   -DMODE=TEST    ejecuta comandos SIN correo:
 *        java -DMODE=TEST -jar FederCorreo-1.0.jar remitente@x.com "LISPRO[\"*\"]"
 *        java -DMODE=TEST -jar FederCorreo-1.0.jar -f pruebas.txt      (lineas: remitente | asunto)
 */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("FederCorreo (Grupo 28) - MODE: " + Config.MODE);
        if ("TEST".equals(Config.MODE)) {
            modoTest(args);
            return;
        }
        System.out.println(ConexionDB.estaDisponible() ? "BD: OK" : "BD: NO DISPONIBLE (se reintentara en cada ciclo)");
        System.out.println("Comandos registrados: " + ProcesadorComandos.REGISTRO.todos().size());
        int espera = Config.getInt("ciclo.segundos", 10) * 1000;
        boolean mostrarCiclos = !"false".equalsIgnoreCase(Config.get("consola.ciclos", "true"));
        System.out.println("Revisando la bandeja cada " + espera / 1000 + " s. Ctrl+C para detener.");
        long n = 0;

        while (true) {
            // Blindaje: pase lo que pase en un ciclo, se registra y el bucle CONTINUA.
            long t0 = System.currentTimeMillis();
            CorreoService.Ciclo c = null;
            try {
                c = CorreoService.procesar();
                if (c.bd()) Mantenimiento.ejecutar();   // sin BD no tiene sentido (ya se registro [BD])
            } catch (Throwable t) {
                Log.error("Error no controlado en el ciclo; se continua", t);
            }
            n++;
            if (mostrarCiclos || (c != null && c.bandeja() > 0)) {
                Log.info(resumen(n, c, System.currentTimeMillis() - t0));
            }
            try {
                Thread.sleep(espera);
            } catch (InterruptedException e) {
                Log.info("Ciclo interrumpido, saliendo.");
                break;
            }
        }
    }

    /** Ej: "[Ciclo #42] BD OK | POP3 OK | bandeja 2 -> 2 respondidos | 2310 ms" */
    private static String resumen(long n, CorreoService.Ciclo c, long ms) {
        StringBuilder sb = new StringBuilder("[Ciclo #").append(n).append("] ");
        if (c == null) return sb.append("ERROR no controlado (ver logs/error.log) | ").append(ms).append(" ms").toString();
        if (!c.bd()) return sb.append("BD FALLO | POP3 - | ciclo saltado, los correos esperan | ").append(ms).append(" ms").toString();
        sb.append("BD OK | POP3 ").append(Boolean.TRUE.equals(c.pop3()) ? "OK" : "FALLO");
        sb.append(" | bandeja ").append(c.bandeja());
        if (c.bandeja() > 0) {
            StringBuilder d = new StringBuilder();
            if (c.respondidos()  > 0) d.append(", ").append(c.respondidos()).append(" respondidos");
            if (c.sinRespuesta() > 0) d.append(", ").append(c.sinRespuesta()).append(" sin respuesta (SMTP)");
            if (c.pendientes()   > 0) d.append(", ").append(c.pendientes()).append(" pendientes (BD)");
            if (c.ignorados()    > 0) d.append(", ").append(c.ignorados()).append(" ignorados");
            if (c.errores()      > 0) d.append(", ").append(c.errores()).append(" con error");
            if (d.length() > 0) sb.append(" -> ").append(d.substring(2));
        }
        return sb.append(" | ").append(ms).append(" ms").toString();
    }

    private static void modoTest(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("-f")) {
            List<String> lineas = Files.readAllLines(Paths.get(args[1]), StandardCharsets.UTF_8);
            int n = 0;
            for (String linea : lineas) {
                if (linea.isBlank() || linea.strip().startsWith("#")) continue;
                int sep = linea.indexOf('|');
                if (sep < 0) { System.out.println("Linea ignorada (falta '|'): " + linea); continue; }
                ejecutar(linea.substring(0, sep).strip(), linea.substring(sep + 1).strip(), ++n);
            }
            Mantenimiento.ejecutar();
        } else if (args.length == 2) {
            ejecutar(args[0], args[1], 1);
        } else {
            System.out.println("Uso: remitente \"ASUNTO\"   |   -f archivo.txt");
        }
    }

    private static void ejecutar(String remitente, String asunto, int n) {
        long t0 = System.currentTimeMillis();
        ProcesadorComandos.Salida s = ProcesadorComandos.procesar(asunto, remitente);
        long ms = System.currentTimeMillis() - t0;
        String seguro = ProcesadorComandos.ocultarClaves(asunto);
        System.out.println("\n#" + n + " ==== " + remitente + " | " + seguro);
        System.out.println(CorreoService.cuerpo(seguro, s.respuesta()));
        if (s.respuesta().imagen != null)
            System.out.println("[imagen adjunta: " + s.respuesta().imagen.nombre() + ", " + s.respuesta().imagen.kb() + " KB]");
        Log.acceso(remitente, seguro, s.rol(), s.respuesta().exito, "[TEST]", ms);
    }
}
