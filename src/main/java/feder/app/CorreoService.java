package feder.app;

import feder.infra.Adjuntos;
import feder.infra.ConexionCorreo;
import feder.infra.ConexionDB;
import feder.infra.EnviadorCorreo;
import feder.infra.Log;
import feder.infra.PlantillaHtml;
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
 * Si el comando se ejecuto pero falla el SMTP, el correo SI se borra (para no re-ejecutar el comando)
 * y se registra el error [SMTP].
 */
public final class CorreoService {

    private CorreoService() {}

    /**
     * Resumen de un ciclo, para mostrarlo en consola.
     * @param pop3 null = no se intento (BD caida)
     */
    public record Ciclo(boolean bd, Boolean pop3, int bandeja, int respondidos, int ignorados,
                        int pendientes, int sinRespuesta, int errores) {}

    private enum Resultado { RESPONDIDO, IGNORADO, PENDIENTE_BD, SIN_RESPUESTA_SMTP }

    public static Ciclo procesar() {
        if (!ConexionDB.estaDisponible()) {
            return new Ciclo(false, null, 0, 0, 0, 0, 0, 0);
        }
        Store store = null;
        Folder inbox = null;
        int bandeja = 0, respondidos = 0, ignorados = 0, pendientes = 0, sinRespuesta = 0, errores = 0;
        try {
            store = Session.getInstance(ConexionCorreo.getPop3Props()).getStore("pop3");
            store.connect(ConexionCorreo.HOST, ConexionCorreo.USUARIO, ConexionCorreo.PASSWORD);
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] mensajes = inbox.getMessages();
            bandeja = mensajes.length;
            if (bandeja == 0) return new Ciclo(true, true, 0, 0, 0, 0, 0, 0);
            Arrays.sort(mensajes, Comparator.comparing(CorreoService::fecha));

            for (Message msg : mensajes) {
                try {
                    switch (procesarMensaje(msg)) {
                        case RESPONDIDO         -> respondidos++;
                        case IGNORADO           -> ignorados++;
                        case PENDIENTE_BD       -> pendientes++;
                        case SIN_RESPUESTA_SMTP -> sinRespuesta++;
                    }
                } catch (Exception e) {
                    errores++;
                    Log.error(Log.Tipo.POP3, "Error leyendo un mensaje de la bandeja (se conserva para reintentar)", e);
                }
            }
        } catch (Exception e) {
            Log.error(Log.Tipo.POP3, "No se pudo leer la bandeja " + ConexionCorreo.HOST + ":"
                    + ConexionCorreo.POP3_PORT + " (usuario " + ConexionCorreo.USUARIO + ")", e);
            return new Ciclo(true, false, bandeja, respondidos, ignorados, pendientes, sinRespuesta, errores);
        } finally {
            try {
                if (inbox != null && inbox.isOpen()) inbox.close(true);   // aplica los borrados
            } catch (Exception e) {
                Log.error(Log.Tipo.POP3, "No se pudieron borrar los correos procesados (podrian procesarse de nuevo)", e);
            }
            try { if (store != null && store.isConnected()) store.close(); } catch (Exception ignored) {}
        }
        return new Ciclo(true, true, bandeja, respondidos, ignorados, pendientes, sinRespuesta, errores);
    }

    private static Date fecha(Message m) {
        try {
            Date d = m.getSentDate();
            return d != null ? d : new Date(0);
        } catch (MessagingException e) {
            return new Date(0);
        }
    }

    private static Resultado procesarMensaje(Message msg) throws Exception {
        Address[] from = msg.getFrom();
        if (from == null || from.length == 0) {
            msg.setFlag(Flags.Flag.DELETED, true);
            return Resultado.IGNORADO;
        }
        String remitente = ((InternetAddress) from[0]).getAddress();
        String r = remitente.toLowerCase();

        // Evita bucles: respuestas automaticas, rebotes y nuestros propios correos
        if (r.contains("noreply") || r.contains("no-reply") || r.contains("mailer-daemon")
                || r.contains("postmaster") || r.contains("bounce") || r.equalsIgnoreCase(ConexionCorreo.CORREO)) {
            Log.info("  [IGNORADO] remitente automatico: " + remitente);
            msg.setFlag(Flags.Flag.DELETED, true);
            return Resultado.IGNORADO;
        }

        String asunto = msg.getSubject() == null ? "" : MimeUtility.decodeText(msg.getSubject());
        String asuntoSeguro = ProcesadorComandos.ocultarClaves(asunto);
        Log.info("  Procesando: " + remitente + " | " + asuntoSeguro);

        long t0 = System.currentTimeMillis();
        ProcesadorComandos.Salida s;
        try {
            try {
                Adjuntos.establecer(Adjuntos.extraer(msg));   // imagenes adjuntas (p. ej. SUBQR)
            } catch (Exception e) {
                Log.error(Log.Tipo.POP3, "No se pudieron leer los adjuntos de " + remitente + " (se procesa sin ellos)", e);
            }
            s = ProcesadorComandos.procesar(asunto, remitente);
        } finally {
            Adjuntos.limpiar();
        }
        long ms = System.currentTimeMillis() - t0;
        Respuesta resp = s.respuesta();

        if (resp.errorBD) {
            Log.acceso(remitente, asuntoSeguro, s.rol(), false, "BD caida, se reintentara: " + resp.mensaje, ms);
            return Resultado.PENDIENTE_BD;   // no se borra: se reintenta en el proximo ciclo
        }

        // El comando ya se ejecuto: el correo se borra aunque falle el envio, para no ejecutarlo dos veces.
        msg.setFlag(Flags.Flag.DELETED, true);
        try {
            EnviadorCorreo.enviar(remitente, "RE: " + asuntoSeguro, cuerpo(asuntoSeguro, resp),
                    html(asuntoSeguro, resp, false), resp.imagen);
        } catch (Exception e) {
            Log.error(Log.Tipo.SMTP, "No se pudo enviar la respuesta a " + remitente + " via "
                    + ConexionCorreo.HOST + ":" + ConexionCorreo.SMTP_PORT + " (el comando SI se ejecuto)", e);
            Log.acceso(remitente, asuntoSeguro, s.rol(), resp.exito,
                    primeraLinea(resp.mensaje) + " [respuesta NO enviada: SMTP]", ms);
            return Resultado.SIN_RESPUESTA_SMTP;
        }
        Log.acceso(remitente, asuntoSeguro, s.rol(), resp.exito, primeraLinea(resp.mensaje), ms);
        return Resultado.RESPONDIDO;
    }

    static String cuerpo(String asunto, Respuesta r) {
        return "=== FEDER - Comercio de productos agricolas (Grupo 28) ===\n"
                + "Comando: " + asunto + "\n"
                + "Estado:  " + (r.exito ? "EXITO" : "ERROR") + "\n\n"
                + (r.exito ? r.mensaje : "Error: " + r.mensaje) + "\n\n"
                + "---\nRespuesta automatica de " + ConexionCorreo.CORREO + "  |  Envie HELP para ver sus comandos.";
    }

    /** Version con diseño de la respuesta; si algo falla se usa el texto monoespaciado de siempre. */
    static String html(String asunto, Respuesta r, boolean vistaPrevia) {
        try {
            String src = null;
            if (r.imagen != null) {
                src = vistaPrevia   // en el navegador no existe cid:, se incrusta como data URI
                        ? "data:" + r.imagen.mime() + ";base64," + java.util.Base64.getEncoder().encodeToString(r.imagen.datos())
                        : "cid:" + PlantillaHtml.CID_IMAGEN;
            }
            return PlantillaHtml.respuesta(asunto, r.exito, r.mensaje, src);
        } catch (Exception e) {
            Log.error(Log.Tipo.OTRO, "No se pudo armar el HTML de la respuesta; se envia en texto", e);
            return PlantillaHtml.preformateado(cuerpo(asunto, r));
        }
    }

    private static String primeraLinea(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
