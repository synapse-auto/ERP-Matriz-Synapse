package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Executa o artefato externo real, nunca contra o banco compartilhado do CRM. */
@Testcontainers
class AgendamentoIntegracaoIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final String EVENTO = """
            {"clinica_id":2,"schedule_id":"teste-1","acao":"CANCELADO",
             "lead_id":"00000000-0000-4000-8000-000000000001",
             "atendimento_id":"00000000-0000-4000-8000-000000000002"}
            """;

    private Connection conectar() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private String script() throws Exception {
        try (var recurso = Objects.requireNonNull(getClass().getResourceAsStream("/n8n-sql/001-agendamento-evento.sql"))) {
            return new String(recurso.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void executar(String sql) throws SQLException {
        try (var conexao = conectar(); var comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private String consultar(String sql) throws SQLException {
        try (var conexao = conectar(); var comando = conexao.createStatement(); var resultado = comando.executeQuery(sql)) {
            resultado.next();
            return resultado.getString(1);
        }
    }

    private String registrar(String dados, String chave) throws SQLException {
        try (var conexao = conectar(); var comando = conexao.prepareStatement(
                "SELECT evento_id::text || ':' || repetido::text FROM automacao_agendamentos.registrar_evento(?::jsonb, ?)")) {
            comando.setString(1, dados);
            comando.setString(2, chave);
            try (var resultado = comando.executeQuery()) {
                resultado.next();
                return resultado.getString(1);
            }
        }
    }

    @BeforeEach
    void provisionarBancoIsolado() throws Exception {
        executar("DROP SCHEMA IF EXISTS automacao_agendamentos CASCADE; DROP TABLE IF EXISTS public.lead; DROP TABLE IF EXISTS public.flyway_schema_history");
        executar(script());
    }

    @Test
    void replayNaoDuplicaEConflitoNaoSobrescreve() throws Exception {
        var original = registrar(EVENTO, "evento-1");
        assertThat(original).endsWith(":false");
        assertThat(registrar(EVENTO, "evento-1")).isEqualTo(original.replace(":false", ":true"));
        assertThatThrownBy(() -> registrar(EVENTO.replace("CANCELADO", "CONFIRMADO"), "evento-1"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
        assertThat(consultar("SELECT count(*) FROM automacao_agendamentos.agendamento_evento")).isEqualTo("1");
        assertThat(consultar("SELECT acao FROM automacao_agendamentos.agendamento_evento")).isEqualTo("CANCELADO");
        registrar(EVENTO.replace("\"clinica_id\":2", "\"clinica_id\":3"), "evento-1");
        assertThat(consultar("SELECT count(*) FROM automacao_agendamentos.agendamento_evento")).isEqualTo("2");
    }

    @Test
    void replayComOffsetEquivalentePreservaInstante() throws Exception {
        var utc = EVENTO.replace("{", "{\"agendado_para\":\"2026-10-08T15:00:00Z\",");
        var local = utc.replace("2026-10-08T15:00:00Z", "2026-10-08T12:00:00-03:00");
        var original = registrar(utc, "offset");
        assertThat(registrar(local, "offset")).isEqualTo(original.replace(":false", ":true"));
    }

    @Test
    void concorrenciaMantemUmEvento() throws Exception {
        var barreira = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tarefa = (java.util.concurrent.Callable<String>) () -> {
                barreira.await(10, TimeUnit.SECONDS);
                return registrar(EVENTO, "concorrente");
            };
            var primeira = executor.submit(tarefa);
            var segunda = executor.submit(tarefa);
            assertThat(java.util.List.of(primeira.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("1:false", "1:true");
        }
    }

    @Test
    void rejeitaCamposInvalidosEPayloadBruto() {
        for (var dados : java.util.List.of(EVENTO.replace("CANCELADO", "DESCONHECIDO"),
                EVENTO.replace("\"clinica_id\":2,", ""), EVENTO.replace("teste-1", ""),
                EVENTO.replace("00000000-0000-4000-8000-000000000001", "invalido"),
                EVENTO.replace("\"lead_id\":\"00000000-0000-4000-8000-000000000001\",", ""),
                EVENTO.replace("{", "{\"darwin_payload\":{},"), "[]")) {
            assertThatThrownBy(() -> registrar(dados, "invalido")).isInstanceOf(SQLException.class);
        }
        assertThatThrownBy(() -> registrar(EVENTO, " ")).isInstanceOf(SQLException.class);
    }

    @Test
    void viewMostraSomenteCancelamentosSemExporPayload() throws Exception {
        registrar(EVENTO, "cancelado");
        registrar(EVENTO.replace("CANCELADO", "CONFIRMADO"), "confirmado");
        registrar(EVENTO.replace("CANCELADO", "REAGENDAMENTO_SOLICITADO"), "reagendamento");
        assertThat(consultar("SELECT classificacao FROM automacao_agendamentos.vw_cancelamentos_agendamento"))
                .isEqualTo("SEM_DESCRICAO");
        assertThat(consultar("SELECT count(*) FROM automacao_agendamentos.vw_cancelamentos_agendamento")).isEqualTo("1");
        assertThat(consultar("SELECT count(*) FROM information_schema.columns WHERE table_schema='automacao_agendamentos' AND column_name IN ('darwin_payload','atualizado_em')")).isEqualTo("0");
    }

    @Test
    void publicoNaoPodeLerNemExecutar() throws Exception {
        executar("DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='sem_acesso') THEN CREATE ROLE sem_acesso; END IF; END $$; GRANT USAGE ON SCHEMA automacao_agendamentos TO sem_acesso");
        assertThatThrownBy(() -> executar("SET ROLE sem_acesso; SELECT * FROM automacao_agendamentos.vw_cancelamentos_agendamento"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
        assertThatThrownBy(() -> executar("SET ROLE sem_acesso; SELECT automacao_agendamentos.registrar_evento('{}', 'x')"))
                .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("42501");
    }

    @Test
    void recusaBancoDoCrmEReinstalacao() throws Exception {
        registrar(EVENTO, "preservado");
        assertThatThrownBy(() -> executar(script())).isInstanceOf(SQLException.class);
        assertThat(consultar("SELECT count(*) FROM automacao_agendamentos.agendamento_evento")).isEqualTo("1");
        executar("DROP SCHEMA automacao_agendamentos CASCADE; CREATE TABLE public.lead(id integer)");
        assertThatThrownBy(() -> executar(script())).isInstanceOf(SQLException.class)
                .extracting("SQLState").isEqualTo("22023");
        assertThat(consultar("SELECT count(*) FROM pg_namespace WHERE nspname='automacao_agendamentos'")).isEqualTo("0");
    }
}
