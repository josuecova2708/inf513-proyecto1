package feder.infra;

import jakarta.mail.*;
import jakarta.mail.internet.*;

/**
 * Envio SMTP. Cada correo va como multipart/alternative:
 *   - text/plain  (para clientes de texto)
 *   - text/html con <pre> monoespaciado, para que las tablas se vean alineadas en Gmail.
 * En MODE=TEST no envia nada: imprime el correo en consola.
 */
public final class EnviadorCorreo {

    private EnviadorCorreo() {}

    public static void enviar(String para, String asunto, String cuerpo) throws MessagingException {
        if ("TEST".equals(Config.MODE)) {
            System.out.println("---- [TEST] correo a " + para + " | " + asunto + " ----");
            System.out.println(cuerpo);
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

        MimeBodyPart html = new MimeBodyPart();
        html.setContent("<html><body><pre style=\"font-family:Consolas,'Courier New',monospace;font-size:13px\">"
                + escaparHtml(cuerpo) + "</pre></body></html>", "text/html; charset=UTF-8");

        MimeMultipart alt = new MimeMultipart("alternative");
        alt.addBodyPart(texto);
        alt.addBodyPart(html);
        m.setContent(alt);

        try (Transport t = sesion.getTransport("smtp")) {
            t.connect(ConexionCorreo.HOST, ConexionCorreo.SMTP_PORT, null, null);
            t.sendMessage(m, m.getAllRecipients());
        }
    }

    private static String escaparHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
