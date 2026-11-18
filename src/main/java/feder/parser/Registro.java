package feder.parser;

import feder.parser.ComandoSpec.Accion;
import feder.parser.ComandoSpec.Ejecutor;

import java.util.*;

/** Catalogo de todos los comandos del sistema, en el orden en que se registran. */
public final class Registro {

    private final Map<String, ComandoSpec> comandos = new LinkedHashMap<>();

    public void cmd(String nombre, String cu, String modulo, Accion accion, String descripcion,
                    String ejemplo, Ejecutor ejecutor, ParamSpec... params) {
        if (comandos.containsKey(nombre)) {
            throw new IllegalStateException("Comando duplicado: " + nombre);
        }
        comandos.put(nombre, new ComandoSpec(nombre, cu, modulo, accion, List.of(params),
                descripcion, ejemplo, ejecutor));
    }

    public ComandoSpec buscar(String nombre) {
        return comandos.get(nombre);
    }

    public Collection<ComandoSpec> todos() {
        return comandos.values();
    }

    /** Comando conocido mas parecido (para sugerir ante un error de tipeo). */
    public String sugerencia(String nombre) {
        String mejor = null;
        int dist = Integer.MAX_VALUE;
        for (String c : comandos.keySet()) {
            int d = levenshtein(nombre, c);
            if (d < dist) { dist = d; mejor = c; }
        }
        return dist <= 2 ? mejor : null;
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
