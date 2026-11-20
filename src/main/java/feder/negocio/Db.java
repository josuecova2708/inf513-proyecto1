package feder.negocio;

import feder.infra.ConexionDB;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos. TODAS las consultas pasan por PreparedStatement con parametros
 * enlazados (nunca se concatenan valores del usuario en el SQL).
 */
public final class Db {

    private Db() {}

    @FunctionalInterface
    public interface Trabajo<T> {
        T ejecutar(Connection con) throws Exception;
    }

    /**
     * Conexion compartida durante el procesamiento de UN comando: evita abrir varias conexiones
     * (cada una cuesta un viaje de red + autenticacion) para un mismo correo.
     */
    private static final ThreadLocal<Connection> COMPARTIDA = new ThreadLocal<>();

    /** Abre la conexion del comando; cerrar con terminarSesion(). */
    public static Connection iniciarSesion() throws SQLException {
        Connection c = ConexionDB.getConnection();
        COMPARTIDA.set(c);
        return c;
    }

    public static void terminarSesion() {
        Connection c = COMPARTIDA.get();
        COMPARTIDA.remove();
        if (c != null) {
            try { c.close(); } catch (SQLException ignored) {}
        }
    }

    /** Ejecuta el trabajo en una transaccion: commit si termina bien, rollback ante cualquier error. */
    public static <T> T tx(Trabajo<T> trabajo) throws Exception {
        Connection compartida = COMPARTIDA.get();
        if (compartida != null) return enTransaccion(compartida, trabajo);
        try (Connection con = ConexionDB.getConnection()) {
            return enTransaccion(con, trabajo);
        }
    }

    private static <T> T enTransaccion(Connection con, Trabajo<T> trabajo) throws Exception {
        boolean anidada = !con.getAutoCommit();      // ya dentro de otra tx: se suma a ella
        if (anidada) return trabajo.ejecutar(con);
        con.setAutoCommit(false);
        try {
            T r = trabajo.ejecutar(con);
            con.commit();
            return r;
        } catch (Exception e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(true);
        }
    }

    /** Trabajo de solo lectura / una sola sentencia (autocommit). */
    public static <T> T con(Trabajo<T> trabajo) throws Exception {
        Connection compartida = COMPARTIDA.get();
        if (compartida != null) return trabajo.ejecutar(compartida);
        try (Connection con = ConexionDB.getConnection()) {
            return trabajo.ejecutar(con);
        }
    }

    public static PreparedStatement preparar(Connection con, String sql, Object... args) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            int idx = i + 1;
            if (a == null)                         ps.setNull(idx, Types.NULL);
            else if (a instanceof Integer v)       ps.setInt(idx, v);
            else if (a instanceof Long v)          ps.setLong(idx, v);
            else if (a instanceof BigDecimal v)    ps.setBigDecimal(idx, v);
            else if (a instanceof Boolean v)       ps.setBoolean(idx, v);
            else if (a instanceof LocalDate v)     ps.setObject(idx, v);
            else if (a instanceof LocalDateTime v) ps.setObject(idx, v);
            else if (a instanceof byte[] v)        ps.setBytes(idx, v);   // BYTEA (p. ej. imagen del QR)
            else                                   ps.setString(idx, a.toString());
        }
        return ps;
    }

    /** Resultado formateado como tabla de texto para el correo. */
    public static String tabla(Connection con, String sql, Object... args) throws Exception {
        try (PreparedStatement ps = preparar(con, sql, args); ResultSet rs = ps.executeQuery()) {
            return TablaFormato.formatear(rs);
        }
    }

    public static String tabla(String sql, Object... args) throws Exception {
        return con(c -> tabla(c, sql, args));
    }

    public static int update(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = preparar(con, sql, args)) {
            return ps.executeUpdate();
        }
    }

    public static List<Fila> filas(Connection con, String sql, Object... args) throws SQLException {
        List<Fila> lista = new ArrayList<>();
        try (PreparedStatement ps = preparar(con, sql, args); ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            while (rs.next()) {
                Fila f = new Fila();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    f.put(md.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                }
                lista.add(f);
            }
        }
        return lista;
    }

    /** Primera fila o null. */
    public static Fila fila(Connection con, String sql, Object... args) throws SQLException {
        List<Fila> l = filas(con, sql, args);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Primera columna de la primera fila (o null). Util para RETURNING id, count(*), etc. */
    @SuppressWarnings("unchecked")
    public static <T> T valor(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = preparar(con, sql, args); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? (T) rs.getObject(1) : null;
        }
    }

    public static int entero(Connection con, String sql, Object... args) throws SQLException {
        Object v = valor(con, sql, args);
        return v == null ? 0 : ((Number) v).intValue();
    }

    public static boolean existe(Connection con, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = preparar(con, sql, args); ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}
