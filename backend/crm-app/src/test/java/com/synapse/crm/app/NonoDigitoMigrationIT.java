package com.synapse.crm.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Executa a V50 sobre um banco parado na V49, com dados plantados a mao.
 *
 * <p>Esta e a unica prova possivel da fusao: ela apaga leads, e o que ela faz de errado nao volta.
 * Por isso os dois lados sao testados — o caminho feliz, que precisa mover tudo para o lado certo, e
 * os caminhos que precisam <b>abortar</b> em vez de adivinhar.
 *
 * <p>Herda de {@link PostgresIT} apenas para reaproveitar o container compartilhado: nao ha Spring
 * aqui, cada teste cria e destroi o proprio banco dentro do mesmo Postgres.
 */
class NonoDigitoMigrationIT extends PostgresIT {

    /** Um cliente so, nos dois formatos que a Meta e o cadastro manual produzem. */
    private static final String SEM_O_NONO = "556181536371";

    private static final String COM_O_NONO = "5561981536371";

    /** O atendimento vazio criado ao puxar o duplicado costuma ser o mais recente. */
    private static final Instant INICIO_ANTIGO = Instant.parse("2025-03-01T12:00:00Z");

    private static final Instant INICIO_RECENTE = Instant.parse("2026-08-31T12:10:00Z");

