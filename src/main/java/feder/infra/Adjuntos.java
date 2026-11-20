package feder.infra;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Imagenes adjuntas al correo que se esta procesando (p. ej. el QR que sube el Administrador con SUBQR).
 * CorreoService las carga antes de ejecutar el comando y las limpia al terminar; los handlers las leen
 * con {@link #primeraImagen()}. Mismo patron ThreadLocal que Notificador.
 */
public final class Adjuntos {

    /** Archivo adjunto (entrante o saliente). */
    public record Archivo(String nombre, String mime, byte[] datos) {
        public int kb() { return (datos.length + 1023) / 1024; }
    }

    public static final int MAX_BYTES = 2 * 1024 * 1024;   // 2 MB

    private static final ThreadLocal<List<Archivo>> ACTUALES = ThreadLocal.withInitial(ArrayList::new);

    private Adjuntos() {}

    public static void establecer(List<Archivo> archivos) { ACTUALES.set(new ArrayList<>(archivos)); }
    public static void limpiar()                          { ACTUALES.remove(); }

    /** Primera imagen PNG/JPG adjunta, o null. */
    public static Archivo primeraImagen() {
        for (Archivo a : ACTUALES.get()) if (esImagen(a.mime())) return a;
        return null;
    }

    public static boolean esImagen(String mime) {
        return mime != null && (mime.equals("image/png") || mime.equals("image/jpeg"));
    }

    /** Recorre el correo (multipart anidado) y devuelve las imagenes PNG/JPG de hasta 2 MB. */
    public static List<Archivo> extraer(Part parte) throws Exception {
        List<Archivo> lista = new ArrayList<>();
        recorrer(parte, lista);
        return lista;
    }

    private static void recorrer(Part p, List<Archivo> lista) throws Exception {
        if (p.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) p.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                recorrer(bp, lista);
            }
            return;
        }
        String mime = mimeBase(p.getContentType());
        String nombre = p.getFileName();
        if ("image/jpg".equals(mime)) mime = "image/jpeg";
        if (!esImagen(mime) && nombre != null) {   // algunos clientes mandan application/octet-stream
            String n = nombre.toLowerCase(Locale.ROOT);
            if (n.endsWith(".png")) mime = "image/png";
            else if (n.endsWith(".jpg") || n.endsWith(".jpeg")) mime = "image/jpeg";
        }
        if (!esImagen(mime)) return;
        try (InputStream in = p.getInputStream()) {
            byte[] datos = in.readNBytes(MAX_BYTES + 1);
            if (datos.length > MAX_BYTES) {
                Log.info("  [ADJUNTO] imagen ignorada por superar 2 MB: " + nombre);
                return;
            }
            lista.add(new Archivo(nombre != null ? nombre : (mime.equals("image/png") ? "imagen.png" : "imagen.jpg"),
                    mime, datos));
        }
    }

    private static String mimeBase(String contentType) {
        if (contentType == null) return "";
        int i = contentType.indexOf(';');
        return (i < 0 ? contentType : contentType.substring(0, i)).strip().toLowerCase(Locale.ROOT);
    }
}
