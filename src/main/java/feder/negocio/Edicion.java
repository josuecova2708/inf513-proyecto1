package feder.negocio;

import feder.parser.ParamSpec;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Soporte para los comandos MODxxx["id","campo","valor"]: lista blanca de campos editables,
 * cada uno con su columna real y su regla de validacion. El nombre de la columna NUNCA viene
 * del usuario (sale de esta lista), y el valor va como parametro enlazado.
 */
public final class Edicion {

    private record Campo(String columna, ParamSpec spec) {}

    private final String tabla;
    private final Map<String, Campo> campos = new LinkedHashMap<>();

    public Edicion(String tabla) {
        this.tabla = tabla;
    }

    public Edicion campo(String nombre, String columna, ParamSpec spec) {
        campos.put(nombre, new Campo(columna, spec));
        return this;
    }

    public Edicion campo(String nombre, ParamSpec spec) {
        return campo(nombre, nombre, spec);
    }

    public String[] nombres() {
        return campos.keySet().toArray(String[]::new);
    }

    /** Valida el valor segun el campo (lanza ErrorNegocio si no corresponde). */
    public Object convertir(String campo, String valor) {
        Campo c = campos.get(campo);
        if (c == null) throw new ErrorNegocio("Campo no editable: " + campo
                + ". Campos permitidos: " + String.join(", ", nombres()));
        try {
            return c.spec.validar(valor);
        } catch (IllegalArgumentException e) {
            throw new ErrorNegocio("Valor invalido: " + e.getMessage());
        }
    }

    /** UPDATE tabla SET columna = ? WHERE id = ?   (valor ya convertido) */
    public void aplicar(Connection con, int id, String campo, Object valor) throws Exception {
        Campo c = campos.get(campo);
        int n = Db.update(con, "UPDATE " + tabla + " SET " + c.columna + " = ? WHERE id = ?", valor, id);
        if (n == 0) throw new ErrorNegocio("No existe el registro con id " + id + " en " + tabla + ".");
    }
}
