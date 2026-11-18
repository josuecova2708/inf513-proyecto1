package feder.parser;

import feder.negocio.Respuesta;
import feder.seguridad.Contexto;

import java.util.List;

/**
 * Definicion completa de un comando. De este unico registro salen:
 * la validacion de parametros, la autorizacion (modulo + accion contra rol_permiso)
 * y el texto del HELP.
 */
public final class ComandoSpec {

    /** Accion sobre el modulo; corresponde a las columnas ver/crear/editar/eliminar de rol_permiso. */
    public enum Accion { VER, CREAR, EDITAR, ELIMINAR, PUBLICO, REGISTRO }

    @FunctionalInterface
    public interface Ejecutor {
        Respuesta ejecutar(Params p, Contexto ctx) throws Exception;
    }

    public final String          nombre;
    public final String          cu;
    public final String          modulo;
    public final Accion          accion;
    public final List<ParamSpec> params;
    public final String          descripcion;
    public final String          ejemplo;
    public final Ejecutor        ejecutor;

    public ComandoSpec(String nombre, String cu, String modulo, Accion accion, List<ParamSpec> params,
                       String descripcion, String ejemplo, Ejecutor ejecutor) {
        this.nombre      = nombre;
        this.cu          = cu;
        this.modulo      = modulo;
        this.accion      = accion;
        this.params      = params;
        this.descripcion = descripcion;
        this.ejemplo     = ejemplo;
        this.ejecutor    = ejecutor;
    }

    /** NOMBRE["p1","p2?"] */
    public String firma() {
        if (params.isEmpty()) return nombre;
        StringBuilder sb = new StringBuilder(nombre).append('[');
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(params.get(i).firma()).append('"');
        }
        return sb.append(']').toString();
    }

    /** Valida cantidad y tipo de los parametros recibidos. */
    public Params validar(List<String> crudos) throws IllegalArgumentException {
        long obligatorios = params.stream().filter(p -> p.obligatorio).count();
        if (crudos.size() > params.size()) {
            throw new IllegalArgumentException(nombre + " recibe como maximo " + params.size()
                    + " parametro(s) y llegaron " + crudos.size() + ".");
        }
        // El ultimo obligatorio marca el minimo de parametros a enviar.
        int minimo = 0;
        for (int i = 0; i < params.size(); i++) if (params.get(i).obligatorio) minimo = i + 1;
        if (crudos.size() < minimo) {
            throw new IllegalArgumentException(nombre + " requiere " + obligatorios
                    + " parametro(s) obligatorio(s) y llegaron " + crudos.size() + ".");
        }
        Object[] valores = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            String crudo = i < crudos.size() ? crudos.get(i) : null;
            valores[i] = params.get(i).validar(crudo);
        }
        return new Params(valores);
    }
}
