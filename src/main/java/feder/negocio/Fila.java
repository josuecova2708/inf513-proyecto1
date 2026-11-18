package feder.negocio;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** Una fila de resultado con getters tipados (columnas por nombre, en minusculas). */
public class Fila extends LinkedHashMap<String, Object> {

    public String str(String col) {
        Object v = get(col);
        return v == null ? null : v.toString();
    }

    public Integer entero(String col) {
        Object v = get(col);
        return v == null ? null : ((Number) v).intValue();
    }

    public BigDecimal dec(String col) {
        Object v = get(col);
        if (v == null) return null;
        return v instanceof BigDecimal b ? b : new BigDecimal(v.toString());
    }

    public boolean bool(String col) {
        Object v = get(col);
        return v != null && (Boolean) v;
    }
}
