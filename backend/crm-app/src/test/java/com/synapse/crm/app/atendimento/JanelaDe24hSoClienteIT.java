package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.IniciarNovoContatoUseCase;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.ResponderAtendimentoDaAutomacaoUseCase;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * E121: a janela de 24h so conta mensagem do cliente.
 *
 * <p>Antes, {@code contatoParaEnvio} lia {@code ultima_interacao_em}, avancado tambem em saida —
 * enquanto a equipe falava, a janela nunca fechava. Agora a fonte unica e
 * {@code lead.ultima_mensagem_do_lead_em}, escrita so por {@link RegistrarMensagemRecebidaUseCase},
 * e o cartao do painel le a mesma coluna (a LATERAL da E114 saiu).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class JanelaDe24hSoClienteIT extends PostgresIT {

    private static final String PREFIXO = "E121-janela-";
    private static final Duration JANELA = Duration.ofHours(24);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CanalGateway canal;

    @Autowired
    private EnviarMensagemUseCase enviar;

    @Autowired
    private IniciarNovoContatoUseCase iniciar;

    @Autowired
    private ResponderAtendimentoDaAutomacaoUseCase responderIa;

    @Autowired
    private RegistrarMensagemRecebidaUseCase registrarRecebida;

    @AfterEach
    void limpar() {
        ApoioRls.sair();
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM outbox_evento WHERE payload->>'leadId' IN"
                        + " (SELECT id::text FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("1: cliente ha 1h + 10 saidas do atendente -> janela aberta")
    void clienteHaUmaHora_dezSaidas_janelaAberta() {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();
        UUID lead = criarLead("aberta", ana, null);
        UUID atendimento = criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(1)));
        inserirMensagemLead(atendimento, lead, agora.minus(Duration.ofHours(1)));
        for (int i = 0; i < 10; i++) {
            inserirMensagemSaida(atendimento, ana, agora.minus(Duration.ofMinutes(50 - i)));
        }

        assertThat(aceitaPelaFonte(lead, agora)).isTrue();
        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThatCode(() -> enviar.executar(lead, "ainda dentro")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("2: cliente ha 30h + saida ha 1h -> janela fechada (o bug de hoje)")
    void clienteHa30h_saidaRecente_janelaFechada() {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();
        UUID lead = criarLead("fechada", ana, null);
        UUID atendimento = criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(31)));
        inserirMensagemLead(atendimento, lead, agora.minus(Duration.ofHours(30)));
        inserirMensagemSaida(atendimento, ana, agora.minus(Duration.ofHours(1)));

        assertThat(coluna(lead, "ultima_interacao_em")).isNotNull();
        assertThat(coluna(lead, "ultima_mensagem_do_lead_em")).isBefore(agora.minus(JANELA));
        assertThat(aceitaPelaFonte(lead, agora)).isFalse();

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThatThrownBy(() -> enviar.executar(lead, "deveria barrar"))
                .isInstanceOf(ForaDaJanelaException.class);
    }

    @Test
    @DisplayName("3: lead que nunca escreveu -> janela inexistente, template obrigatorio")
    void leadQueNuncaEscreveu_inexistente() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();
        UUID lead = criarLead("importado", ana, null);
        criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora);

        assertThat(coluna(lead, "ultima_mensagem_do_lead_em")).isNull();
        assertThat(aceitaPelaFonte(lead, agora)).isFalse();
        assertThat(estadoDoCartao(lead, agora)).isEqualTo("inexistente");
    }

    @Test
    @DisplayName("4: mensagem do cliente reabre; IA e atendente nao")
    void soMensagemDoClienteReabre() {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();
        UUID lead = criarLead("reabre", ana, null);
        UUID atendimento = criarAtendimento(lead, ana, "EM_IA", agora.minus(Duration.ofHours(2)));

        assertThat(aceitaPelaFonte(lead, agora)).isFalse();

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThatThrownBy(() -> enviar.executar(lead, "antes do cliente"))
                .isInstanceOf(ForaDaJanelaException.class);
        ApoioRls.sair();

        ContextoDeServico.executarComo("e121", () -> assertThatThrownBy(
                        () -> responderIa.executar(atendimento, "ia sem janela"))
                .isInstanceOf(ForaDaJanelaException.class));

        ContextoDeServico.executarComo(
                "e121",
                () -> registrarRecebida.executar(
                        new RegistrarMensagemRecebidaUseCase.MensagemRecebida(
                                lead, null, null, "cliente voltou")));

        Instant soCliente = coluna(lead, "ultima_mensagem_do_lead_em");
        assertThat(soCliente).isNotNull();
        assertThat(aceitaPelaFonte(lead, Instant.now())).isTrue();

        Instant interacaoAntes = coluna(lead, "ultima_interacao_em");
        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        enviar.executar(lead, "resposta humana");
        ApoioRls.sair();

        assertThat(coluna(lead, "ultima_mensagem_do_lead_em")).isEqualTo(soCliente);
        assertThat(coluna(lead, "ultima_interacao_em")).isAfter(interacaoAntes);
    }

    @Test
    @DisplayName("5: EnviarMensagem, IniciarNovoContato e ResponderAutomacao concordam")
    void tresCaminhosDeEnvioConcordam() {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();

        UUID leadAberto = criarLead("concordancia-aberto", ana, null);
        UUID atAberto = criarAtendimento(leadAberto, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(1)));
        inserirMensagemLead(atAberto, leadAberto, agora.minus(Duration.ofHours(1)));

        UUID leadFechado = criarLead("concordancia-fechado", ana, null);
        UUID atFechado =
                criarAtendimento(leadFechado, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(40)));
        inserirMensagemLead(atFechado, leadFechado, agora.minus(Duration.ofHours(30)));
        inserirMensagemSaida(atFechado, ana, agora.minus(Duration.ofHours(1)));

        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        assertThat(aceitaPelaFonte(leadAberto, agora)).isTrue();
        assertThat(aceitaPelaFonte(leadFechado, agora)).isFalse();

        assertThatCode(() -> enviar.executar(leadAberto, "ok aberto")).doesNotThrowAnyException();
        assertThatThrownBy(() -> enviar.executar(leadFechado, "deve falhar"))
                .isInstanceOf(ForaDaJanelaException.class);

        String telefoneAberto =
                jdbc.queryForObject("SELECT telefone FROM lead WHERE id = ?", String.class, leadAberto);
        String telefoneFechado =
                jdbc.queryForObject("SELECT telefone FROM lead WHERE id = ?", String.class, leadFechado);

        assertThatCode(() -> iniciar.executar(new IniciarNovoContatoUseCase.Pedido(
                        "mesmo aberto", telefoneAberto, "ainda aberto", null)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> iniciar.executar(new IniciarNovoContatoUseCase.Pedido(
                        "mesmo fechado", telefoneFechado, "texto fora", null)))
                .isInstanceOf(ForaDaJanelaException.class);
        ApoioRls.sair();

        UUID leadIa = criarLead("concordancia-ia", null, null);
        UUID atIa = criarAtendimento(leadIa, null, "EM_IA", agora.minus(Duration.ofHours(1)));
        inserirMensagemLead(atIa, leadIa, agora.minus(Duration.ofHours(1)));
        ContextoDeServico.executarComo(
                "e121",
                () -> assertThatCode(() -> responderIa.executar(atIa, "ia dentro"))
                        .doesNotThrowAnyException());

        UUID leadIaFechado = criarLead("concordancia-ia-fechado", null, null);
        UUID atIaFechado =
                criarAtendimento(leadIaFechado, null, "EM_IA", agora.minus(Duration.ofHours(40)));
        inserirMensagemLead(atIaFechado, leadIaFechado, agora.minus(Duration.ofHours(30)));
        ContextoDeServico.executarComo(
                "e121",
                () -> assertThatThrownBy(() -> responderIa.executar(atIaFechado, "ia fora"))
                        .isInstanceOf(ForaDaJanelaException.class));
    }

    @Test
    @DisplayName("6: cartao da tela e aceitaTextoLivre nunca se contradizem")
    void concordanciaTelaEEnvio() throws Exception {
        assertThat(canal.provedor()).isEqualTo("meta-cloud");
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();

        UUID aberto = criarLead("tela-aberta", ana, null);
        UUID atAberto = criarAtendimento(aberto, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(2)));
        inserirMensagemLead(atAberto, aberto, agora.minus(Duration.ofHours(1)));

        UUID fechado = criarLead("tela-fechada", ana, null);
        UUID atFechado = criarAtendimento(fechado, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(40)));
        inserirMensagemLead(atFechado, fechado, agora.minus(Duration.ofHours(30)));

        UUID inexistente = criarLead("tela-inexistente", ana, null);
        criarAtendimento(inexistente, ana, "EM_ATENDIMENTO", agora);

        for (UUID lead : List.of(aberto, fechado, inexistente)) {
            String estadoTela = estadoDoCartao(lead, agora);
            boolean telaLibera = "aberta".equals(estadoTela);
            boolean backendAceita = aceitaPelaFonte(lead, agora);
            assertThat(telaLibera)
                    .as("lead %s tela=%s", lead, estadoTela)
                    .isEqualTo(backendAceita);
        }
    }

    @Test
    @DisplayName("7: semRetornoDias continua lendo ultima_interacao_em (saida conta)")
    void semRetornoDiasNaoMudouDeSignificado() {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();
        UUID lead = criarLead("agenda", ana, null);
        UUID atendimento = criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofDays(40)));
        inserirMensagemLead(atendimento, lead, agora.minus(Duration.ofDays(40)));
        Instant saida = agora.minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.MICROS);
        inserirMensagemSaida(atendimento, ana, saida);

        Instant interacao = coluna(lead, "ultima_interacao_em");
        Instant doCliente = coluna(lead, "ultima_mensagem_do_lead_em");
        // Postgres timestamptz guarda micros; Instant.now() traz nanos — truncar na entrada.
        assertThat(interacao).isEqualTo(saida);
        assertThat(doCliente).isBefore(agora.minus(JANELA));
        assertThat(aceitaPelaFonte(lead, agora)).isFalse();

        Instant limiar30 = agora.minus(Duration.ofDays(30));
        Instant baseFiltro = jdbc.queryForObject(
                        "SELECT COALESCE(ultima_interacao_em, criado_em) FROM lead WHERE id = ?",
                        Timestamp.class,
                        lead)
                .toInstant();
        assertThat(baseFiltro).isAfter(limiar30);
    }

    @Test
    @DisplayName("8: backfill preenche a ultima LEAD; rodar de novo nao altera")
    void backfillIdempotente() {
        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID lead = criarLead("backfill", ana, null);
        UUID atendimento = criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(5)));
        Instant ultimaLead = agora.minus(Duration.ofHours(3));
        Instant anterior = agora.minus(Duration.ofHours(4));
        inserirMensagemLeadSemColuna(atendimento, anterior);
        inserirMensagemLeadSemColuna(atendimento, ultimaLead);
        inserirMensagemSaida(atendimento, ana, agora.minus(Duration.ofHours(1)));

        jdbc.update("UPDATE lead SET ultima_mensagem_do_lead_em = NULL WHERE id = ?", lead);
        assertThat(coluna(lead, "ultima_mensagem_do_lead_em")).isNull();

        rodarBackfill();
        Instant depois = coluna(lead, "ultima_mensagem_do_lead_em");
        assertThat(depois).isEqualTo(ultimaLead);

        rodarBackfill();
        assertThat(coluna(lead, "ultima_mensagem_do_lead_em")).isEqualTo(depois);
    }

    private boolean aceitaPelaFonte(UUID leadId, Instant agora) {
        return canal.aceitaTextoLivre(Optional.ofNullable(coluna(leadId, "ultima_mensagem_do_lead_em")), agora);
    }

    private String estadoDoCartao(UUID leadId, Instant agora) throws Exception {
        Instant ultima = ultimaMensagemDoLeadDoCartao(leadId);
        if (ultima == null) {
            return "inexistente";
        }
        return ultima.isAfter(agora.minus(JANELA)) ? "aberta" : "fechada";
    }

    private Instant ultimaMensagemDoLeadDoCartao(UUID leadId) throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        String corpo = ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=ATIVOS", String.class)
                .getBody();
        for (JsonNode cartao : json.readTree(corpo)) {
            if (leadId.toString().equals(cartao.path("leadId").asText())) {
                JsonNode campo = cartao.path("ultimaMensagemDoLeadEm");
                return campo.isMissingNode() || campo.isNull() ? null : Instant.parse(campo.asText());
            }
        }
        throw new AssertionError("cartao nao encontrado: " + leadId + " corpo=" + corpo);
    }

    private void rodarBackfill() {
        jdbc.execute("SELECT set_config('app.papel', 'SERVICO', TRUE)");
        jdbc.update(
                """
                UPDATE lead l
                   SET ultima_mensagem_do_lead_em = origem.ultima
                  FROM (
                        SELECT a.lead_id AS lead_id, max(m.enviado_em) AS ultima
                          FROM mensagem m
                          JOIN atendimento a ON a.id = m.atendimento_id
                         WHERE m.remetente_tipo = 'LEAD'
                         GROUP BY a.lead_id
                       ) origem
                 WHERE l.id = origem.lead_id
                   AND l.nome LIKE ?
                """,
                PREFIXO + "%");
    }

    private Instant coluna(UUID leadId, String nome) {
        Timestamp valor = jdbc.queryForObject(
                "SELECT " + nome + " FROM lead WHERE id = ?", Timestamp.class, leadId);
        return valor == null ? null : valor.toInstant();
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono, Instant ultimaMensagemDoLead) {
        UUID id = UUID.randomUUID();
        String telefone =
                "55619912" + String.format("%05d", Math.floorMod(id.getLeastSignificantBits(), 100_000));
        jdbc.update(
                """
                INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico,
                                  ultima_mensagem_do_lead_em)
                VALUES (?, ?, ?, ?, ?::status_basico_lead, ?)
                """,
                id,
                PREFIXO + nome + "-" + id,
                telefone,
                dono,
                dono == null ? "IA" : "EM_ATENDIMENTO",
                ultimaMensagemDoLead == null ? null : Timestamp.from(ultimaMensagemDoLead));
        return id;
    }

    private UUID criarAtendimento(UUID leadId, UUID atendenteId, String status, Instant iniciadoEm) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, ?::status_atendimento, ?)",
                id,
                leadId,
                atendenteId,
                status,
                Timestamp.from(iniciadoEm));
        return id;
    }

    private void inserirMensagemLead(UUID atendimentoId, UUID leadId, Instant enviadoEm) {
        Instant quando = enviadoEm.truncatedTo(ChronoUnit.MICROS);
        inserirMensagemLeadSemColuna(atendimentoId, quando);
        jdbc.update(
                """
                UPDATE lead
                   SET ultima_mensagem_do_lead_em =
                       GREATEST(COALESCE(ultima_mensagem_do_lead_em, ?), ?),
                       ultima_interacao_em =
                       GREATEST(COALESCE(ultima_interacao_em, ?), ?)
                 WHERE id = ?
                """,
                Timestamp.from(quando),
                Timestamp.from(quando),
                Timestamp.from(quando),
                Timestamp.from(quando),
                leadId);
    }

    private void inserirMensagemLeadSemColuna(UUID atendimentoId, Instant enviadoEm) {
        Instant quando = enviadoEm.truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo,"
                        + " status_entrega, enviado_em)"
                        + " VALUES (?, ?, 'LEAD'::remetente_tipo, 'TEXTO'::tipo_mensagem, ?,"
                        + " 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                "msg cliente",
                Timestamp.from(quando));
    }

    private void inserirMensagemSaida(UUID atendimentoId, UUID atendenteId, Instant enviadoEm) {
        Instant quando = enviadoEm.truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,"
                        + " conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, 'ATENDENTE'::remetente_tipo, ?, 'TEXTO'::tipo_mensagem, ?,"
                        + " 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                atendenteId,
                "msg saida",
                Timestamp.from(quando));
        jdbc.update(
                """
                UPDATE lead
                   SET ultima_interacao_em =
                       GREATEST(COALESCE(ultima_interacao_em, ?), ?),
                       num_mensagens = num_mensagens + 1
                 WHERE id = (SELECT lead_id FROM atendimento WHERE id = ?)
                """,
                Timestamp.from(quando),
                Timestamp.from(quando),
                atendimentoId);
    }
}
