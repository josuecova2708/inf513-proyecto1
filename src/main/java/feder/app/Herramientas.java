package feder.app;

import feder.infra.ConexionCorreo;
import feder.infra.ConexionDB;

import jakarta.mail.Folder;
import jakarta.mail.Session;
import jakarta.mail.Store;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;

/**
 * Utilidades de administracion (no requieren psql):
 *   java -cp FederCorreo-1.0.jar feder.app.Herramientas diag
 *   java -cp FederCorreo-1.0.jar feder.app.Herramientas sql db/schema.sql
 *   java -cp FederCorreo-1.0.jar feder.app.Herramientas query "SELECT ..."
 */
public class Herramientas {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Uso: diag | sql <archivo.sql> | query \"SELECT ...\" | ciclo | enviar <remitente> <asunto>"
                    + " | preview <remitente> \"<ASUNTO>\"");
            return;
        }
        switch (args[0].toLowerCase()) {
            case "diag"  -> diagnostico();
            case "sql"   -> ejecutarArchivo(args[1]);
            case "query" -> consulta(args[1]);
            case "manual" -> manual(args.length > 1 ? args[1] : "doc/COMANDOS.md");
            case "ciclo" -> { CorreoService.procesar(); Mantenimiento.ejecutar(); }
            case "enviar" -> enviarPrueba(args[1], args[2]);
            case "preview" -> preview(args[1], args[2]);
            default      -> System.out.println("Opcion desconocida: " + args[0]);
        }
    }

    private static void diagnostico() {
        System.out.println("BD  -> " + ConexionDB.url());
        try (Connection con = ConexionDB.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT current_database(), current_user, version(), " +
                     "(SELECT count(*) FROM information_schema.tables WHERE table_schema='public')")) {
            rs.next();
            System.out.println("    OK  db=" + rs.getString(1) + " user=" + rs.getString(2)
                    + " tablas_public=" + rs.getInt(4));
            System.out.println("    " + rs.getString(3));
        } catch (Exception e) {
            System.out.println("    FALLO: " + e.getMessage());
        }

        System.out.println("POP3 -> " + ConexionCorreo.HOST + ":" + ConexionCorreo.POP3_PORT
                + " usuario=" + ConexionCorreo.USUARIO);
        try {
            Store store = Session.getInstance(ConexionCorreo.getPop3Props()).getStore("pop3");
            store.connect(ConexionCorreo.HOST, ConexionCorreo.USUARIO, ConexionCorreo.PASSWORD);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            System.out.println("    OK  mensajes en INBOX=" + inbox.getMessageCount());
            inbox.close(false);
            store.close();
        } catch (Exception e) {
            System.out.println("    FALLO: " + e.getMessage());
        }
    }

    /** Ejecuta un script completo (pgjdbc respeta los bloques $$ de las funciones). */
    private static void ejecutarArchivo(String ruta) throws Exception {
        String sql = Files.readString(Paths.get(ruta), StandardCharsets.UTF_8);
        try (Connection con = ConexionDB.getConnection(); Statement st = con.createStatement()) {
            con.setAutoCommit(false);
            try {
                st.execute(sql);
                con.commit();
                System.out.println("OK: " + ruta + " ejecutado.");
            } catch (SQLException e) {
                con.rollback();
                System.out.println("FALLO (se hizo rollback): " + e.getMessage());
            }
        }
    }

    /** Genera el manual de comandos (Markdown) desde el registro y la matriz de permisos de la BD. */
    private static void manual(String salida) throws Exception {
        String[] roles = { "Administrador", "Personal", "Productor", "Cliente" };
        String[] abrev = { "A", "P", "R", "C" };
        java.util.List<feder.seguridad.Contexto> ctxs = new java.util.ArrayList<>();
        java.util.List<java.util.Map<String, boolean[]>> perms = new java.util.ArrayList<>();
        try (Connection con = ConexionDB.getConnection()) {
            for (String r : roles) {
                int id = feder.negocio.Db.entero(con, "SELECT id FROM rol WHERE nombre = ?", r);
                ctxs.add(new feder.seguridad.Contexto("x", 0, id, r, r, null, true, false));
                perms.add(feder.seguridad.Autorizacion.permisosDe(con, id));
            }
        }
        feder.seguridad.Contexto anon = feder.seguridad.Contexto.anonimo("x", false);
        String[] titulos = {
            "GEN", "General", "CU1", "CU1 - Usuarios, roles y permisos", "CU2", "CU2 - Productores y comunidades",
            "CU3", "CU3 - Produccion, lotes y trazabilidad", "CU4", "CU4 - Catalogo, inventario y alertas",
            "CU5", "CU5 - Carrito, compras y encargos", "CU6", "CU6 - Pedidos, pagos, entregas y notificaciones",
            "CU7", "CU7 - Indicadores financieros", "CU8", "CU8 - Reportes y estadisticas" };
        StringBuilder sb = new StringBuilder();
        sb.append("# Manual de comandos - FEDER por correo (Grupo 28)\n\n")
          .append("Envíe un correo a **grupo28sa@tecnoweb.org.bo** con el comando en el **Asunto** (el cuerpo se ignora).\n\n")
          .append("- Formato: `COMANDO[\"param1\",\"param2\",...]`. Los comandos sin parámetros pueden ir solos: `HELP`, `LISCAR`.\n")
          .append("- `param?` = opcional (puede omitirse o ir vacío `\"\"`). `\"*\"` = todos (en filtros).\n")
          .append("- Se toleran espacios extra, minúsculas, prefijos `RE:`/`RV:`/`FW:` y comillas escapadas `\\\"` dentro de un parámetro.\n")
          .append("- Fechas `AAAA-MM-DD`; fecha y hora `AAAA-MM-DD HH:MM`; decimales con punto o coma.\n")
          .append("- El sistema identifica al usuario por el **correo remitente**. Quien no está registrado solo puede usar `HELP` y `REGCLI`.\n")
          .append("- `HELP` devuelve solo los comandos que **su rol** puede usar; `HELP[\"COMANDO\"]` explica uno en detalle.\n\n")
          .append("Roles: **A** = Administrador, **P** = Personal de FEDER, **R** = Productor (solo sus datos), **C** = Cliente (solo sus datos), **N** = no registrado.\n")
          .append("Los permisos salen de la tabla `rol_permiso` y el Administrador los puede cambiar con `ASIPRM` (esta tabla refleja la configuración inicial).\n");
        for (int t = 0; t < titulos.length; t += 2) {
            String cu = titulos[t];
            sb.append("\n## ").append(titulos[t + 1]).append("\n\n| Comando | Roles | Descripción | Ejemplo |\n|---|---|---|---|\n");
            for (feder.parser.ComandoSpec c : ProcesadorComandos.REGISTRO.todos()) {
                if (!c.cu.equals(cu)) continue;
                StringBuilder rs = new StringBuilder();
                for (int i = 0; i < roles.length; i++)
                    if (feder.seguridad.Autorizacion.puede(ctxs.get(i), perms.get(i), c)) rs.append(rs.length() > 0 ? "," : "").append(abrev[i]);
                if (feder.seguridad.Autorizacion.puede(anon, java.util.Map.of(), c)) rs.append(rs.length() > 0 ? "," : "").append("N");
                sb.append("| `").append(c.firma().replace("|", "\\|")).append("` | ").append(rs)
                  .append(" | ").append(c.descripcion.replace("|", "\\|")).append(" | `")
                  .append(c.ejemplo.replace("|", "\\|")).append("` |\n");
            }
        }
        sb.append("\n## Flujos típicos\n\n")
          .append("**Compra inmediata (Cliente):** `LISPRO[\"*\"]` → `INSCAR[\"1\",\"10\"]` → `LISCAR` → `CONCAR[\"recojo\"]` (reserva el stock) → `INSPAG[\"<pedido>\",\"efectivo\"]` → recibe correo *Pago confirmado* / *Pedido confirmado*.\n\n")
          .append("**Procesar el pedido (Personal):** `MODPAG[\"<pago>\",\"pagado\"]` → `MODPED[\"<pedido>\",\"en_preparacion\"]` (descuenta los lotes) → `MODPED[\"<pedido>\",\"listo_entrega\"]` (avisa al cliente) → `MODENT[\"<pedido>\",\"entregada\",\"Receptor\"]`.\n\n")
          .append("**Producción (Productor → Personal):** `INSPRC[...]` → `VALPRC[\"<id>\",\"validada\"]` → `INSLOT[\"<id>\",\"COD-001\",\"cantidad\",\"vence\",\"costo\"]` → el stock aparece en el catálogo.\n\n")
          .append("**Encargo:** Cliente `INSENC[...]` → Personal `MODENC[\"<id>\",\"aceptado\"]` → (llega el stock) `MODENC[\"<id>\",\"disponible\"]` (avisa al cliente) → Cliente `CONENC[\"<id>\",\"recojo\"]` → pago como en una compra.\n");
        Files.writeString(Paths.get(salida), sb.toString(), StandardCharsets.UTF_8);
        System.out.println("Manual generado: " + salida);
    }

    /** Deja un correo de prueba en la casilla del grupo, como si lo enviara 'remitente'. */
    private static void enviarPrueba(String remitente, String asunto) throws Exception {
        Session s = Session.getInstance(ConexionCorreo.getSmtpProps());
        jakarta.mail.internet.MimeMessage m = new jakarta.mail.internet.MimeMessage(s);
        m.setFrom(new jakarta.mail.internet.InternetAddress(remitente));
        m.addRecipient(jakarta.mail.Message.RecipientType.TO,
                new jakarta.mail.internet.InternetAddress(ConexionCorreo.CORREO));
        m.setSubject(asunto, "UTF-8");
        m.setText("Prueba automatica", "UTF-8");
        jakarta.mail.Transport.send(m);
        System.out.println("Correo de prueba entregado a " + ConexionCorreo.CORREO);
    }

    /**
     * Ejecuta un comando como si llegara por correo y guarda la respuesta con diseño en logs/preview.html
     * (no envia nada). OJO: el comando se ejecuta de verdad; usar comandos de consulta (HELP, LISPRO...).
     */
    private static void preview(String remitente, String asunto) throws Exception {
        ProcesadorComandos.Salida s = ProcesadorComandos.procesar(asunto, remitente);
        String seguro = ProcesadorComandos.ocultarClaves(asunto);
        java.nio.file.Path salida = Paths.get(feder.infra.Config.get("log.dir", "logs"), "preview.html");
        Files.createDirectories(salida.getParent());
        Files.writeString(salida, CorreoService.html(seguro, s.respuesta()), StandardCharsets.UTF_8);
        System.out.println("Vista previa guardada en " + salida.toAbsolutePath());
    }

    private static void consulta(String sql) throws Exception {
        try (Connection con = ConexionDB.getConnection(); Statement st = con.createStatement()) {
            if (st.execute(sql)) {
                try (ResultSet rs = st.getResultSet()) {
                    System.out.println(feder.negocio.TablaFormato.formatear(rs));
                }
            } else {
                System.out.println("Filas afectadas: " + st.getUpdateCount());
            }
        }
    }
}
