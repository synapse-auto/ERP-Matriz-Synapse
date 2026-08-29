package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000"
        })
class RespostaEEncaminhamentoIT extends PostgresIT {

    private static final String PREFIXO = "E87-ref-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private CanalFake canal;
    @Autowired private PublicadorDaOutbox publicador;

    private UUID idAna;
    private UUID idBruno;

    @BeforeEach
    void preparar() {
        limpar();
        canal.limpar();
        canal.abrirJanela();
        canal.religar();
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
    }

    @AfterEach
    void limpar() {
        jdbc.update(
                """
                DELETE FROM outbox_evento WHERE payload->>'atendimentoId' IN (
                    SELECT a.id::text FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
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
    @DisplayName("resposta valida persiste citacao e entrega context.message_id ao adaptador")
    void respostaValidaPersisteEEnviaContexto() throws Exception {
        Cenario origem = criarConversaComWamid(idAna, "lead origem", "wamid.E87-origem");

        var resposta = enviarComo(
                EMAIL_ANA,
                Map.of(
                        "leadId", origem.leadId().toString(),
                        "conteudo", "recebido",
                        "mensagemOrigemId", origem.mensagemId().toString(),
                        "origemEnviadaEm", origem.enviadoEm().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = json.readTree(resposta.getBody());
        UUID novaId = UUID.fromString(corpo.path("mensagemId").asText());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem_referencia WHERE mensagem_id = ?",
                        Integer.class,
                        novaId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT tipo FROM mensagem_referencia WHERE mensagem_id = ?",
                        String.class,
                        novaId))
                .isEqualTo("RESPOSTA");

        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(canal.enviados()).isNotEmpty();
            assertThat(canal.enviados().getLast().contextoWamid()).isEqualTo("wamid.E87-origem");
        });

        var historico = chamar(
                EMAIL_ANA,
                HttpMethod.GET,
                "/api/v1/atendimentos/" + origem.atendimentoId() + "/mensagens",
                null);
        assertThat(historico.getBody()).contains("\"tipoReferencia\":\"RESPOSTA\"");
        assertThat(historico.getBody()).contains("recebido");
    }

    @Test
    @DisplayName("origem sem wamid responde 422 e nao grava mensagem nova")
    void origemSemWamidNaoGrava() {
        Cenario origem = criarConversaSemWamid(idAna, "lead sem wamid");
        int antes = countMensagens(origem.atendimentoId());

        var resposta = enviarComo(
                EMAIL_ANA,
                Map.of(
                        "leadId", origem.leadId().toString(),
                        "conteudo", "nao deve sair",
                        "mensagemOrigemId", origem.mensagemId().toString(),
                        "origemEnviadaEm", origem.enviadoEm().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("Resposta indevida");
        assertThat(countMensagens(origem.atendimentoId())).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_referencia", Integer.class))
                .isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem_referencia r JOIN mensagem m ON m.id = r.mensagem_id WHERE m.atendimento_id = ?",
                        Integer.class,
                        origem.atendimentoId()))
                .isZero();
    }

    @Test
    @DisplayName("atendente nao responde nem encaminha mensagem do colega")
    void atendenteNaoAlcancaColega() {
        Cenario daAna = criarConversaComWamid(idAna, "lead ana", "wamid.E87-ana");
        Cenario doBruno = criarConversaComWamid(idBruno, "lead bruno dest", "wamid.E87-bruno");

        var resposta = enviarComo(
                EMAIL_BRUNO,
                Map.of(
                        "leadId", daAna.leadId().toString(),
                        "conteudo", "invasao",
                        "mensagemOrigemId", daAna.mensagemId().toString(),
                        "origemEnviadaEm", daAna.enviadoEm().toString()));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var encaminhar = chamar(
                EMAIL_BRUNO,
                HttpMethod.POST,
                "/api/v1/atendimentos/"
                        + daAna.atendimentoId()
                        + "/mensagens/"
                        + daAna.mensagemId()
                        + "/encaminhamentos?enviadoEm="
                        + daAna.enviadoEm(),
                Map.of("destinoAtendimentoId", doBruno.atendimentoId().toString()));
        assertThat(encaminhar.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(countMensagens(doBruno.atendimentoId())).isEqualTo(1);
    }

    @Test
    @DisplayName("encaminha texto para destino visivel sem alterar a origem")
    void encaminhaTexto() throws Exception {
        Cenario origem = criarConversaComWamid(idAna, "lead de", "wamid.E87-fwd-o");
        Cenario destino = criarConversaComWamid(idAna, "lead para", "wamid.E87-fwd-d");
        String original = jdbc.queryForObject(
                "SELECT conteudo FROM mensagem WHERE id = ?", String.class, origem.mensagemId());

        var resposta = chamar(
                EMAIL_ANA,
                HttpMethod.POST,
                "/api/v1/atendimentos/"
                        + origem.atendimentoId()
                        + "/mensagens/"
                        + origem.mensagemId()
                        + "/encaminhamentos?enviadoEm="
                        + origem.enviadoEm(),
                Map.of("destinoAtendimentoId", destino.atendimentoId().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = json.readTree(resposta.getBody());
        UUID novaId = UUID.fromString(corpo.path("mensagemId").asText());
        assertThat(corpo.path("atendimentoId").asText()).isEqualTo(destino.atendimentoId().toString());
        assertThat(jdbc.queryForObject(
                        "SELECT conteudo FROM mensagem WHERE id = ?", String.class, origem.mensagemId()))
                .isEqualTo(original);
        assertThat(jdbc.queryForObject(
                        "SELECT tipo FROM mensagem_referencia WHERE mensagem_id = ?",
                        String.class,
                        novaId))
                .isEqualTo("ENCAMINHAMENTO");
        assertThat(countMensagens(destino.atendimentoId())).isEqualTo(2);
    }

    @Test
    @DisplayName("encaminhar botoes responde 422 sem gravar no destino")
    void encaminhaBotoesRecusa() {
        Cenario origem = criarConversaComWamid(idAna, "lead botoes", "wamid.E87-btn");
        jdbc.update("UPDATE mensagem SET tipo = 'BOTOES'::tipo_mensagem, opcoes = '[{\"id\":\"1\",\"titulo\":\"A\"}]'::jsonb WHERE id = ?",
                origem.mensagemId());
        Cenario destino = criarConversaComWamid(idAna, "lead dest botoes", "wamid.E87-btn-d");
        int antes = countMensagens(destino.atendimentoId());

        var resposta = chamar(
                EMAIL_ANA,
                HttpMethod.POST,
                "/api/v1/atendimentos/"
                        + origem.atendimentoId()
                        + "/mensagens/"
                        + origem.mensagemId()
                        + "/encaminhamentos?enviadoEm="
                        + origem.enviadoEm(),
                Map.of("destinoAtendimentoId", destino.atendimentoId().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(countMensagens(destino.atendimentoId())).isEqualTo(antes);
    }

    @Test
    @DisplayName("provedor fora nao impede o HTTP de resposta: mensagem fica PENDENTE")
    void provedorForaNaoDerrubaEnvio() {
        canal.derrubar("timeout");
        Cenario origem = criarConversaComWamid(idAna, "lead timeout", "wamid.E87-to");

        var resposta = enviarComo(
                EMAIL_ANA,
                Map.of(
                        "leadId", origem.leadId().toString(),
                        "conteudo", "ainda assim",
                        "mensagemOrigemId", origem.mensagemId().toString(),
                        "origemEnviadaEm", origem.enviadoEm().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("PENDENTE");
    }

    private Cenario criarConversaComWamid(UUID dono, String nome, String wamid) {
        Cenario cenario = criarConversaSemWamid(dono, nome);
        jdbc.update(
                """
                INSERT INTO mensagem_id_externo (wamid, mensagem_id, mensagem_enviada_em, atendimento_id)
                SELECT ?, id, enviado_em, atendimento_id FROM mensagem WHERE id = ?
                """,
                wamid,
                cenario.mensagemId());
        return cenario;
    }

    private Cenario criarConversaSemWamid(UUID dono, String nome) {
        UUID leadId = UUID.randomUUID();
        UUID atendimentoId = UUID.randomUUID();
        UUID mensagemId = UUID.randomUUID();
        Instant enviadoEm = Instant.parse("2026-08-29T15:00:00Z");
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico, ultima_interacao_em)"
                        + " VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?)",
                leadId,
                PREFIXO + nome + " " + leadId,
                "5599" + String.format("%09d", Math.floorMod(leadId.getLeastSignificantBits(), 1_000_000_000L)),
                dono,
                Timestamp.from(Instant.now()));
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_atendimento, now())",
                atendimentoId,
                leadId,
                dono);
        jdbc.update(
                """
                INSERT INTO mensagem
                    (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', 'mensagem do cliente', 'ENTREGUE', ?)
                """,
                mensagemId,
                atendimentoId,
                Timestamp.from(enviadoEm));
        return new Cenario(leadId, atendimentoId, mensagemId, enviadoEm);
    }

    private int countMensagens(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimentoId);
    }

    private ResponseEntity<String> enviarComo(String email, Map<String, String> corpo) {
        return chamar(email, HttpMethod.POST, "/api/v1/atendimentos/mensagens", corpo);
    }

    private ResponseEntity<String> chamar(String email, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private record Cenario(UUID leadId, UUID atendimentoId, UUID mensagemId, Instant enviadoEm) {}
}
