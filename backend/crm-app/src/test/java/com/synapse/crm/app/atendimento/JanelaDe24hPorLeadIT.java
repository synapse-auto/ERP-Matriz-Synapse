package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import com.synapse.crm.atendimento.domain.canal.CanalGateway;

/**
 * E114: a janela de 24h da Meta e do <b>cliente</b>, nao do atendimento.
 *
 * <p>O sintoma era o composer exigir template a cada atendimento novo, mesmo com o cliente tendo
 * escrito minutos antes num atendimento ja finalizado. A causa vivia no cartao do painel: a coluna
 * {@code ultima_mensagem_do_lead_em} recortava por {@code atendimento_id}, entao atendimento novo
 * (sem mensagem do cliente ainda) devolvia nulo e a tela caia em {@code inexistente}.
 *
 * <p>Estes testes fixam a verdade que o cartao passa para a tela: a ultima mensagem do cliente
 * atravessa <b>todos os atendimentos do lead</b>. O caso do lead que nunca escreveu — inclusive o que
 * so <em>recebeu</em> mensagem — importa por causa dos contatos importados que nao podem aparecer com
 * janela aberta; ele tambem e a trava contra "consertar" isso usando {@code lead.ultima_interacao_em},
 * que avanca em envio de saida e mentiria janela aberta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class JanelaDe24hPorLeadIT extends PostgresIT {

    private static final String PREFIXO = "E114-janela-";
    private static final Duration JANELA = Duration.ofHours(24);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    /**
     * O gateway ativo no perfil dev e o {@code meta-cloud} — o unico que tem janela de 24h. O
     * {@link com.synapse.crm.app.canal.CanalFake} so entra quando um teste pede
     * {@code provedor=fake}, e a concordancia so faz sentido contra quem realmente aplica a janela.
     */
    @Autowired
    private CanalGateway canal;

    @AfterEach
    void limpar() {
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("cliente escreveu ha 1h, atendimento finalizado, novo atendimento sem mensagem -> cartao preenchido e janela aberta")
    void clienteEscreveuHaPoucoEmAtendimentoFinalizado_novoAtendimentoHerdaAJanela() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID lead = criarLead("herda janela", ana, "EM_ATENDIMENTO");
        Instant agora = Instant.now();

        UUID finalizado = criarAtendimento(lead, ana, "FINALIZADO", agora.minus(Duration.ofHours(2)));
        inserirMensagem(finalizado, "LEAD", null, "cliente falou", agora.minus(Duration.ofHours(1)));

        // Atendimento novo, aberto agora, ainda sem nenhuma mensagem.
        criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofMinutes(1)));

        Instant ultimaDoLead = ultimaMensagemDoLeadDoCartao(lead);

        assertThat(ultimaDoLead)
                .as("cartao deve devolver a ultima mensagem do cliente, mesmo vinda de outro atendimento | %s",
                        diagnosticoDoLead(lead))
                .isNotNull();
        assertThat(estadoDaJanela(ultimaDoLead, agora)).isEqualTo("aberta");
    }

    @Test
    @DisplayName("ultima mensagem do cliente tem 30h -> janela fechada mesmo com atendimento novo")
    void clienteEscreveuHa30h_janelaFechada() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID lead = criarLead("janela expirada", ana, "EM_ATENDIMENTO");
        Instant agora = Instant.now();

        UUID finalizado = criarAtendimento(lead, ana, "FINALIZADO", agora.minus(Duration.ofHours(31)));
        inserirMensagem(finalizado, "LEAD", null, "cliente falou ha muito", agora.minus(Duration.ofHours(30)));
        criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofMinutes(1)));

        Instant ultimaDoLead = ultimaMensagemDoLeadDoCartao(lead);

        assertThat(ultimaDoLead).as("%s", diagnosticoDoLead(lead)).isNotNull();
        assertThat(estadoDaJanela(ultimaDoLead, agora)).isEqualTo("fechada");
    }

    @Test
    @DisplayName("lead que nunca escreveu (importado/pela tela) -> janela inexistente")
    void leadQueNuncaEscreveu_inexistente() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID lead = criarLead("importado sem fala", ana, "EM_ATENDIMENTO");
        Instant agora = Instant.now();
        criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofMinutes(1)));

        Instant ultimaDoLead = ultimaMensagemDoLeadDoCartao(lead);

        assertThat(ultimaDoLead)
                .as("contato importado que nunca escreveu nao pode aparecer com janela")
                .isNull();
        assertThat(estadoDaJanela(ultimaDoLead, agora)).isEqualTo("inexistente");
    }

    @Test
    @DisplayName("lead que so RECEBEU mensagem (nunca escreveu) -> inexistente; trava contra usar lead.ultima_interacao_em")
    void leadQueSoRecebeuMensagem_continuaInexistente() throws Exception {
        UUID ana = idDoUsuario(EMAIL_ANA);
        UUID lead = criarLead("so recebeu", ana, "EM_ATENDIMENTO");
        Instant agora = Instant.now();

        UUID atendimento = criarAtendimento(lead, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofHours(1)));
        // Envio de saida ha minutos: avanca lead.ultima_interacao_em, mas nao e mensagem do cliente.
        inserirMensagem(atendimento, "ATENDENTE", ana, "primeira abordagem", agora.minus(Duration.ofMinutes(5)));

        Instant ultimaDoLead = ultimaMensagemDoLeadDoCartao(lead);

        assertThat(ultimaDoLead)
                .as("so mensagem de saida nao abre janela; se abrisse, 7.144 importados apareceriam abertos")
                .isNull();
        assertThat(estadoDaJanela(ultimaDoLead, agora)).isEqualTo("inexistente");
    }

    @Test
    @DisplayName("concordancia: o estado da tela nunca contradiz canal.aceitaTextoLivre")
    void concordanciaEntreCartaoEBackend() throws Exception {
        assertThat(canal.provedor())
                .as("a concordancia so vale contra um provedor que aplica a janela de 24h")
                .isEqualTo("meta-cloud");

        UUID ana = idDoUsuario(EMAIL_ANA);
        Instant agora = Instant.now();

        UUID leadAberta = criarLead("concordancia aberta", ana, "EM_ATENDIMENTO");
        UUID finAberta = criarAtendimento(leadAberta, ana, "FINALIZADO", agora.minus(Duration.ofHours(2)));
        inserirMensagem(finAberta, "LEAD", null, "acabou de escrever", agora.minus(Duration.ofHours(1)));
        criarAtendimento(leadAberta, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofMinutes(1)));

        UUID leadFechada = criarLead("concordancia fechada", ana, "EM_ATENDIMENTO");
        UUID finFechada = criarAtendimento(leadFechada, ana, "FINALIZADO", agora.minus(Duration.ofHours(31)));
        inserirMensagem(finFechada, "LEAD", null, "escreveu ha 30h", agora.minus(Duration.ofHours(30)));
        criarAtendimento(leadFechada, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofMinutes(1)));

        UUID leadInexistente = criarLead("concordancia inexistente", ana, "EM_ATENDIMENTO");
        criarAtendimento(leadInexistente, ana, "EM_ATENDIMENTO", agora.minus(Duration.ofMinutes(1)));

        for (UUID lead : new UUID[] {leadAberta, leadFechada, leadInexistente}) {
            Instant ultimaDoLead = ultimaMensagemDoLeadDoCartao(lead);
            boolean telaLiberaTextoLivre = "aberta".equals(estadoDaJanela(ultimaDoLead, agora));
            boolean backendAceita = canal.aceitaTextoLivre(Optional.ofNullable(ultimaDoLead), agora);

            assertThat(telaLiberaTextoLivre)
                    .as("lead %s: a tela libera texto livre sse, e somente se, o backend aceita", lead)
                    .isEqualTo(backendAceita);
        }
    }

    // --- estimativa client-side, espelhando frontend/src/lib/atendimento/janela-24h.ts ----------

    private static String estadoDaJanela(Instant ultimaMensagemDoLeadEm, Instant agora) {
        if (ultimaMensagemDoLeadEm == null) {
            return "inexistente";
        }
        return ultimaMensagemDoLeadEm.isAfter(agora.minus(JANELA)) ? "aberta" : "fechada";
    }

    // --- apoio ----------------------------------------------------------------------------------

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
        throw new AssertionError("cartao do lead nao encontrado: " + leadId + " | corpo=" + corpo
                + " | " + diagnosticoDoLead(leadId));
    }

    /** Estado bruto no banco, para transformar um nulo silencioso em evidencia acionavel. */
    private String diagnosticoDoLead(UUID leadId) {
        var atendimentos = jdbc.queryForList(
                "SELECT id, status, atendente_id, iniciado_em FROM atendimento WHERE lead_id = ? ORDER BY iniciado_em",
                leadId);
        var mensagens = jdbc.queryForList(
                "SELECT m.id, m.atendimento_id, m.remetente_tipo, m.enviado_em"
                        + " FROM mensagem m JOIN atendimento a ON a.id = m.atendimento_id"
                        + " WHERE a.lead_id = ? ORDER BY m.enviado_em",
                leadId);
        return "atendimentos=" + atendimentos + " mensagens=" + mensagens;
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, ?::status_basico_lead)",
                id,
                PREFIXO + nome + "-" + id,
                dono,
                status);
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

    private void inserirMensagem(
            UUID atendimentoId, String remetenteTipo, UUID remetenteId, String conteudo, Instant enviadoEm) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,"
                        + " conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, ?::remetente_tipo, ?, 'TEXTO'::tipo_mensagem, ?,"
                        + " 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                remetenteTipo,
                remetenteId,
                conteudo,
                Timestamp.from(enviadoEm));
    }
}
