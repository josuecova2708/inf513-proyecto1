package feder.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Parametros ya validados y convertidos a su tipo Java. */
public final class Params {

    private final Object[] v;

    public Params(Object[] valores) {
        this.v = valores;
    }

    public boolean tiene(int i)          { return i < v.length && v[i] != null; }
    public Object  obj(int i)            { return i < v.length ? v[i] : null; }
    public String  str(int i)            { return (String) obj(i); }
    public Integer entero(int i)         { return (Integer) obj(i); }
    public BigDecimal dec(int i)         { return (BigDecimal) obj(i); }
    public LocalDate fecha(int i)        { return (LocalDate) obj(i); }
    public LocalDateTime fechaHora(int i){ return (LocalDateTime) obj(i); }

    public String str(int i, String porDefecto) {
        return tiene(i) ? str(i) : porDefecto;
    }
}
