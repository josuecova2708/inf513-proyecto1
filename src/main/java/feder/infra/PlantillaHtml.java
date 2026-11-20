package feder.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version HTML (con diseño) de los correos. Solo PRESENTACION: recibe el mismo texto plano que ya
 * generan los comandos y lo convierte; no toca la logica de negocio.
 *   - Las tablas ASCII de TablaFormato (+----+ / | a | b |) se convierten en <table> reales.
 *   - "== Titulo ==" y lineas en MAYUSCULAS se muestran como subtitulos.
 *   - "Clave: valor" se muestra con la clave en negrita.
 * Todo el CSS va inline porque Gmail y otros clientes ignoran los bloques <style>.
 */
public final class PlantillaHtml {

    private PlantillaHtml() {}

    // Paleta
    private static final String VERDE      = "#2e7d32";
    private static final String VERDE_OSC  = "#1b5e20";
    private static final String FONDO      = "#f3f5f2";
    private static final String BORDE      = "#dfe5dc";
    private static final String TEXTO      = "#1f2a1f";
    private static final String TENUE      = "#6b7a6b";
    private static final String FUENTE     = "font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;";
    private static final String MONO       = "font-family:Consolas,'Courier New',monospace;";

    private static final Pattern SEPARADOR = Pattern.compile("^\\+(-+\\+)+$");
    private static final Pattern NUMERO    = Pattern.compile("^-?(Bs\\s*)?\\d[\\d.,]*%?$");
    private static final Pattern TITULO_EQ = Pattern.compile("^==+\\s*(.+?)\\s*==+$");
    private static final Pattern CLAVE     = Pattern.compile("^([\\p{L}¿?()/ ]{2,28}):\\s+(\\S.*)$");

    /** Respuesta a un comando enviado por correo. */
    public static String respuesta(String comando, boolean exito, String mensaje) {
        String insignia = exito
                ? insignia("EXITO", "#e8f5e9", VERDE_OSC)
                : insignia("ERROR", "#fdecea", "#b71c1c");
        String cuerpo = exito
                ? cuerpo(mensaje, false)
                : "<div style=\"background:#fdecea;border-left:4px solid #c62828;padding:12px 14px;border-radius:6px;"
                  + "color:#7f1d1d;\">" + cuerpo(mensaje, false) + "</div>";
        String cabecera = "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
                + "<td style=\"" + FUENTE + "font-size:12px;color:" + TENUE + ";padding-bottom:4px;\">Comando</td>"
                + "<td align=\"right\" rowspan=\"2\" style=\"vertical-align:middle;\">" + insignia + "</td></tr>"
                + "<tr><td><span style=\"" + MONO + "font-size:14px;background:" + FONDO + ";border:1px solid " + BORDE
                + ";border-radius:4px;padding:3px 8px;color:" + TEXTO + ";word-break:break-all;\">" + esc(comando)
                + "</span></td></tr></table>"
                + "<div style=\"height:1px;background:" + BORDE + ";margin:16px 0;\"></div>";
        return documento(cabecera + cuerpo, "Respuesta automatica de " + ConexionCorreo.CORREO
                + " &nbsp;·&nbsp; Envie <b>HELP</b> para ver sus comandos.");
    }

    /** Aviso automatico (notificaciones, alertas, cancelaciones). */
    public static String aviso(String titulo, String mensaje) {
        String t = titulo == null ? "" : titulo.replaceFirst("^(?i)FEDER\\s*-\\s*", "");
        String cabecera = "<div style=\"" + FUENTE + "font-size:18px;font-weight:600;color:" + VERDE_OSC
                + ";margin-bottom:14px;\">" + esc(t) + "</div>";
        return documento(cabecera + cuerpo(mensaje, true), "Aviso automatico de " + ConexionCorreo.CORREO
                + " &nbsp;·&nbsp; Envie <b>HELP</b> para ver sus comandos.");
    }

    /** Version de respaldo (la que se usaba antes): texto monoespaciado. */
    public static String preformateado(String texto) {
        return "<html><body><pre style=\"" + MONO + "font-size:13px\">" + esc(texto) + "</pre></body></html>";
    }

    // ------------------------------------------------------------------ estructura

