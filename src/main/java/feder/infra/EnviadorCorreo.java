package feder.infra;

import jakarta.mail.*;
import jakarta.mail.internet.*;

/**
 * Envio SMTP. Cada correo va como multipart/alternative:
 *   - text/plain  (para clientes de texto; mismo contenido de siempre)
 *   - text/html   con diseño (PlantillaHtml): tablas reales, insignia de estado, cabecera y pie.
 * Si armar el HTML falla por cualquier motivo se usa la version <pre> monoespaciada, asi el envio nunca se cae.
 * En MODE=TEST no envia nada: imprime el correo en consola.
 */
public final class EnviadorCorreo {

    private EnviadorCorreo() {}

    /** Aviso generico: el HTML se arma automaticamente a partir del texto. */
    public static void enviar(String para, String asunto, String cuerpo) throws MessagingException {
        enviar(para, asunto, cuerpo, null);
    }

    /** @param html version con diseño ya armada (null = se genera con PlantillaHtml.aviso). */
    public static void enviar(String para, String asunto, String cuerpo, String html) throws MessagingException {
        enviar(para, asunto, cuerpo, html, null);
    }

    /**
     * @param imagen imagen opcional (p. ej. el QR). Se incrusta en el HTML (cid:imagen) y ademas queda
     *               como archivo descargable.
     */
    public static void enviar(String para, String asunto, String cuerpo, String html, Adjuntos.Archivo imagen)
            throws MessagingException {
        if ("TEST".equals(Config.MODE)) {
            System.out.println("---- [TEST] correo a " + para + " | " + asunto + " ----");
            System.out.println(cuerpo);
            if (imagen != null) System.out.println("[imagen adjunta: " + imagen.nombre() + ", " + imagen.kb() + " KB]");
            System.out.println("--------------------------------------------------------");
            return;
        }
        Session sesion = Session.getInstance(ConexionCorreo.getSmtpProps());
        MimeMessage m = new MimeMessage(sesion);
        m.setFrom(new InternetAddress(ConexionCorreo.CORREO));
        m.addRecipient(Message.RecipientType.TO, new InternetAddress(para));
        m.setSubject(asunto, "UTF-8");

        MimeBodyPart texto = new MimeBodyPart();
        texto.setText(cuerpo, "UTF-8");

        MimeBodyPart parteHtml = new MimeBodyPart();
        parteHtml.setContent(html != null ? html : htmlSeguro(asunto, cuerpo), "text/html; charset=UTF-8");

        MimeMultipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(texto);
        alt.addBodyPart(parteHtml);

        if (imagen == null) {
            m.setContent(alt);
        } else {
            // multipart/related [ alternative(texto, html), imagen con Content-ID ]
            MimeBodyPart contenedorAlt = new MimeBodyPart();
            contenedorAlt.setContent(alt);
            MimeBodyPart img = new MimeBodyPart();
            img.setDataHandler(new jakarta.activation.DataHandler(
                    new jakarta.mail.util.ByteArrayDataSource(imagen.datos(), imagen.mime())));
            img.setContentID("<" + PlantillaHtml.CID_IMAGEN + ">");
            img.setDisposition(Part.INLINE);
            img.setFileName(imagen.nombre());
            MimeMultipart related = new MimeMultipart("related");
            related.addBodyPart(contenedorAlt);
            related.addBodyPart(img);
            m.setContent(related);
        }

        try (Transport t = sesion.getTransport("smtp")) {
            t.connect(ConexionCorreo.HOST, ConexionCorreo.SMTP_PORT, null, null);
            t.sendMessage(m, m.getAllRecipients());
        }
    }

    private static String htmlSeguro(String asunto, String cuerpo) {
        try {
            return PlantillaHtml.aviso(asunto, cuerpo);
        } catch (Exception e) {
            Log.error(Log.Tipo.OTRO, "No se pudo armar el HTML del aviso; se envia en texto", e);
            return PlantillaHtml.preformateado(cuerpo);
        }
    }
}
