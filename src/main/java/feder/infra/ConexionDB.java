package feder.infra;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConexionDB {

    private static final String URL =
            "jdbc:postgresql://" + Config.host("db.host", "www.tecnoweb.org.bo") + ":"
            + Config.get("db.port", "5432") + "/" + Config.get("db.name", "db_grupo28sa")
            // Timeouts: una conexion colgada NO debe congelar el ciclo de correo.
            + "?connectTimeout=10&socketTimeout=60&ApplicationName=FederCorreo";
    private static final String USER = Config.get("db.user", "grupo28sa");
    private static final String PASS = Config.get("db.pass", "grup028grup028*");

    private ConexionDB() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static String url() { return URL; }

    /** Ping rapido; no lanza excepcion. */
    public static boolean estaDisponible() {
        try (Connection con = getConnection()) {
            return con.isValid(3);
        } catch (Exception e) {
            Log.error(Log.Tipo.BD, "BD no disponible (" + Config.host("db.host", "?") + ":"
                    + Config.get("db.port", "5432") + "): " + Log.causa(e), null);
            return false;
        }
    }
}
