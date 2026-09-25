import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.synapse.crm.atendimento.application.painel.PainelDeAtendimentosRepositorio;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;

/**
 * E209 — extrai as SQLs que {@code PainelDeAtendimentosRepositorioJdbc} realmente monta, com os
 * parâmetros já ligados, sem banco: o adaptador recebe um DataSource que só captura o
 * prepareStatement. Assim a bancada mede o código compilado, não uma transcrição manual.
 *
 * <p>Uso: java -cp "crm-atendimento/target/classes;<dependencias>" ExtrairSql.java <saida>
 */
public class ExtrairSql {

    static final UUID GESTOR = UUID.fromString("00000000-0000-0000-0000-0000000a0010");
    static final UUID ATENDENTE = UUID.fromString("00000000-0000-0000-0000-0000000a0001");

    record Capturada(String sql, List<Object> parametros) {}

    static final class Captura extends RuntimeException {
        final Capturada capturada;

        Captura(Capturada capturada) {
            super(null, null, false, false);
            this.capturada = capturada;
        }
    }

    public static void main(String[] args) throws Exception {
        Path saida = Path.of(args[0]);
        Files.createDirectories(saida);
        PainelDeAtendimentosRepositorio painel = instanciar();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        Map<String, Capturada> cenarios = new TreeMap<>();
        for (String papel : List.of("GESTOR", "ATENDENTE")) {
            UUID usuario = papel.equals("GESTOR") ? GESTOR : ATENDENTE;
            boolean restrito = papel.equals("ATENDENTE");
            for (VisaoAtendimento visao : VisaoAtendimento.values()) {
                if (visao == VisaoAtendimento.TODOS && restrito) continue;
                String sufixo = papel + "_" + visao;
                cenarios.put("contar_" + sufixo, capturar(p -> p.contar(visao, usuario, restrito), painel));
                cenarios.put("listar_" + sufixo, capturar(p -> p.listar(visao, usuario, restrito), painel));
                cenarios.put("pagina1_" + sufixo, capturar(
                        p -> p.listarPaginado(visao, usuario, restrito, false, null, null, 51), painel));
            }
            cenarios.put("pagina2_" + papel + "_FINALIZADOS", capturar(p -> p.listarPaginado(
                    VisaoAtendimento.FINALIZADOS, usuario, restrito, true,
                    Instant.parse("2026-07-01T00:00:00Z"),
                    UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), 51), painel));
        }

        for (Map.Entry<String, Capturada> cenario : cenarios.entrySet()) {
            String papel = cenario.getKey().contains("GESTOR") ? "GESTOR" : "ATENDENTE";
            UUID usuario = papel.equals("GESTOR") ? GESTOR : ATENDENTE;
            Files.writeString(saida.resolve(cenario.getKey() + ".sql"),
                    comContexto(papel, usuario, ligar(cenario.getValue())), StandardCharsets.UTF_8);
        }
        System.out.println(cenarios.size() + " cenarios em " + saida.toAbsolutePath());
    }

    static PainelDeAtendimentosRepositorio instanciar() throws Exception {
        Class<?> tipo = Class.forName(
                "com.synapse.crm.atendimento.infrastructure.persistencia.painel.PainelDeAtendimentosRepositorioJdbc");
        Constructor<?> construtor = tipo.getDeclaredConstructor(DataSource.class);
        construtor.setAccessible(true);
        return (PainelDeAtendimentosRepositorio) construtor.newInstance(dataSourceQueCaptura());
    }

    static Capturada capturar(Function<PainelDeAtendimentosRepositorio, ?> chamada,
            PainelDeAtendimentosRepositorio painel) {
        try {
            chamada.apply(painel);
        } catch (RuntimeException erro) {
            Throwable causa = erro;
            while (causa != null && !(causa instanceof Captura)) causa = causa.getCause();
            if (causa instanceof Captura captura) return captura.capturada;
            throw erro;
        }
        throw new IllegalStateException("nenhuma SQL executada");
    }

    static DataSource dataSourceQueCaptura() {
        return (DataSource) Proxy.newProxyInstance(ExtrairSql.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (ds, metodoDs, argsDs) -> {
                    if (!metodoDs.getName().equals("getConnection")) return null;
                    return Proxy.newProxyInstance(ExtrairSql.class.getClassLoader(),
                            new Class<?>[] {Connection.class}, (con, metodo, args) -> switch (metodo.getName()) {
                                case "prepareStatement" -> statement((String) args[0]);
                                case "isClosed" -> false;
                                case "getAutoCommit" -> false;
                                default -> null;
                            });
                });
    }

    static PreparedStatement statement(String sql) {
        List<Object> parametros = new ArrayList<>();
        return (PreparedStatement) Proxy.newProxyInstance(ExtrairSql.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (ps, metodo, args) -> {
                    String nome = metodo.getName();
                    if (nome.startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer i) {
                        while (parametros.size() < i) parametros.add(null);
                        parametros.set(i - 1, args[1]);
                        return null;
                    }
                    if (nome.startsWith("execute")) throw new Captura(new Capturada(sql, List.copyOf(parametros)));
                    return defaultDe(metodo);
                });
    }

    static Object defaultDe(Method metodo) {
        Class<?> tipo = metodo.getReturnType();
        if (tipo == boolean.class) return false;
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        return null;
    }

    static String ligar(Capturada capturada) {
        StringBuilder sql = new StringBuilder();
        int indice = 0;
        for (char caractere : capturada.sql().toCharArray()) {
            if (caractere == '?') {
                sql.append(literal(capturada.parametros().get(indice++)));
            } else {
                sql.append(caractere);
            }
        }
        if (indice != capturada.parametros().size()) {
            throw new IllegalStateException("parametros sobrando: " + capturada.sql());
        }
        return sql.toString();
    }

    static String literal(Object valor) {
        if (valor == null) return "NULL";
        if (valor instanceof Number) return valor.toString();
        if (valor instanceof UUID) return "'" + valor + "'::uuid";
        if (valor instanceof java.sql.Timestamp ts) return "'" + ts.toInstant() + "'::timestamptz";
        return "'" + valor.toString().replace("'", "''") + "'";
    }

    static String comContexto(String papel, UUID usuario, String sql) {
        return "SET ROLE synapse_app;\n"
                + "SELECT set_config('app.usuario_id', '" + usuario + "', false),"
                + " set_config('app.papel', '" + papel + "', false) \\g /dev/null\n"
                + sql + ";\n";
    }
}
