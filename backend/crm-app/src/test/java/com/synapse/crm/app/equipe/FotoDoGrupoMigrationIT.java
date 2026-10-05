package com.synapse.crm.app.equipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.synapse.crm.app.PostgresIT;

/**
 * Executa a V94 sobre um banco parado na V93 com grupos reais plantados: e a unica prova de que o
 * backfill do criador acerta quem criou, de que a migration nao tira privilegio que a V65 concedeu
 * (o trigger de citacoes depende dele) e de que as restricoes da foto valem.
 *
 * <p>Herda de {@link PostgresIT} so para reaproveitar o container; sem Spring, cada teste cria e
 * destroi o proprio banco dentro dele.
 */
class FotoDoGrupoMigrationIT extends PostgresIT {

    private String banco;
    private String url;
    private JdbcTemplate jdbc;
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "foto_grupo_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway("93").migrate();
        ana = criarUsuario("ana");
        bruno = criarUsuario("bruno");
    }

    @AfterEach
    void removerBanco() throws Exception {
        if (banco != null) {
            executarNoBancoAdministrativo("DROP DATABASE IF EXISTS " + banco + " WITH (FORCE)");
        }
    }

    @Test
    @DisplayName("backfill: o criador e o autor da mensagem GRUPO_CRIADO, nao de quem agiu depois; sem rastro ou direta ficam nulos")
    void backfill_criadorVemDaMensagemDeCriacao() {
        UUID grupoDaAna = criarGrupo("Ops", ana, bruno);
        mensagemDeSistema(grupoDaAna, ana, "{\"evento\":\"GRUPO_CRIADO\",\"nome\":\"Ops\"}", "2026-09-01T10:00:00Z");
        mensagemDeSistema(grupoDaAna, bruno, "{\"evento\":\"NOME_ALTERADO\",\"nome\":\"Ops 2\"}", "2026-09-02T10:00:00Z");

        UUID grupoDoBruno = criarGrupo("Vendas", bruno, ana);
        mensagemDeSistema(grupoDoBruno, bruno, "{\"evento\":\"GRUPO_CRIADO\",\"nome\":\"Vendas\"}", "2026-09-03T10:00:00Z");

        UUID grupoSemRastro = criarGrupo("Antigo", ana, bruno);
        UUID direta = criarConversa("DIRETA", null, ana, bruno);

        flyway("94").migrate();

        assertThat(criador(grupoDaAna)).isEqualTo(ana);
        assertThat(criador(grupoDoBruno)).isEqualTo(bruno);
        assertThat(criador(grupoSemRastro)).as("sem mensagem de criacao nao se adivinha criador").isNull();
        assertThat(criador(direta)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_interno_conversa WHERE foto_referencia IS NOT NULL", Integer.class))
                .as("nenhuma foto e inventada pelo backfill").isZero();
    }

    @Test
    @DisplayName("a V94 nao tira de synapse_chat_rls o SELECT/UPDATE que a V65 concedeu: excluir mensagem continua funcionando")
    void privilegiosDaV65Preservados() {
        flyway("94").migrate();

        for (String privilegio : new String[] {"SELECT", "UPDATE"}) {
            assertThat(jdbc.queryForObject(
                            "SELECT has_table_privilege('synapse_chat_rls', 'chat_interno_mensagem', ?)", Boolean.class, privilegio))
                    .as(privilegio + " em chat_interno_mensagem").isTrue();
        }
        UUID grupo = criarGrupo("Excluir", ana, bruno);
        UUID mensagem = mensagemDeTexto(grupo, ana);
        UUID citacao = mensagemDeTexto(grupo, bruno);
        jdbc.update(
                "UPDATE chat_interno_mensagem SET referencia_origem_id = ?, referencia_tipo = 'RESPOSTA',"
                        + " referencia_autor = 'Ana', referencia_tipo_conteudo = 'TEXTO', referencia_previa = 'oi'"
                        + " WHERE id = ?",
                mensagem, citacao);

        // O DELETE dispara o trigger SECURITY DEFINER, que atualiza as citacoes com o privilegio do dono.
        jdbc.update("DELETE FROM chat_interno_mensagem WHERE id = ?", mensagem);

        assertThat(jdbc.queryForObject(
                "SELECT referencia_origem_removida FROM chat_interno_mensagem WHERE id = ?", Boolean.class, citacao)).isTrue();
    }

    @Test
    @DisplayName("restricoes: foto so em grupo, e referencia e versao andam juntas")
    void restricoesDaFoto() {
        flyway("94").migrate();
        UUID grupo = criarGrupo("Fotos", ana, bruno);
        UUID direta = criarConversa("DIRETA", null, ana, bruno);

        jdbc.update("UPDATE chat_interno_conversa SET foto_referencia = 'grupo/a.png', foto_atualizada_em = now() WHERE id = ?", grupo);
        assertThat(jdbc.queryForObject("SELECT foto_referencia FROM chat_interno_conversa WHERE id = ?", String.class, grupo))
                .isEqualTo("grupo/a.png");

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE chat_interno_conversa SET foto_referencia = 'grupo/b.png', foto_atualizada_em = now() WHERE id = ?", direta))
                .isInstanceOf(DataIntegrityViolationException.class);
        UUID semFoto = criarGrupo("Sem foto", ana, bruno);
        assertThatThrownBy(() -> jdbc.update("UPDATE chat_interno_conversa SET foto_referencia = 'grupo/c.png' WHERE id = ?", semFoto))
                .as("referencia sem versao").isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE chat_interno_conversa SET foto_atualizada_em = NULL WHERE id = ?", grupo))
                .as("versao some, referencia fica").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("grupo novo criado pela funcao de bootstrap ja nasce com o criador; usuario apagado deixa o criador nulo")
    void funcaoDeCriacaoRegistraOCriador() throws Exception {
        flyway("94").migrate();

        UUID grupo;
        try (Connection conexao = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute("SELECT set_config('app.usuario_id', '" + ana + "', false)");
            try (var resultado = comando.executeQuery(
                    "SELECT app_criar_conversa_grupo('Novo', ARRAY['" + ana + "','" + bruno + "']::uuid[])")) {
                resultado.next();
                grupo = resultado.getObject(1, UUID.class);
            }
        }

        assertThat(criador(grupo)).isEqualTo(ana);

        jdbc.update("DELETE FROM chat_interno_participante WHERE usuario_id = ?", ana);
        jdbc.update("DELETE FROM usuario WHERE id = ?", ana);
        assertThat(criador(grupo)).as("ON DELETE SET NULL").isNull();
    }

    // --- apoio -------------------------------------------------------------------------------------

    private UUID criador(UUID conversa) {
        return jdbc.queryForObject("SELECT criado_por_id FROM chat_interno_conversa WHERE id = ?", UUID.class, conversa);
    }

    private UUID criarGrupo(String nome, UUID primeiro, UUID segundo) {
        return criarConversa("GRUPO", nome, primeiro, segundo);
    }

    private UUID criarConversa(String tipo, String nome, UUID primeiro, UUID segundo) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO chat_interno_conversa (id, tipo, nome) VALUES (?, ?::tipo_conversa_chat, ?)", id, tipo, nome);
        jdbc.update("INSERT INTO chat_interno_participante (conversa_id, usuario_id) VALUES (?, ?), (?, ?)",
                id, primeiro, id, segundo);
        return id;
    }

    private void mensagemDeSistema(UUID conversa, UUID autor, String conteudo, String quando) {
        jdbc.update(
                "INSERT INTO chat_interno_mensagem (id, conversa_id, remetente_id, tipo, conteudo, enviado_em)"
                        + " VALUES (?, ?, ?, 'SISTEMA', ?, ?::timestamptz)",
                UUID.randomUUID(), conversa, autor, conteudo, quando);
    }

    private UUID mensagemDeTexto(UUID conversa, UUID autor) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO chat_interno_mensagem (id, conversa_id, remetente_id, tipo, conteudo, enviado_em)"
                        + " VALUES (?, ?, ?, 'TEXTO', 'oi', ?::timestamptz)",
                id, conversa, autor, Instant.now().toString());
        return id;
    }

    private UUID criarUsuario(String apelido) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel) VALUES (?, ?, ?, 'nao-utilizado', 'ATENDENTE')",
                id, apelido, apelido + "@dev.invalid");
        return id;
    }

    private Flyway flyway(String alvo) {
        return Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("telefone_ddi_padrao", "55"))
                .target(MigrationVersion.fromVersion(alvo))
                .load();
    }

    private void executarNoBancoAdministrativo(String sql) throws Exception {
        try (Connection conexao = DriverManager.getConnection(url("postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private static String url(String banco) {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + banco;
    }
}
