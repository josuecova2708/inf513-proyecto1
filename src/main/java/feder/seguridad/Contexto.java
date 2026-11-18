package feder.seguridad;

/**
 * Quien envio el comando. Se resuelve a partir del correo remitente (tabla usuario).
 * Viaja hasta los handlers para que controlen la PROPIEDAD de los datos:
 * un Productor solo ve su emprendimiento y un Cliente solo lo suyo.
 */
public class Contexto {

    public static final String ADMIN     = "Administrador";
    public static final String PERSONAL  = "Personal";
    public static final String PRODUCTOR = "Productor";
    public static final String CLIENTE   = "Cliente";

    public final String  email;
    public final int     usuarioId;          // -1 si no esta registrado
    public final int     rolId;              // -1 si no esta registrado
    public final String  rol;                // nombre del rol o null
    public final String  nombre;
    public final Integer emprendimientoId;   // solo Productor con emprendimiento asignado
    public final boolean registrado;
    public final boolean inactivo;           // existe pero su cuenta esta inactiva

    public Contexto(String email, int usuarioId, int rolId, String rol, String nombre,
                    Integer emprendimientoId, boolean registrado, boolean inactivo) {
        this.email            = email;
        this.usuarioId        = usuarioId;
        this.rolId            = rolId;
        this.rol              = rol;
        this.nombre           = nombre;
        this.emprendimientoId = emprendimientoId;
        this.registrado       = registrado;
        this.inactivo         = inactivo;
    }

    public static Contexto anonimo(String email, boolean inactivo) {
        return new Contexto(email, -1, -1, null, null, null, false, inactivo);
    }

    public boolean esAdmin()     { return ADMIN.equals(rol); }
    public boolean esPersonal()  { return PERSONAL.equals(rol); }
    public boolean esProductor() { return PRODUCTOR.equals(rol); }
    public boolean esCliente()   { return CLIENTE.equals(rol); }

    /** Administrador y Personal ven todo; Productor y Cliente solo lo propio. */
    public boolean esStaff()     { return esAdmin() || esPersonal(); }

    public String descripcion() {
        return registrado ? rol + " (" + email + ")" : "no registrado";
    }
}
