package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

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

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * {@code POST /api/v1/atendimentos/mensagens}, {@code /transferir} e {@code /finalizar} ponta a
 * ponta — os tres endpoints que faltavam ter controller, agora expostos sobre os use cases da E04.
 * {@code AtendimentoIT} ja cobre a logica de negocio desses use cases (RN-CRM-06, RLS) diretamente;
 * este teste cobre a camada HTTP: mapeamento de excecao para status, formato de resposta.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AtendimentoAcoesControllerIT extends PostgresIT {

    private static final String PREFIXO = "E11-acoes-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID idAna;
    private UUID idBruno;

    @BeforeEach
    void prepararUsuarios() {
        // O container de integracao e compartilhado entre execucoes da suite; remova
        // sobras de uma JVM interrompida antes de medir a visibilidade do lote.
        limpar();
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
    }

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
    @DisplayName("enviar: cria mensagem PENDENTE e devolve o atendimento")
    void enviar_textoLivre_criaPendente() {
        UUID lead = criarLead("lead ana " + sufixo(), idAna, Instant.now());

        var resposta = enviarComo(EMAIL_ANA, SENHA_ATENDENTE, lead, "ola, tudo bem?");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"statusEntrega\":\"PENDENTE\"");
    }

    @Test
    @DisplayName("enviar: lead de colega nao e alcancado, responde 404")
    void enviar_leadDeColega_retorna404() {
        UUID leadDaAna = criarLead("lead ana " + sufixo(), idAna, Instant.now());

        var resposta = enviarComo(EMAIL_BRUNO, SENHA_ATENDENTE, leadDaAna, "ola");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("enviar: fora da janela de 24h responde 422 antes de gravar")
    void enviar_foraDaJanela_retorna422() {
        UUID lead = criarLead("lead fechado " + sufixo(), idAna, Instant.now().minus(Duration.ofHours(48)));

        var resposta = enviarComo(EMAIL_ANA, SENHA_ATENDENTE, lead, "ola depois de dias");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains("Fora da janela de 24 horas");
    }

    @Test
    @DisplayName("transferir: move para outro atendente")
    void transferir_sucesso_moveParaOutroAtendente() {
        UUID lead = criarLead("lead transferir " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", idBruno.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(idBruno.toString());
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(idBruno);
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = (SELECT lead_id FROM atendimento WHERE id = ?)",
                        UUID.class,
                        atendimentoId))
                .isEqualTo(idBruno);
    }

    @Test
    @DisplayName("transferir: destino gestor e recusado antes de alterar atendimento ou lead")
    void transferir_destinoGestor_retorna422SemEscritas() {
        assertDestinoInvalido(idDoUsuario(EMAIL_GESTOR), "papel nao elegivel", EMAIL_GESTOR, SENHA_GESTOR);
    }

    @Test
    @DisplayName("transferir: destino subgestor e recusado antes de alterar atendimento ou lead")
    void transferir_destinoSubgestor_retorna422SemEscritas() {
        assertDestinoInvalido(idDoUsuario(EMAIL_SUBGESTOR), "papel nao elegivel", EMAIL_GESTOR, SENHA_GESTOR);
    }

    @Test
    @DisplayName("transferir: destino administrador e recusado antes de alterar atendimento ou lead")
    void transferir_destinoAdministrador_retorna422SemEscritas() {
        assertDestinoInvalido(idDoUsuario(EMAIL_ADMINISTRADOR), "papel nao elegivel", EMAIL_GESTOR, SENHA_GESTOR);
    }

    @Test
    @DisplayName("transferir: destino inativo e recusado antes de alterar atendimento ou lead")
    void transferir_destinoInativo_retorna422SemEscritas() {
        UUID destino = idDoUsuario(EMAIL_BRUNO);
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", destino);
        try {
            assertDestinoInvalido(destino, "inativo", EMAIL_GESTOR, SENHA_GESTOR);
        } finally {
            jdbc.update("UPDATE usuario SET ativo = TRUE WHERE id = ?", destino);
        }
    }

    @Test
    @DisplayName("transferir: destino inexistente e recusado antes de alterar atendimento ou lead")
    void transferir_destinoInexistente_retorna422SemEscritas() {
        assertDestinoInvalido(UUID.randomUUID(), "inexistente", EMAIL_GESTOR, SENHA_GESTOR);
    }

    @Test
    @DisplayName("transferir: destino nulo devolve atendimento e lead para a IA")
    void transferir_destinoNulo_devolveParaIa() {
        UUID lead = criarLead("lead devolver ia " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT status_basico FROM lead WHERE id = ?", String.class, lead))
                .isEqualTo("IA");
    }

    @Test
    @DisplayName("transferir: atendimento de colega nao e alcancado, responde 404")
    void transferir_atendimentoDeColega_retorna404() {
        UUID lead = criarLead("lead intocavel " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_BRUNO,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", idBruno.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("transferir: atendente nao entrega um Potencial a um colega escolhido a dedo")
    void transferir_atendentePotencialParaColega_retorna403() {
        UUID atendimentoId = criarAtendimentoPotencial("lead potencial " + sufixo());

        var resposta = chamar(
                EMAIL_BRUNO,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", idAna.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("transferir: atendente pode assumir um Potencial para si mesmo")
    void transferir_atendentePotencialParaSiMesmo_retorna200() {
        UUID atendimentoId = criarAtendimentoPotencial("lead potencial " + sufixo());

        var resposta = chamar(
                EMAIL_BRUNO,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", idBruno.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(idBruno.toString());
    }

    @Test
    @DisplayName("finalizar: encerra o atendimento")
    void finalizar_sucesso_encerraAtendimento() {
        UUID lead = criarLead("lead finalizar " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/finalizar",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("FINALIZADO");
    }

    @Test
    @DisplayName("finalizar duas vezes: a segunda responde 409")
    void finalizar_duasVezes_retorna409() {
        UUID lead = criarLead("lead finalizar 2x " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/finalizar",
                null);
        var segunda = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/finalizar",
                null);

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("finalizar em lote respeita a visibilidade do atendente")
    void finalizarEmLote_finalizaSomenteAtendimentosVisiveis() {
        UUID leadDaAna = criarLead("lead lote ana " + sufixo(), idAna, Instant.now());
        UUID atendimentoDaAna = criarAtendimentoViaEnvioComo(EMAIL_ANA, SENHA_ATENDENTE, leadDaAna);
        UUID leadDoBruno = criarLead("lead lote bruno " + sufixo(), idBruno, Instant.now());
        UUID atendimentoDoBruno = criarAtendimentoViaEnvioComo(EMAIL_BRUNO, SENHA_ATENDENTE, leadDoBruno);

        var previa = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.GET,
                "/api/v1/atendimentos/finalizar-lote",
                null);
        assertThat(previa.getStatusCode()).isEqualTo(HttpStatus.OK);
        int quantidadeVisivel = extrairInt(previa.getBody(), "quantidade");
        assertThat(quantidadeVisivel).isGreaterThanOrEqualTo(1);

        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/finalizar-lote",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody())
                .contains(
                        "\"solicitados\":" + quantidadeVisivel,
                        "\"finalizados\":" + quantidadeVisivel,
                        "\"recusados\":0");
        assertThat(jdbc.queryForObject("SELECT status FROM atendimento WHERE id = ?", String.class, atendimentoDaAna))
                .isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT status FROM atendimento WHERE id = ?", String.class, atendimentoDoBruno))
                .isEqualTo("EM_ATENDIMENTO");
    }

    // --- apoio ------------------------------------------------------------

    private UUID criarAtendimentoViaEnvio(UUID leadId) {
        return criarAtendimentoViaEnvioComo(EMAIL_ANA, SENHA_ATENDENTE, leadId);
    }

    private UUID criarAtendimentoViaEnvioComo(String email, String senha, UUID leadId) {
        var resposta = enviarComo(email, senha, leadId, "mensagem inicial");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extrairUuid(resposta.getBody(), "atendimentoId");
    }

    private void assertDestinoInvalido(UUID destino, String motivo, String email, String senha) {
        UUID lead = criarLead("lead destino invalido " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                email,
                senha,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", destino.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).contains(destino.toString()).contains(motivo);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(idAna);
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead))
                .isEqualTo(idAna);
    }

    private ResponseEntity<String> enviarComo(String email, String senha, UUID leadId, String conteudo) {
        return chamar(
                email,
                senha,
                HttpMethod.POST,
                "/api/v1/atendimentos/mensagens",
                Map.of("leadId", leadId.toString(), "conteudo", conteudo));
    }

    private ResponseEntity<String> chamar(
            String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static UUID extrairUuid(String json, String campo) {
        return UUID.fromString(json.replaceAll(".*\"" + campo + "\":\"([^\"]+)\".*", "$1"));
    }

    private static int extrairInt(String json, String campo) {
        return Integer.parseInt(json.replaceAll(".*\"" + campo + "\":([0-9]+).*", "$1"));
    }

    private static String sufixo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    /** Um atendimento sem dono, grupo "Potenciais" — alcancavel por qualquer atendente via RLS. */
    private UUID criarAtendimentoPotencial(String nomeLead) {
        UUID leadId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em)"
                        + " VALUES (?, ?, NULL, 'IA'::status_basico_lead, ?)",
                leadId,
                PREFIXO + nomeLead,
                Timestamp.from(Instant.now()));
        UUID atendimentoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, NULL, 'EM_IA'::status_atendimento, now())",
                atendimentoId,
                leadId);
        return atendimentoId;
    }

    private UUID criarLead(String nome, UUID dono, Instant ultimaInteracao) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?)",
                id,
                PREFIXO + nome,
                dono,
                Timestamp.from(ultimaInteracao));
        return id;
    }
}
