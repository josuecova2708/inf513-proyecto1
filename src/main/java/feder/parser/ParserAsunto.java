package feder.parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Analizador lexico del Asunto. Gramatica aceptada:
 *
 *   asunto    := prefijo* COMANDO ( '[' params? ']' )?
 *   prefijo   := ("RE" | "RV" | "FW" | "FWD" | "AW") ':'
 *   COMANDO   := letra+                      (mayusculas o minusculas)
 *   params    := param ( ',' param )*
 *   param     := '"' ( caracter | '\"' | '\\' )* '"'
 *
 * Tolera espacios en blanco en cualquier punto entre tokens, comillas "tipograficas"
 * que agregan algunos clientes de correo (“ ” „) y parametros vacios "".
 * Ante un error indica la posicion y lo que se esperaba.
 */
public final class ParserAsunto {

    public record Resultado(String comando, List<String> params) {}

    public static class ErrorSintaxis extends Exception {
        public ErrorSintaxis(String msg) { super(msg); }
    }

    private final String s;
    private int pos;

    private ParserAsunto(String s) {
        this.s = s;
    }

    public static Resultado parsear(String asunto) throws ErrorSintaxis {
        if (asunto == null || asunto.isBlank()) {
            throw new ErrorSintaxis("El asunto esta vacio. Escriba un comando, por ejemplo: HELP");
        }
        String normalizado = asunto
                .replace('“', '"').replace('”', '"').replace('„', '"')  // “ ” „
                .replace(' ', ' ')                                               // espacio duro
                .strip();
        normalizado = quitarPrefijos(normalizado);
        return new ParserAsunto(normalizado).leer();
    }

    private static String quitarPrefijos(String t) {
        String r = t;
        while (true) {
            String sig = r.replaceFirst("(?i)^(re|rv|fw|fwd|aw)\\s*:\\s*", "");
            if (sig.equals(r)) return r;
            r = sig;
        }
    }

    private Resultado leer() throws ErrorSintaxis {
        espacios();
        int ini = pos;
        while (pos < s.length() && Character.isLetter(s.charAt(pos))) pos++;
        if (pos == ini) {
            throw error("se esperaba el nombre del comando (solo letras), ej: LISPRO[\"*\"]");
        }
        String comando = s.substring(ini, pos).toUpperCase();
        espacios();

        List<String> params = new ArrayList<>();
        if (pos == s.length()) {
            return new Resultado(comando, params);          // comando sin corchetes, ej: HELP
        }
        if (s.charAt(pos) != '[') {
            throw error("se esperaba '[' despues de " + comando);
        }
        pos++;
        espacios();
        if (pos < s.length() && s.charAt(pos) == ']') {      // COMANDO[]
            pos++;
        } else {
            while (true) {
                params.add(leerCadena());
                espacios();
                if (pos >= s.length()) {
                    throw error("falta el ']' de cierre");
                }
                char c = s.charAt(pos);
                if (c == ',') { pos++; espacios(); continue; }
                if (c == ']') { pos++; break; }
                throw error("se esperaba ',' o ']' pero se encontro '" + c + "'");
            }
        }
        espacios();
        if (pos != s.length()) {
            throw error("hay texto sobrante despues de ']': \"" + s.substring(pos) + "\"");
        }
        return new Resultado(comando, params);
    }

    private String leerCadena() throws ErrorSintaxis {
        if (pos >= s.length() || s.charAt(pos) != '"') {
            throw error("cada parametro debe ir entre comillas dobles, ej: \"valor\"");
        }
        int inicio = pos;
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '\\' && pos + 1 < s.length()
                    && (s.charAt(pos + 1) == '"' || s.charAt(pos + 1) == '\\')) {
                sb.append(s.charAt(pos + 1));               // \"  ->  "     \\  ->  \
                pos += 2;
                continue;
            }
            if (c == '"') {
                pos++;
                return sb.toString().strip();
            }
            sb.append(c);
            pos++;
        }
        pos = inicio;
        throw error("comilla sin cerrar en el parametro que empieza aqui");
    }

    private void espacios() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
    }

    private ErrorSintaxis error(String detalle) {
        String marca = s + "\n" + " ".repeat(Math.min(pos, s.length())) + "^";
        return new ErrorSintaxis("Error de sintaxis en la posicion " + (pos + 1) + ": " + detalle
                + "\n\n" + marca);
    }
}