    private String banco;
    private String url;
    private JdbcTemplate jdbc;
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void criarBancoNaVersaoAnterior() throws Exception {
        banco = "nono_digito_" + UUID.randomUUID().toString().replace("-", "");
        executarNoBancoAdministrativo("CREATE DATABASE " + banco);
        url = url(banco);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword()));
        flyway(MigrationVersion.fromVersion("49")).migrate();

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
    @DisplayName("funde no lado que tem a conversa, com o dono desse lado, sem perder nada")
    void migration_conversaDeUmLadoCadastroVazioDoOutro_fundeNoLadoCerto() {
        // O lead que a Meta criou: 12 digitos, dono Ana, e a conversa inteira.
        UUID comConversa = criarLead("Adjair", SEM_O_NONO, ana);
        UUID atendimento = criarAtendimento(comConversa, ana);
        criarMensagem(atendimento);
        criarMensagem(atendimento);
        UUID evento = criarEventoDeTimeline(comConversa);
        UUID lembrete = criarLembrete(comConversa, ana);
        UUID programada = criarMensagemProgramada(comConversa, ana);
        UUID tagCompartilhada = criarTag("orcamento");
        UUID tagSoDoPerdedor = criarTag("obra");
        jdbc.update(
                "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", comConversa, tagCompartilhada);
        jdbc.update(
                "INSERT INTO audit_log (ator_tipo, acao, entidade_tipo, lead_id)"
                        + " VALUES ('SISTEMA', 'teste', 'Lead', ?)",
                comConversa);

        // O cadastro manual: 13 digitos, outro dono, sem conversa, mas com campos que o outro nao
        // tem. E com um nome melhor — que a regra manda descartar de proposito.
        UUID cadastroVazio = criarLead("Jair real 1814", COM_O_NONO, bruno);
        jdbc.update(
                "UPDATE lead SET email = ?, empresa = ?, codigo = ?, notas = ? WHERE id = ?",
                "jair@exemplo.invalid",
                "Vidracaria Jair",
                "1814",
                "cliente antigo",
                cadastroVazio);
        jdbc.update(
                "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", cadastroVazio, tagCompartilhada);
        jdbc.update(
                "INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?)", cadastroVazio, tagSoDoPerdedor);
        jdbc.update(
                "INSERT INTO audit_log (ator_tipo, acao, entidade_tipo, lead_id)"
                        + " VALUES ('SISTEMA', 'teste', 'Lead', ?)",
                cadastroVazio);

        flyway(null).migrate();

        assertThat(existe(cadastroVazio)).as("o perdedor e apagado").isFalse();
        assertThat(existe(comConversa)).as("sobrevive quem tem a conversa").isTrue();

        assertThat(coluna(comConversa, "telefone")).isEqualTo(COM_O_NONO);
        assertThat(coluna(comConversa, "nome"))
                .as("o nome nao e fundido: fica o do sobrevivente")
                .isEqualTo("Adjair");
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = ?",
                        UUID.class,
                        comConversa))
                .as("o dono e o do sobrevivente")
                .isEqualTo(ana);

        assertThat(coluna(comConversa, "email")).isEqualTo("jair@exemplo.invalid");
        assertThat(coluna(comConversa, "empresa")).isEqualTo("Vidracaria Jair");
        assertThat(coluna(comConversa, "codigo")).isEqualTo("1814");
        assertThat(coluna(comConversa, "notas"))
                .as("notas nao esta na lista de campos fundidos")
                .isNull();

        assertThat(donoDaLinha("atendimento", atendimento)).isEqualTo(comConversa);
        assertThat(contar(
                        "SELECT count(*) FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                                + " WHERE a.lead_id = ?",
                        comConversa))
                .as("nenhuma mensagem se perde")
                .isEqualTo(2);
        assertThat(donoDaLinha("evento_timeline", evento)).isEqualTo(comConversa);
        assertThat(donoDaLinha("lembrete", lembrete)).isEqualTo(comConversa);
        assertThat(donoDaLinha("mensagem_programada", programada)).isEqualTo(comConversa);
        assertThat(contar("SELECT count(*) FROM lead_tag WHERE lead_id = ?", comConversa))
                .as("a tag repetida nao duplica e a exclusiva do perdedor vem junto")
                .isEqualTo(2);
        assertThat(contar("SELECT count(*) FROM audit_log WHERE lead_id = ?", comConversa))
                .as("a auditoria do perdedor continua alcancavel pelo cliente")
                .isEqualTo(2);
    }

    /**
     * O caso perigoso medido em producao: os dois lados tem atendimento aberto e o vazio e o mais
     * recente. Sem a conversa unica, {@code app_atendimento_aberto_do_lead} devolveria o em branco.
     */
    @Test
    @DisplayName("dois abertos, o vazio e o mais recente: fica o que tem as mensagens")
    void migration_doisAbertosVazioMaisRecente_ficaOQueTemMensagens() {
        UUID comConversa = criarLead("Adjair", SEM_O_NONO, ana);
        UUID atendimentoCheio = criarAtendimento(comConversa, ana, INICIO_ANTIGO);
        criarMensagens(atendimentoCheio, 3);

        UUID cadastroNovo = criarLead("Jair puxado agora", COM_O_NONO, bruno);
        UUID atendimentoVazio = criarAtendimento(cadastroNovo, bruno, INICIO_RECENTE);

        flyway(null).migrate();

        assertThat(existe(cadastroNovo)).isFalse();
        assertThat(abertoDoLead(comConversa))
                .as("o aberto e o que tem as mensagens, nao o mais recente")
                .isEqualTo(atendimentoCheio);
        assertThat(statusDoAtendimento(atendimentoCheio)).isEqualTo("EM_ATENDIMENTO");
        assertThat(statusDoAtendimento(atendimentoVazio)).isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject(
                        "SELECT finalizado_em FROM atendimento WHERE id = ?",
                        Timestamp.class,
                        atendimentoVazio))
                .isNotNull();
        assertThat(contar("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", atendimentoCheio))
                .isEqualTo(3);
    }

    /**
     * A mensagem de template que o atendente mandou continua no historico, num atendimento fechado.
     * Sem encerrar a participacao, o recorte da aba Todos (E106) traria essa conversa vazia de
     * volta para quem so participava.
     */
    @Test
    @DisplayName("atendimento finalizado pela fusao mantem mensagens e encerra a participacao")
    void migration_atendimentoFinalizado_mantemMensagensEEncerraParticipacao() {
        UUID comConversa = criarLead("Adjair", SEM_O_NONO, ana);
        UUID atendimentoCheio = criarAtendimento(comConversa, ana, INICIO_ANTIGO);
        criarMensagens(atendimentoCheio, 3);

        UUID cadastroNovo = criarLead("Jair puxado agora", COM_O_NONO, bruno);
        UUID atendimentoComTemplate = criarAtendimento(cadastroNovo, bruno, INICIO_RECENTE);
        criarMensagem(atendimentoComTemplate);
        criarParticipante(atendimentoComTemplate, bruno);

        flyway(null).migrate();

        assertThat(statusDoAtendimento(atendimentoComTemplate)).isEqualTo("FINALIZADO");
        assertThat(contar("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", atendimentoComTemplate))
                .as("a mensagem de template permanece no atendimento fechado")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT saiu_em FROM atendimento_participante"
                                + " WHERE atendimento_id = ? AND usuario_id = ?",
                        Timestamp.class,
                        atendimentoComTemplate,
                        bruno))
                .as("a participacao e encerrada junto")
                .isNotNull();
        assertThat(contar(
                        "SELECT count(*) FROM atendimento a JOIN lead l ON l.id = a.lead_id"
                                + " WHERE a.id = ? AND (l.atendente_responsavel_id = ?"
                                + " OR EXISTS (SELECT 1 FROM atendimento_participante p"
                                + " WHERE p.atendimento_id = a.id AND p.usuario_id = ?"
                                + " AND p.saiu_em IS NULL))",
                        atendimentoComTemplate,
                        bruno,
                        bruno))
                .as("o recorte da aba Todos nao traz o atendimento finalizado para quem so participava")
                .isZero();
        assertThat(abertoDoLead(comConversa)).isEqualTo(atendimentoCheio);
    }

    @Test
    @DisplayName("so um lado tem atendimento aberto: nada e finalizado")
    void migration_soUmAberto_naoFinalizaNada() {
        UUID comConversa = criarLead("Adjair", SEM_O_NONO, ana);
        UUID atendimento = criarAtendimento(comConversa, ana, INICIO_ANTIGO);
        criarMensagem(atendimento);
        criarLead("Cadastro vazio", COM_O_NONO, bruno);

        flyway(null).migrate();

        assertThat(statusDoAtendimento(atendimento)).isEqualTo("EM_ATENDIMENTO");
        assertThat(jdbc.queryForObject(
                        "SELECT finalizado_em FROM atendimento WHERE id = ?", Timestamp.class, atendimento))
                .isNull();
        assertThat(abertoDoLead(comConversa)).isEqualTo(atendimento);
        assertThat(contar("SELECT count(*) FROM atendimento WHERE status = 'FINALIZADO'")).isZero();
    }

    @Test
    @DisplayName("nenhum lado tem atendimento aberto: a fusao roda e nao finaliza nada")
    void migration_nenhumAberto_fundeENaoFinaliza() {
        UUID maisAntigo = criarLead("Adjair", SEM_O_NONO, ana);
        UUID maisNovo = criarLead("Jair", COM_O_NONO, bruno);
        jdbc.update(
                "UPDATE lead SET criado_em = ? WHERE id = ?",
                Timestamp.from(INICIO_ANTIGO),
                maisAntigo);
        jdbc.update(
                "UPDATE lead SET criado_em = ? WHERE id = ?",
                Timestamp.from(INICIO_RECENTE),
                maisNovo);

        flyway(null).migrate();

        assertThat(existe(maisAntigo)).isTrue();
        assertThat(existe(maisNovo)).isFalse();
        assertThat(contar("SELECT count(*) FROM atendimento")).isZero();
        assertThat(abertoDoLead(maisAntigo)).isNull();
    }

    @Test
    @DisplayName("normaliza quem nao tem par e nao toca em fixo nem em numero de outro pais")
    void migration_semPar_normalizaSoOQueEhCelularBrasileiro() {
        UUID celular = criarLead("Celular sem o nono", "556198430401", null);
        UUID fixo = criarLead("Fixo de Brasilia", "556132241234", null);
        UUID portugues = criarLead("Contato de Lisboa", "351219999999", null);
        UUID jaCanonico = criarLead("Ja canonico", "5561999990000", null);

        flyway(null).migrate();

        assertThat(coluna(celular, "telefone")).isEqualTo("5561998430401");
        assertThat(coluna(fixo, "telefone")).isEqualTo("556132241234");
        assertThat(coluna(portugues, "telefone")).isEqualTo("351219999999");
        assertThat(coluna(jaCanonico, "telefone")).isEqualTo("5561999990000");
    }

    /**
     * O caso que a regra nao preve. Abortar derruba o deploy, e e o comportamento certo: a aplicacao
     * nao subir e recuperavel, fundir o cliente errado nao e.
     *
     * <p>O trio precisa da {@code ck_lead_telefone_canonico} derrubada para ser plantado, e isso e
     * informacao, nao truque de teste: com a constraint da V26 no lugar, so existem dois formatos
     * capazes de convergir para o mesmo canonico, e um trio de verdade exigiria escrita SQL lateral
     * que a constraint hoje impede. O guarda continua valendo a pena porque e ele quem sustenta essa
     * afirmacao caso a constraint mude.
     */
    @Test
    @DisplayName("aborta quando ha mais de dois leads no mesmo telefone canonico")
    void migration_trioNoMesmoFinal_aborta() {
        UUID primeiro = criarLead("Meta", SEM_O_NONO, null);
        UUID segundo = criarLead("Cadastro", COM_O_NONO, null);
        jdbc.execute("ALTER TABLE lead DROP CONSTRAINT ck_lead_telefone_canonico");
        UUID terceiro = criarLead("Local", "6181536371", null);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Mais de dois leads no mesmo telefone canonico");

        assertThat(coluna(primeiro, "telefone")).isEqualTo(SEM_O_NONO);
        assertThat(coluna(segundo, "telefone")).isEqualTo(COM_O_NONO);
        assertThat(coluna(terceiro, "telefone")).isEqualTo("6181536371");
        assertThat(existe(primeiro)).isTrue();
        assertThat(existe(segundo)).isTrue();
        assertThat(existe(terceiro)).isTrue();
    }

    @Test
    @DisplayName("aborta quando o assinante comeca em digito que nao existe no plano brasileiro")
    void migration_assinanteImpossivel_aborta() {
        UUID fora = criarLead("Numero impossivel", "556101111111", null);

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("Telefone fora dos formatos previstos")
                .hasStackTraceContaining("556101111111");

        assertThat(coluna(fora, "telefone")).isEqualTo("556101111111");
    }

    /**
     * A migration levanta do catalogo o que aponta para lead. Uma FK nova que ninguem previu deixaria
     * linha orfa ou estouraria no meio do deploy — este teste cria uma de proposito.
     */
    @Test
    @DisplayName("aborta quando aparece uma FK para lead que ela nao preve")
    void migration_fkNaoPrevista_aborta() {
        criarLead("Meta", SEM_O_NONO, null);
        criarLead("Cadastro", COM_O_NONO, null);
        jdbc.execute("CREATE TABLE anexo_do_lead (id UUID PRIMARY KEY, lead_id UUID NOT NULL"
                + " REFERENCES lead(id))");

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("FK apontando para lead que esta migration nao preve")
                .hasStackTraceContaining("anexo_do_lead");
    }

    /** Prova o guarda de RLS: sem contexto de servico a migration para em vez de fundir nada. */
    @Test
    @DisplayName("aborta quando o contexto de servico nao chega ao banco")
    void migration_semContextoDeServico_aborta() {
        criarLead("Meta", SEM_O_NONO, null);
        // app_enxerga_todos_os_leads() passa a responder FALSE mesmo com app.papel = 'SERVICO'. E a
        // forma de exercitar o guarda sem precisar de um Postgres onde o dono nao seja superusuario;
        // o efeito e o mesmo que la: a migration enxergaria zero leads.
        jdbc.execute("CREATE OR REPLACE FUNCTION app_enxerga_todos_os_leads()"
                + " RETURNS BOOLEAN LANGUAGE sql STABLE AS $$ SELECT FALSE $$");

        assertThatThrownBy(() -> flyway(null).migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("contexto de servico nao aplicado");
    }

    // --- apoio ----------------------------------------------------------------

    private Flyway flyway(MigrationVersion alvo) {
        var configuracao = Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("telefone_ddi_padrao", "55"));
        if (alvo != null) {
            configuracao.target(alvo);
        }
        return configuracao.load();
    }

    private UUID criarUsuario(String apelido) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel)"
                        + " VALUES (?, ?, ?, 'nao-utilizado', 'ATENDENTE')",
                id,
                apelido,
                apelido + "@dev.invalid");
        return id;
    }

    private UUID criarLead(String nome, String telefone, UUID dono) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico, atendente_responsavel_id)"
                        + " VALUES (?, ?, ?, ?::status_basico_lead, ?)",
                id,
                nome,
                telefone,
                dono == null ? "IA" : "EM_ATENDIMENTO",
                dono);
        return id;
    }

    private UUID criarAtendimento(UUID leadId, UUID atendenteId) {
        return criarAtendimento(leadId, atendenteId, Instant.now());
    }

    private UUID criarAtendimento(UUID leadId, UUID atendenteId, Instant iniciadoEm) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO', ?)",
                id,
                leadId,
                atendenteId,
                Timestamp.from(iniciadoEm));
        return id;
    }

    private void criarMensagens(UUID atendimentoId, int quantas) {
        for (int i = 0; i < quantas; i++) {
            criarMensagem(atendimentoId);
        }
    }

    private void criarParticipante(UUID atendimentoId, UUID usuarioId) {
        jdbc.update(
                "INSERT INTO atendimento_participante (atendimento_id, usuario_id) VALUES (?, ?)",
                atendimentoId,
                usuarioId);
    }

    private UUID abertoDoLead(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT app_atendimento_aberto_do_lead(?)", UUID.class, leadId);
    }

    private String statusDoAtendimento(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT status::text FROM atendimento WHERE id = ?", String.class, atendimentoId);
    }

    private void criarMensagem(UUID atendimentoId) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo)"
                        + " VALUES (?, ?, 'LEAD', 'TEXTO', 'bom dia')",
                UUID.randomUUID(),
                atendimentoId);
    }

    private UUID criarEventoDeTimeline(UUID leadId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO evento_timeline (id, lead_id, tipo, descricao, origem)"
                        + " VALUES (?, ?, 'TESTE', 'evento de teste', 'SISTEMA')",
                id,
                leadId);
        return id;
    }

    private UUID criarLembrete(UUID leadId, UUID atendenteId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lembrete (id, lead_id, atendente_id, texto, data_hora)"
                        + " VALUES (?, ?, ?, 'ligar', now())",
                id,
                leadId,
                atendenteId);
        return id;
    }

    private UUID criarMensagemProgramada(UUID leadId, UUID atendenteId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO mensagem_programada (id, lead_id, atendente_id, conteudo, data_envio)"
                        + " VALUES (?, ?, ?, 'lembrete', now())",
                id,
                leadId,
                atendenteId);
        return id;
    }

    private UUID criarTag(String nome) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO tag (id, nome, cor) VALUES (?, ?, '#000000')", id, nome);
        return id;
    }

    private boolean existe(UUID leadId) {
        return contar("SELECT count(*) FROM lead WHERE id = ?", leadId) == 1;
    }

    private String coluna(UUID leadId, String nome) {
        return jdbc.queryForObject("SELECT " + nome + " FROM lead WHERE id = ?", String.class, leadId);
    }

    private UUID donoDaLinha(String tabela, UUID id) {
        return jdbc.queryForObject("SELECT lead_id FROM " + tabela + " WHERE id = ?", UUID.class, id);
    }

    private int contar(String sql, Object... argumentos) {
        Integer total = jdbc.queryForObject(sql, Integer.class, argumentos);
        return total == null ? 0 : total;
    }

    private void executarNoBancoAdministrativo(String sql) throws Exception {
        try (Connection conexao = DriverManager.getConnection(
                        url("postgres"), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private static String url(String banco) {
        return "jdbc:postgresql://"
                + POSTGRES.getHost()
                + ":"
                + POSTGRES.getMappedPort(5432)
                + "/"
                + banco;
    }
}
