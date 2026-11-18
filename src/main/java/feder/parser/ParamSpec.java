package feder.parser;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Definicion de un parametro: nombre, tipo y restricciones. Se valida en el servidor
 * ANTES de llegar al motor SQL (requisito de seguridad del enunciado).
 */
public final class ParamSpec {

    public enum Tipo { TEXTO, ENTERO, DECIMAL, DECIMAL_SIGNO, FECHA, FECHAHORA, EMAIL, CI, TELEFONO, OPCION }

    public final String   nombre;
    public final Tipo     tipo;
    public final boolean  obligatorio;
    public final boolean  admiteTodos;   // "*" significa "todos" (valor null)
    public final String[] opciones;
    public final int      maxLargo;

    private ParamSpec(String nombre, Tipo tipo, boolean obligatorio, boolean admiteTodos,
                      String[] opciones, int maxLargo) {
        this.nombre      = nombre;
        this.tipo        = tipo;
        this.obligatorio = obligatorio;
        this.admiteTodos = admiteTodos;
        this.opciones    = opciones;
        this.maxLargo    = maxLargo;
    }

    // ── Fabricas ──────────────────────────────────────────────────────────
    public static ParamSpec texto(String n)          { return new ParamSpec(n, Tipo.TEXTO, true, false, null, 200); }
    public static ParamSpec texto(String n, int max) { return new ParamSpec(n, Tipo.TEXTO, true, false, null, max); }
    public static ParamSpec entero(String n)         { return new ParamSpec(n, Tipo.ENTERO, true, false, null, 0); }
    public static ParamSpec decimal(String n)        { return new ParamSpec(n, Tipo.DECIMAL, true, false, null, 0); }
    public static ParamSpec decimalSigno(String n)   { return new ParamSpec(n, Tipo.DECIMAL_SIGNO, true, false, null, 0); }
    public static ParamSpec fecha(String n)          { return new ParamSpec(n, Tipo.FECHA, true, false, null, 0); }
    public static ParamSpec fechaHora(String n)      { return new ParamSpec(n, Tipo.FECHAHORA, true, false, null, 0); }
    public static ParamSpec email(String n)          { return new ParamSpec(n, Tipo.EMAIL, true, false, null, 120); }
    public static ParamSpec ci(String n)             { return new ParamSpec(n, Tipo.CI, true, false, null, 20); }
    public static ParamSpec telefono(String n)       { return new ParamSpec(n, Tipo.TELEFONO, true, false, null, 20); }
    public static ParamSpec opcion(String n, String... ops) { return new ParamSpec(n, Tipo.OPCION, true, false, ops, 0); }

    /** Parametro que puede omitirse o enviarse vacio "". */
    public ParamSpec opcional() { return new ParamSpec(nombre, tipo, false, admiteTodos, opciones, maxLargo); }

    /** Parametro de filtro: "*" (o vacio/omitido) significa todos. */
    public ParamSpec todos()    { return new ParamSpec(nombre, tipo, false, true, opciones, maxLargo); }

    /** Como se muestra en la ayuda. */
    public String firma() {
        String base = tipo == Tipo.OPCION ? String.join("|", opciones) : nombre;
        if (admiteTodos) return base + "|*";
        return obligatorio ? base : base + "?";
    }

    public String descripcionTipo() {
        return switch (tipo) {
            case TEXTO         -> "texto (max " + maxLargo + ")";
            case ENTERO        -> "entero positivo";
            case DECIMAL       -> "numero >= 0 (ej: 12.50)";
            case DECIMAL_SIGNO -> "numero, puede ser negativo (ej: -3.5)";
            case FECHA         -> "fecha AAAA-MM-DD";
            case FECHAHORA     -> "fecha y hora AAAA-MM-DD HH:MM";
            case EMAIL         -> "correo electronico";
            case CI            -> "CI/NIT (solo digitos, opcional complemento: 1234567-1A)";
            case TELEFONO      -> "telefono (7 a 15 digitos)";
            case OPCION        -> "una de: " + String.join(", ", opciones);
        };
    }

    // ── Validacion ────────────────────────────────────────────────────────
    private static final DateTimeFormatter FH = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FD = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Convierte y valida. Devuelve null si el valor esta vacio y es opcional. */
    public Object validar(String crudo) throws IllegalArgumentException {
        String v = crudo == null ? "" : crudo.strip();
        if (admiteTodos && (v.isEmpty() || v.equals("*"))) return null;
        if (v.isEmpty()) {
            if (obligatorio) throw new IllegalArgumentException("el parametro \"" + nombre + "\" es obligatorio");
            return null;
        }
        try {
            return switch (tipo) {
                case TEXTO -> {
                    if (v.length() > maxLargo) throw invalido(v, "maximo " + maxLargo + " caracteres");
                    yield v;
                }
                case ENTERO -> {
                    if (!v.matches("\\d{1,9}")) throw invalido(v, "debe ser un entero positivo");
                    int n = Integer.parseInt(v);
                    if (n <= 0) throw invalido(v, "debe ser mayor a 0");
                    yield n;
                }
                case DECIMAL, DECIMAL_SIGNO -> {
                    String d = v.replace(',', '.');
                    String patron = tipo == Tipo.DECIMAL ? "\\d{1,10}(\\.\\d{1,2})?" : "-?\\d{1,10}(\\.\\d{1,2})?";
                    if (!d.matches(patron)) throw invalido(v, descripcionTipo() + ", maximo 2 decimales");
                    yield new BigDecimal(d);
                }
                case FECHA -> {
                    try { yield LocalDate.parse(v); }
                    catch (DateTimeParseException e) { yield LocalDate.parse(v, FD); }
                }
                case FECHAHORA -> {
                    if (v.length() <= 10) {
                        yield LocalDate.parse(v).atStartOfDay();
                    }
                    yield LocalDateTime.parse(v.replace('T', ' '), FH);
                }
                case EMAIL -> {
                    if (v.length() > maxLargo || !v.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$"))
                        throw invalido(v, "correo invalido, ej: nombre@gmail.com");
                    yield v.toLowerCase(Locale.ROOT);
                }
                case CI -> {
                    String c = v.toUpperCase(Locale.ROOT);
                    if (!c.matches("\\d{4,12}(-[0-9A-Z]{1,3})?")) throw invalido(v, descripcionTipo());
                    yield c;
                }
                case TELEFONO -> {
                    String t = v.replace(" ", "");
                    if (!t.matches("\\+?\\d{7,15}")) throw invalido(v, descripcionTipo());
                    yield t;
                }
                case OPCION -> {
                    String norm = normalizar(v);
                    yield Arrays.stream(opciones).filter(o -> normalizar(o).equals(norm)).findFirst()
                            .orElseThrow(() -> invalido(v, "valores permitidos: " + String.join(", ", opciones)));
                }
            };
        } catch (DateTimeParseException e) {
            throw invalido(v, descripcionTipo());
        }
    }

    private IllegalArgumentException invalido(String v, String regla) {
        return new IllegalArgumentException("valor invalido para \"" + nombre + "\": \"" + v + "\" (" + regla + ")");
    }

    /** "En Preparación" -> "en_preparacion" (sin tildes, minusculas, espacios a _). */
    static String normalizar(String s) {
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).strip().replaceAll("[\\s-]+", "_");
    }
}
