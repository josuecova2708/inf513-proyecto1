package feder.negocio.usuarios;

import feder.app.ProcesadorComandos;
import feder.negocio.Db;
import feder.negocio.Respuesta;
import feder.parser.ComandoSpec;
import feder.parser.ComandoSpec.Accion;
import feder.parser.ParamSpec;
import feder.parser.Params;
import feder.parser.Registro;
import feder.seguridad.Autorizacion;
import feder.seguridad.Contexto;

import java.util.*;

/** HELP: muestra SOLO los comandos que el rol del remitente puede usar. */
public final class AyudaHandler {

    private static final Map<String, String> CU = new LinkedHashMap<>();
    static {
        CU.put("GEN", "General");
        CU.put("CU1", "CU1 - Usuarios, roles y permisos");
        CU.put("CU2", "CU2 - Productores y comunidades");
        CU.put("CU3", "CU3 - Produccion, lotes y trazabilidad");
        CU.put("CU4", "CU4 - Catalogo, inventario y alertas");
        CU.put("CU5", "CU5 - Carrito, compras y encargos");
        CU.put("CU6", "CU6 - Pedidos, pagos, entregas y notificaciones");
        CU.put("CU7", "CU7 - Indicadores financieros");
        CU.put("CU8", "CU8 - Reportes y estadisticas");
    }

    private AyudaHandler() {}

    public static void registrar(Registro r) {
        r.cmd("HELP", "GEN", "ayuda", Accion.PUBLICO,
                "Muestra los comandos que usted puede usar, o el detalle de uno.",
                "HELP   o   HELP[\"INSCAR\"]", AyudaHandler::ayuda,
                ParamSpec.texto("comando", 10).todos());
    }

    private static Respuesta ayuda(Params p, Contexto ctx) throws Exception {
        Map<String, boolean[]> permisos = ctx.registrado
                ? Db.con(c -> Autorizacion.permisosDe(c, ctx.rolId)) : Map.of();
        Registro reg = ProcesadorComandos.REGISTRO;
        return p.tiene(0) ? detalle(reg, p.str(0).toUpperCase(), ctx, permisos) : listado(reg, ctx, permisos);
    }

    private static Respuesta listado(Registro reg, Contexto ctx, Map<String, boolean[]> permisos) {
        StringBuilder sb = new StringBuilder();
        sb.append("Remitente: ").append(ctx.email).append('\n');
        sb.append("Rol:       ").append(ctx.registrado ? ctx.rol : "NO REGISTRADO").append("\n\n");
        sb.append("Formato: COMANDO[\"param1\",\"param2\",...]   (\"*\" = todos; param? = opcional)\n");
        sb.append("Detalle de un comando: HELP[\"COMANDO\"]\n");

        Map<String, List<ComandoSpec>> porCu = new LinkedHashMap<>();
        CU.keySet().forEach(k -> porCu.put(k, new ArrayList<>()));
        for (ComandoSpec c : reg.todos()) {
            if (Autorizacion.puede(ctx, permisos, c)) porCu.get(c.cu).add(c);
        }
        int total = 0;
        for (var e : porCu.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append("\n== ").append(CU.get(e.getKey())).append(" ==\n");
            for (ComandoSpec c : e.getValue()) {
                sb.append("  ").append(c.firma()).append('\n');
                sb.append("      ").append(c.descripcion).append('\n');
                total++;
            }
        }
        if (!ctx.registrado) {
            sb.append("\nUsted aun no esta registrado: registrese con REGCLI para comprar en FEDER.\n");
        }
        sb.append("\nTotal: ").append(total).append(" comandos disponibles para usted.");
        return Respuesta.ok(sb.toString());
    }

    private static Respuesta detalle(Registro reg, String nombre, Contexto ctx, Map<String, boolean[]> permisos) {
        ComandoSpec c = reg.buscar(nombre);
        if (c == null) {
            String sug = reg.sugerencia(nombre);
            return Respuesta.error("No existe el comando " + nombre + "." + (sug != null ? " ¿Quiso decir " + sug + "?" : ""));
        }
        StringBuilder sb = new StringBuilder();
        sb.append(c.nombre).append("  (").append(CU.get(c.cu)).append(")\n\n");
        sb.append(c.descripcion).append("\n\n");
        sb.append("Uso:     ").append(c.firma()).append('\n');
        sb.append("Ejemplo: ").append(c.ejemplo).append("\n\n");
        if (!c.params.isEmpty()) {
            sb.append("Parametros:\n");
            for (ParamSpec ps : c.params) {
                sb.append(String.format("  %-18s %s%s%n", ps.nombre, ps.descripcionTipo(),
                        ps.admiteTodos ? " | \"*\" = todos" : ps.obligatorio ? "" : " (opcional)"));
            }
            sb.append('\n');
        }
        String permiso = switch (c.accion) {
            case PUBLICO  -> "cualquier remitente";
            case REGISTRO -> "solo remitentes NO registrados";
            default       -> c.accion.name().toLowerCase() + " en el modulo '" + c.modulo + "'";
        };
        sb.append("Permiso requerido: ").append(permiso).append('\n');
        sb.append("¿Usted puede usarlo?: ").append(Autorizacion.puede(ctx, permisos, c) ? "SI" : "NO");
        return Respuesta.ok(sb.toString());
    }
}
