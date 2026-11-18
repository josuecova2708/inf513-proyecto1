package feder.infra;

import java.util.Properties;

public final class ConexionCorreo {

    public static final String HOST      = Config.host("mail.host", "mail.tecnoweb.org.bo");
    public static final int    POP3_PORT = Config.getInt("mail.pop3.port", 110);
    public static final int    SMTP_PORT = Config.getInt("mail.smtp.port", 25);
    public static final String USUARIO   = Config.get("mail.user", "grupo28sa");
    public static final String PASSWORD  = Config.get("mail.pass", "grup028grup028*");
    public static final String CORREO    = Config.get("mail.address", "grupo28sa@tecnoweb.org.bo");

    private ConexionCorreo() {}

    public static Properties getPop3Props() {
        Properties p = new Properties();
        p.put("mail.pop3.host", HOST);
        p.put("mail.pop3.port", String.valueOf(POP3_PORT));
        p.put("mail.pop3.ssl.enable", "false");
        p.put("mail.pop3.starttls.enable", "false");
        p.put("mail.pop3.connectiontimeout", "15000");
        p.put("mail.pop3.timeout", "30000");
        return p;
    }

    /**
     * El relay SMTP de tecnoweb autoriza por IP (lista blanca del docente), no por
     * credenciales. En SERVER sale por localhost; en DEV tu IP publica debe estar habilitada.
     */
    public static Properties getSmtpProps() {
        Properties p = new Properties();
        p.put("mail.smtp.host", HOST);
        p.put("mail.smtp.port", String.valueOf(SMTP_PORT));
        p.put("mail.smtp.auth", "false");
        p.put("mail.smtp.starttls.enable", "false");
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "30000");
        p.put("mail.smtp.writetimeout", "30000");
        p.put("mail.mime.charset", "UTF-8");
        return p;
    }
}
