package feder.negocio;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

public class TablaFormato {

    private static final java.time.format.DateTimeFormatter FH =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Texto de una celda: fechas sin milisegundos, sin saltos de linea, max 100 caracteres. */
    private static String celda(Object val) {
        if (val == null) return "-";
        if (val instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().format(FH);
        String s = val.toString().replaceAll("\\s+", " ").strip();
        return s.length() > 100 ? s.substring(0, 97) + "..." : s;
    }

    public static String formatear(ResultSet rs) throws Exception {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();

        String[] headers = new String[cols];
        for (int i = 0; i < cols; i++) {
            headers[i] = meta.getColumnName(i + 1).toUpperCase();
        }

        List<String[]> filas = new ArrayList<>();
        while (rs.next()) {
            String[] fila = new String[cols];
            for (int i = 0; i < cols; i++) {
                Object val = rs.getObject(i + 1);
                fila[i] = celda(val);
            }
            filas.add(fila);
        }

        if (filas.isEmpty()) {
            return "(sin resultados)";
        }

        int[] anchos = new int[cols];
        for (int i = 0; i < cols; i++) {
            anchos[i] = headers[i].length();
        }
        for (String[] fila : filas) {
            for (int i = 0; i < cols; i++) {
                anchos[i] = Math.max(anchos[i], fila[i].length());
            }
        }

        String sep = separador(anchos);
        StringBuilder sb = new StringBuilder();
        sb.append(sep).append("\n");
        sb.append(fila(headers, anchos)).append("\n");
        sb.append(sep).append("\n");
        for (String[] f : filas) {
            sb.append(fila(f, anchos)).append("\n");
        }
        sb.append(sep);
        return sb.toString();
    }

    private static String separador(int[] anchos) {
        StringBuilder sb = new StringBuilder("+");
        for (int a : anchos) sb.append("-".repeat(a + 2)).append("+");
        return sb.toString();
    }

    private static String fila(String[] celdas, int[] anchos) {
        StringBuilder sb = new StringBuilder("|");
        for (int i = 0; i < celdas.length; i++) {
            sb.append(" ").append(pad(celdas[i], anchos[i])).append(" |");
        }
        return sb.toString();
    }

    private static String pad(String s, int ancho) {
        return s + " ".repeat(ancho - s.length());
    }
}