    private static String documento(String contenido, String pie) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>"
                + "<body style=\"margin:0;padding:0;background:" + FONDO + ";\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:" + FONDO + ";\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:680px;table-layout:fixed;"
                + "background:#ffffff;border:1px solid " + BORDE + ";border-radius:10px;overflow:hidden;\">"
                // Banda superior
                + "<tr><td style=\"background:" + VERDE + ";padding:18px 24px;\">"
                + "<div style=\"" + FUENTE + "font-size:20px;font-weight:700;color:#ffffff;letter-spacing:.5px;\">FEDER</div>"
                + "<div style=\"" + FUENTE + "font-size:13px;color:#c8e6c9;margin-top:2px;\">"
                + "Comercio de productos agricolas &nbsp;·&nbsp; Grupo 28</div></td></tr>"
                // Contenido
                + "<tr><td style=\"padding:22px 24px;" + FUENTE + "font-size:14px;line-height:1.5;color:" + TEXTO + ";\">"
                + contenido + "</td></tr>"
                // Pie
                + "<tr><td style=\"background:#fafbf9;border-top:1px solid " + BORDE + ";padding:12px 24px;"
                + FUENTE + "font-size:12px;color:" + TENUE + ";\">" + pie + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String insignia(String texto, String fondo, String color) {
        return "<span style=\"" + FUENTE + "display:inline-block;background:" + fondo + ";color:" + color
                + ";font-size:12px;font-weight:700;letter-spacing:.6px;padding:5px 12px;border-radius:999px;\">"
                + texto + "</span>";
    }

    // ------------------------------------------------------------------ cuerpo

