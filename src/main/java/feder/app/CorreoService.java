package feder.app;

import feder.infra.ConexionCorreo;
import feder.infra.ConexionDB;
import feder.infra.EnviadorCorreo;
import feder.infra.Log;
import feder.negocio.Respuesta;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Lee la casilla por POP3 (FIFO: del mas antiguo al mas reciente), procesa el comando del Asunto
 * y responde al remitente. Si la BD no responde, el correo NO se borra para reintentarlo.
 */
public final class CorreoService {

    private CorreoService() {}

    public static void procesar() {
        if (!ConexionDB.estaDisponible()) {
            Log.info("[BD NO DISPONIBLE] ciclo saltado; los correos se conservan para reintentar.");
            return;
        }
        Store store = null;
        Folder inbox = null;
        try {
            store = Session.getInstance(ConexionCorreo.getPop3Props()).getStore("pop3");
            store.connect(ConexionCorreo.HOST, ConexionCorreo.USUARIO, ConexionCorreo.PASSWORD);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] mensajes = inbox.getMessages();
            if (mensajes.length == 0) return;
            Arrays.sort(mensajes, Comparator.comparing(CorreoService::fecha));
            Log.info("Mensajes en bandeja: " + mensajes.length);

            for (Message msg : mensajes) {
                try {
                    procesarMensaje(msg);
                } catch (Exception e) {
                    Log.error("Error procesando un mensaje (se conserva para reintentar)", e);
                }
            }
        } catch (Exception e) {
            Log.error("Error POP3: " + e.getMessage(), e);
        } finally {
            try { if (inbox != null && inbox.isOpen()) inbox.close(true); } catch (Exception ignored) {}
            try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
        }
    }

    private static Date fecha(Message m) {
        try {
            Date d = m.getSentDate();
            return d != null ? d : new Date(0);
        } catch (MessagingException e) {
            return new Date(0);
        }
    }

    private static void procesarMensaje(Message msg) throws Exception {
        Address[] from = msg.getFrom();
        if (from == null || from.length == 0) {
            msg.setFlag(Flags.Flag.DELETED, true);
            return;
        }
        String remitente = ((InternetAddress) from[0]).getAddress();
        String r = remitente.toLowerCase();

        // Evita bucles: respuestas automaticas, rebotes y nuestros propios correos
        if (r.contains("noreply") || r.contains("no-reply") || r.contains("mailer-daemon")
                || r.contains("postmaster") || r.contains("bounce") || r.equalsIgnoreCase(ConexionCorreo.CORREO)) {
            Log.info("  [IGNORADO] remitente automatico: " + remitente);
            msg.setFlag(Flags.Flag.DELETED, true);
            return;
        }

        String asunto = msg.getSubject() == null ? "" : MimeUtility.decodeText(msg.getSubject());
        String asuntoSeguro = ProcesadorComandos.ocultarClaves(asunto);
        Log.info("  Procesando: " + remitente + " | " + asuntoSeguro);

        long t0 = System.currentTimeMillis();
        ProcesadorComandos.Salida s = ProcesadorComandos.procesar(asunto, remitente);
        long ms = System.currentTimeMillis() - t0;
        Respuesta resp = s.respuesta();

        if (resp.errorBD) {
            Log.acceso(remitente, asuntoSeguro, s.rol(), false, "BD caida, se reintentara: " + resp.mensaje, ms);
            return;   // no se borra: se reintenta en el proximo ciclo
        }

        EnviadorCorreo.enviar(remitente, "RE: " + asuntoSeguro, cuerpo(asuntoSeguro, resp));
        msg.setFlag(Flags.Flag.DELETED, true);
        Log.acceso(remitente, asuntoSeguro, s.rol(), resp.exito, primeraLinea(resp.mensaje), ms);
    }

    static String cuerpo(String asunto, Respuesta r) {
        return "=== FEDER - Comercio de productos agricolas (Grupo 28) ===\n"
                + "Comando: " + asunto + "\n"
                + "Estado:  " + (r.exito ? "EXITO" : "ERROR") + "\n\n"
                + (r.exito ? r.mensaje : "Error: " + r.mensaje) + "\n\n"
                + "---\nRespuesta automatica de " + ConexionCorreo.CORREO + "  |  Envie HELP para ver sus comandos.";
    }

    private static String primeraLinea(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
