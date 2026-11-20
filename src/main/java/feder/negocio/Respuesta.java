package feder.negocio;

import feder.infra.Adjuntos;

/** Resultado de un comando: se envia por correo al remitente. */
public class Respuesta {
    public final boolean exito;
    public final String  mensaje;
    /** true = fallo la conexion con la BD: el correo NO se elimina para reintentarlo luego. */
    public final boolean errorBD;
    /** Imagen que acompaña la respuesta (p. ej. el QR de cobro). Puede ser null. */
    public final Adjuntos.Archivo imagen;

    public Respuesta(boolean exito, String mensaje) {
        this(exito, mensaje, false, null);
    }

    private Respuesta(boolean exito, String mensaje, boolean errorBD, Adjuntos.Archivo imagen) {
        this.exito   = exito;
        this.mensaje = mensaje;
        this.errorBD = errorBD;
        this.imagen  = imagen;
    }

    public static Respuesta ok(String mensaje)    { return new Respuesta(true, mensaje); }
    public static Respuesta ok(String mensaje, Adjuntos.Archivo imagen) { return new Respuesta(true, mensaje, false, imagen); }
    public static Respuesta error(String mensaje) { return new Respuesta(false, mensaje); }
    public static Respuesta errorBD(String det)   { return new Respuesta(false, det, true, null); }
}
