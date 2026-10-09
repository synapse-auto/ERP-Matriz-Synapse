package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.evento.InformacoesDoChatbotParaTempoReal;

/**
 * Base dos ITs do card de informacoes do chatbot.
 *
 * <p>As tres classes concretas (contrato, concorrencia e visibilidade) herdam as mesmas anotacoes e
 * por isso compartilham UM contexto Spring: cada contexto a mais num Postgres compartilhado e mais um
 * pool e mais um conjunto de agendadores disputando conexao e linha de outbox com a suite inteira.
 *
 * <p>A limpeza confere o que limpou: fixture que sobra e vazamento de estado para o proximo teste da
 * suite (o {@code SaudeCriticaIT}, por exemplo, depende de um banco sem residuos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "synapse.seguranca.token-interno=token-informacoes-chatbot",
    "synapse.automacao.informacoes-chatbot-tamanho-maximo=200",
    "synapse.automacao.informacoes-chatbot-tamanho-pagina=3"
})
@Import(InformacoesDoChatbotITBase.Captura.class)
abstract class InformacoesDoChatbotITBase extends PostgresIT {

    static final String TOKEN = "token-informacoes-chatbot";
    static final String PREFIXO = "INFO-CHATBOT-";
    static final String FLAG = "informacoes_chatbot_historico";
    static final String RESUMO_DA_FICHA = "RESUMO-DA-FICHA-NAO-PODE-MUDAR";

    @TestConfiguration
    static class Captura {
        @Bean
        CapturaDeAvisos capturaDeAvisos() {
            return new CapturaDeAvisos();
        }
    }

    static class CapturaDeAvisos {
        final List<InformacoesDoChatbotParaTempoReal> avisos = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void aoAvisar(InformacoesDoChatbotParaTempoReal aviso) {
            avisos.add(aviso);
        }
    }

    record Atendimento(UUID id, UUID leadId) {}

    @Autowired
    protected TestRestTemplate http;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected CapturaDeAvisos captura;

    protected UUID ana;

    @BeforeEach
    void preparar() {
        ana = idDoUsuario(EMAIL_ANA);
        definirFlag(true);
        captura.avisos.clear();
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        definirFlag(false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE nome LIKE ?", Long.class, PREFIXO + "%"))
                .as("a limpeza deixou lead de fixture para tras").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flag WHERE chave = ?", Long.class, FLAG))
                .as("a linha da flag precisa existir ao fim do teste (a migration a cria)").isEqualTo(1L);
        assertThat(flagLigada()).as("flag deixada ligada vaza para os demais ITs da suite").isFalse();
    }

    // --- fixtures -------------------------------------------------------------------------------

    protected UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    protected Atendimento atendimentoComHumano(String marcador) {
        UUID leadId = criarLead(marcador, ana, "EM_ATENDIMENTO");
        jdbc.update("UPDATE lead SET resumo_ia = ? WHERE id = ?", RESUMO_DA_FICHA, leadId);
        return new Atendimento(criarAtendimento(leadId, ana, "EM_ATENDIMENTO"), leadId);
    }

    protected UUID criarLead(String marcador, UUID dono, String statusBasico) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES (?,?,?,?::status_basico_lead)",
                id, PREFIXO + marcador + "-" + id.toString().substring(0, 8), dono, statusBasico);
        return id;
    }

    protected UUID criarAtendimento(UUID leadId, UUID atendente, String status) {
        UUID id = UUID.randomUUID();
        if ("FINALIZADO".equals(status)) {
            jdbc.update(
                    "INSERT INTO atendimento(id,lead_id,atendente_id,status,finalizado_em) VALUES (?,?,?,?::status_atendimento, now())",
                    id, leadId, atendente, status);
        } else {
            jdbc.update(
                    "INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,?,?::status_atendimento)",
                    id, leadId, atendente, status);
        }
        return id;
    }

    /** Insere o card direto no banco (o dono da conexao de teste ignora a RLS): so para estados que o contrato recusa. */
    protected void inserirCardPorSql(UUID atendimentoId, String chave, String conteudo) {
        jdbc.update(
                "INSERT INTO atendimento_informacao_chatbot (id, atendimento_id, chave_idempotencia, conteudo) VALUES (?,?,?,?)",
                UUID.randomUUID(), atendimentoId, chave, conteudo);
    }

    protected void definirFlag(boolean habilitada) {
        jdbc.update(
                "INSERT INTO feature_flag (chave, habilitado, descricao) VALUES (?, ?, 'teste') "
                        + "ON CONFLICT (chave) DO UPDATE SET habilitado = EXCLUDED.habilitado",
                FLAG, habilitada);
    }

    protected boolean flagLigada() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT habilitado FROM feature_flag WHERE chave = ?", Boolean.class, FLAG));
    }

    // --- consultas ------------------------------------------------------------------------------

    protected long cards(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento_informacao_chatbot WHERE atendimento_id = ?", Long.class, atendimentoId);
    }

    protected String conteudoDoCard(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT conteudo FROM atendimento_informacao_chatbot WHERE atendimento_id = ? ORDER BY registrado_em LIMIT 1",
                String.class, atendimentoId);
    }

    protected UUID donoDoAtendimento(UUID atendimentoId) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId);
    }

    protected long participantes(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento_participante WHERE atendimento_id = ?", Long.class, atendimentoId);
    }

    protected String resumoDaFicha(UUID leadId) {
        return jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, leadId);
    }

    // --- HTTP -----------------------------------------------------------------------------------

    protected String loginAna() {
        return bearerDe(EMAIL_ANA, SENHA_ATENDENTE);
    }

    protected String bearerDe(String email, String senha) {
        return ApoioAutenticacao.login(http, email, senha).accessToken();
    }

    /** Leitura de uma pagina de cards; {@code consulta} e a query string ja montada ("" ou "?cursor=..."). */
    protected ResponseEntity<String> lerCards(UUID atendimentoId, String bearer, String consulta) {
        return ApoioAutenticacao.comToken(http, bearer, HttpMethod.GET, urlLeitura(atendimentoId) + consulta, String.class);
    }

    protected ResponseEntity<String> lerCards(UUID atendimentoId, String bearer) {
        return lerCards(atendimentoId, bearer, "");
    }

    protected ResponseEntity<String> lerMensagens(UUID atendimentoId, String bearer) {
        return ApoioAutenticacao.comToken(
                http, bearer, HttpMethod.GET, "/api/v1/atendimentos/" + atendimentoId + "/mensagens", String.class);
    }

    protected ResponseEntity<String> postar(String token, UUID atendimentoId, String chave, String conteudo) {
        HttpHeaders cabecalhos = cabecalhos(token);
        cabecalhos.set("Idempotency-Key", chave);
        try {
            String corpo = json.writeValueAsString(Map.of("conteudo", conteudo));
            return http.exchange(url(atendimentoId), HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException(erro);
        }
    }

    protected static HttpHeaders cabecalhos(String token) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            cabecalhos.set("X-Synapse-Token", token);
        }
        return cabecalhos;
    }

    protected JsonNode ler(ResponseEntity<String> resposta) {
        try {
            return json.readTree(resposta.getBody());
        } catch (JsonProcessingException erro) {
            throw new IllegalStateException("corpo ilegivel: " + resposta.getBody(), erro);
        }
    }

    protected static String url(UUID atendimentoId) {
        return "/internal/v1/atendimentos/" + atendimentoId + "/informacoes-do-chatbot";
    }

    protected static String urlLeitura(UUID atendimentoId) {
        return "/api/v1/atendimentos/" + atendimentoId + "/informacoes-do-chatbot";
    }
}