    /**
     * Convierte el texto plano en HTML.
     * @param esAviso si es true se quitan la cabecera "=== FEDER ===" y el pie "---" que ya trae el texto.
     */
    static String cuerpo(String texto, boolean esAviso) {
        if (texto == null || texto.isBlank()) return "";
        String[] lineas = texto.replace("\r", "").split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < lineas.length) {
            String l = lineas[i];
            String s = l.strip();
            if (esAviso && s.equals("---")) break;                       // pie del texto plano
            if (s.matches("^===.*===$")) { i++; continue; }             // cabecera del texto plano

            if (SEPARADOR.matcher(s).matches() || (s.startsWith("|") && s.endsWith("|") && s.length() > 2)) {
                List<String> bloque = new ArrayList<>();
                while (i < lineas.length) {
                    String t = lineas[i].strip();
                    if (!(SEPARADOR.matcher(t).matches() || (t.startsWith("|") && t.endsWith("|")))) break;
                    bloque.add(t);
                    i++;
                }
                sb.append(tabla(bloque));
                continue;
            }
            sb.append(linea(l));
            i++;
        }
        return sb.toString();
    }

    private static String linea(String l) {
        String s = l.strip();
        if (s.isEmpty()) return "<div style=\"height:10px;\"></div>";

        Matcher m = TITULO_EQ.matcher(s);
        if (m.matches()) return subtitulo(m.group(1));

        int sangria = l.length() - l.stripLeading().length();
        if (sangria == 0 && esMayusculas(s)) return subtitulo(s);
        if (sangria >= 6) {   // descripcion bajo un comando (HELP)
            return "<div style=\"color:" + TENUE + ";font-size:13px;padding:0 0 6px 18px;\">" + esc(s) + "</div>";
        }
        if (sangria >= 2) {   // firma de comando / item de lista
            return "<div style=\"padding:2px 0 0 4px;\"><span style=\"" + MONO + "font-size:13px;color:" + VERDE_OSC
                    + ";background:#f1f8f1;border-radius:3px;padding:1px 6px;white-space:pre-wrap;word-break:break-all;\">" + esc(s) + "</span></div>";
        }
        Matcher c = CLAVE.matcher(s);
        if (c.matches()) {
            return "<div style=\"padding:1px 0;\"><b style=\"color:" + TEXTO + ";\">" + esc(c.group(1).strip())
                    + ":</b> " + esc(c.group(2)) + "</div>";
        }
        return "<div style=\"padding:1px 0;white-space:pre-wrap;word-break:break-word;\">" + esc(s) + "</div>";
    }

    private static String subtitulo(String s) {
        return "<div style=\"font-size:13px;font-weight:700;color:" + VERDE + ";text-transform:uppercase;"
                + "letter-spacing:.6px;margin:14px 0 6px;padding-bottom:4px;border-bottom:2px solid #e3efe3;\">"
                + esc(s) + "</div>";
    }

    /** Linea corta, sin minusculas y con al menos 3 letras (p. ej. "MI CARRITO"). */
    private static boolean esMayusculas(String s) {
        if (s.length() > 60 || s.contains("[") || s.contains("|")) return false;
        int letras = 0;
        for (char ch : s.toCharArray()) {
            if (Character.isLowerCase(ch)) return false;
            if (Character.isLetter(ch)) letras++;
        }
        return letras >= 3 && s.contains(" ") || letras >= 6;
    }

    // ------------------------------------------------------------------ tablas

    /**
     * Bloque de TablaFormato: separador, cabecera, separador, filas..., separador.
     * Las columnas se cortan por las posiciones de los '+' del separador (robusto si una celda trae '|').
     */
    private static String tabla(List<String> bloque) {
        String sep = null;
        for (String b : bloque) if (SEPARADOR.matcher(b).matches()) { sep = b; break; }
        List<Integer> cortes = new ArrayList<>();
        if (sep != null) for (int k = 0; k < sep.length(); k++) if (sep.charAt(k) == '+') cortes.add(k);

        List<String[]> filas = new ArrayList<>();
        int indiceCabecera = -1;
        for (int k = 0; k < bloque.size(); k++) {
            String b = bloque.get(k);
            if (SEPARADOR.matcher(b).matches()) continue;
            String[] celdas = celdas(b, cortes);
            // Cabecera = primera fila entre dos separadores
            if (indiceCabecera < 0 && filas.isEmpty() && k > 0 && k + 1 < bloque.size()
                    && SEPARADOR.matcher(bloque.get(k - 1)).matches() && SEPARADOR.matcher(bloque.get(k + 1)).matches()) {
                indiceCabecera = 0;
            }
            filas.add(celdas);
        }
        if (filas.isEmpty()) return "";

        int columnas = filas.get(0).length;
        boolean compacta = columnas > 6;   // tablas anchas: letra y relleno menores para que entren
        String pad = compacta ? "6px 5px" : "7px 8px";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"overflow-x:auto;margin:6px 0 10px;\">")
          .append("<table cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%;")
          .append("font-size:").append(compacta ? "12px" : "13px").append(";border:1px solid ").append(BORDE).append(";\">");
        for (int r = 0; r < filas.size(); r++) {
            String[] f = filas.get(r);
            boolean cab = r == indiceCabecera;
            String fondoFila = cab ? VERDE : (r % 2 == 0 ? "#ffffff" : "#f6f9f5");
            sb.append("<tr style=\"background:").append(fondoFila).append(";\">");
            for (String c : f) {
                boolean num = !cab && NUMERO.matcher(c).matches();
                String tag = cab ? "th" : "td";
                sb.append('<').append(tag).append(" align=\"").append(num ? "right" : "left").append("\" style=\"")
                  .append("padding:").append(pad).append(";border-bottom:1px solid ").append(BORDE).append(';')
                  .append(cab ? "color:#ffffff;font-weight:600;font-size:" + (compacta ? "11px" : "12px") + ";white-space:nowrap;"
                              : "color:" + TEXTO + ";")
                  .append(num ? MONO + "white-space:nowrap;" : "")
                  .append("\">").append(c.isEmpty() ? "&nbsp;" : esc(c)).append("</").append(tag).append('>');
            }
            sb.append("</tr>");
        }
        sb.append("</table></div>");
        return sb.toString();
    }

    private static String[] celdas(String fila, List<Integer> cortes) {
        if (cortes.size() >= 2 && fila.length() == cortes.get(cortes.size() - 1) + 1) {
            String[] r = new String[cortes.size() - 1];
            for (int k = 0; k + 1 < cortes.size(); k++) r[k] = fila.substring(cortes.get(k) + 1, cortes.get(k + 1)).strip();
            return r;
        }
        String interior = fila.substring(1, fila.length() - 1);   // respaldo: cortar por '|'
        String[] partes = interior.split("\\|", -1);
        for (int k = 0; k < partes.length; k++) partes[k] = partes[k].strip();
        return partes;
    }

    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
