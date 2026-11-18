package feder.negocio;

/** Resultado de un comando: se envia por correo al remitente. */
public class Respuesta {
    public final boolean exito;
    public final String  mensaje;
    /** true = fallo la conexion con la BD: el correo NO se elimina para reintentarlo luego. */
    public final boolean errorBD;

    public Respuesta(boolean exito, String mensaje) {
        this(exito, mensaje, false);
    }

    private Respuesta(boolean exito, String mensaje, boolean errorBD) {
        this.exito   = exito;
        this.mensaje = mensaje;
        this.errorBD = errorBD;
    }

    public static Respuesta ok(String mensaje)    { return new Respuesta(true, mensaje); }
    public static Respuesta error(String mensaje) { return new Respuesta(false, mensaje); }
    public static Respuesta errorBD(String det)   { return new Respuesta(false, det, true); }
}
