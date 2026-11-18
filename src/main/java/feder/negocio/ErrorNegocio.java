package feder.negocio;

/**
 * Error de regla de negocio con un mensaje pensado para el usuario.
 * Lanzarlo dentro de una transaccion la deshace (rollback) y el mensaje se responde por correo.
 */
public class ErrorNegocio extends RuntimeException {
    public ErrorNegocio(String mensaje) {
        super(mensaje);
    }
}
