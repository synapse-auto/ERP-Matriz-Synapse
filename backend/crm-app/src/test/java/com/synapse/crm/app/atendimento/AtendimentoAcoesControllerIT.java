package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private UUID idSubgestor;

    @BeforeEach
    void prepararUsuarios() {
        // O container de integracao e compartilhado entre execucoes da suite; remova
        // sobras de uma JVM interrompida antes de medir a visibilidade do lote.
        limpar();
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
        idSubgestor = idDoUsuario(EMAIL_SUBGESTOR);
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
    @DisplayName("template manual: atendente assume o Potencial, desliga a IA e enfileira a entrega")
    void enviarTemplate_atendenteEmAtendimentoIa_assumeEEnfileira() {
        UUID atendimentoId = criarAtendimentoPotencial("template atendente " + sufixo());
        UUID leadId = leadDoAtendimento(atendimentoId);

        ResponseEntity<String> resposta = enviarTemplateComo(EMAIL_ANA, SENHA_ATENDENTE, leadId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"transferiuOLead\":true");
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, atendimentoId))
                .isEqualTo("EM_ATENDIMENTO");
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(idAna);
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId))
                .isEqualTo(idAna);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_evento WHERE payload->>'atendimentoId' = ?",
                        Long.class,
                        atendimentoId.toString()))
                .isOne();
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(contarTimeline(leadId, "LEAD_TRANSFERIDO_POR_ENVIO")).isOne();
            assertThat(contarAuditoria(leadId, "ENVIO_COM_TRANSFERENCIA_DE_LEAD")).isOne();
        });

    }

    @Test
    @DisplayName("template manual: gestor e subgestor assumem o Potencial conforme a RN-CRM-06")
    void enviarTemplate_gestorESubgestor_assumemOAtendimento() {
        assertTemplateAssume(EMAIL_GESTOR, SENHA_GESTOR, idDoUsuario(EMAIL_GESTOR));
        assertTemplateAssume(EMAIL_SUBGESTOR, SENHA_SUBGESTOR, idSubgestor);
    }

    @Test
    @DisplayName("template manual: validação recusada não altera modo, responsável, mensagem ou outbox")
    void enviarTemplate_invalido_naoAlteraAtendimentoIa() {
        UUID atendimentoId = criarAtendimentoPotencial("template inválido " + sufixo());
        UUID leadId = leadDoAtendimento(atendimentoId);

        ResponseEntity<String> resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/mensagens/template",
                Map.of("leadId", leadId.toString(), "nome", "", "idioma", "pt_BR", "parametros", List.of()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertAtendimentoPermaneceComIa(atendimentoId, leadId);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_evento WHERE payload->>'atendimentoId' = ?",
                        Long.class,
                        atendimentoId.toString()))
                .isZero();
    }

    @Test
    @DisplayName("template manual: atendente não alcança lead de colega pela RN-CRM-01")
    void enviarTemplate_leadDeColega_retorna404SemEfeito() {
        UUID leadId = criarLead("template colega " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(leadId);
        long mensagensAntes = jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimentoId);

        ResponseEntity<String> resposta = enviarTemplateComo(EMAIL_BRUNO, SENHA_ATENDENTE, leadId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(idAna);
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId))
                .isEqualTo(idAna);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimentoId))
                .isEqualTo(mensagensAntes);
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
    @DisplayName("transferir: destino subgestor e aceito e persiste no atendimento e no lead")
    void transferir_destinoSubgestor_retorna200() {
        UUID destino = idDoUsuario(EMAIL_SUBGESTOR);
        UUID lead = criarLead("lead transferir subgestor " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_GESTOR,
                SENHA_GESTOR,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", destino.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(destino.toString());
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(destino);
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead))
                .isEqualTo(destino);
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
    @DisplayName("transferir: atendente passa a conversa propria para um colega ativo")
    void transferir_atendenteParaColegaAtivo_retorna200() {
        UUID lead = criarLead("lead colega " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                Map.of("paraAtendenteId", idBruno.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(idBruno.toString());
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(idBruno);
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead))
                .isEqualTo(idBruno);
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                        "SELECT ator_id FROM evento_timeline WHERE lead_id = ? AND tipo = 'ATENDIMENTO_TRANSFERIDO'",
                        UUID.class,
                        lead))
                .isEqualTo(idAna));
    }

    @Test
    @DisplayName("transferir: atendente para destino que nao e atendente ativo segue o caminho da E53")
    void transferir_atendenteDestinoInvalido_retorna422() {
        assertDestinoInvalido(idDoUsuario(EMAIL_GESTOR), "papel nao elegivel", EMAIL_ANA, SENHA_ATENDENTE);
    }

    @Test
    @DisplayName("transferir: atendente continua devolvendo para a IA")
    void transferir_atendenteDestinoNulo_devolveParaIa() {
        UUID lead = criarLead("lead ana devolve ia " + sufixo(), idAna, Instant.now());
        UUID atendimentoId = criarAtendimentoViaEnvio(lead);

        var resposta = chamar(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/transferir",
                null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT status_basico FROM lead WHERE id = ?", String.class, lead))
                .isEqualTo("IA");
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

    private ResponseEntity<String> enviarTemplateComo(String email, String senha, UUID leadId) {
        return chamar(
                email,
                senha,
                HttpMethod.POST,
                "/api/v1/atendimentos/mensagens/template",
                Map.of(
                        "leadId", leadId.toString(),
                        "nome", "reativacao",
                        "idioma", "pt_BR",
                        "parametros", List.of("Cliente")));
    }

    private void assertTemplateAssume(String email, String senha, UUID responsavelEsperado) {
        UUID atendimentoId = criarAtendimentoPotencial("template gestão " + sufixo());
        UUID leadId = leadDoAtendimento(atendimentoId);

        ResponseEntity<String> resposta = enviarTemplateComo(email, senha, leadId);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, atendimentoId))
                .isEqualTo("EM_ATENDIMENTO");
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isEqualTo(responsavelEsperado);
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId))
                .isEqualTo(responsavelEsperado);
    }

    private void assertAtendimentoPermaneceComIa(UUID atendimentoId, UUID leadId) {
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, atendimentoId))
                .isEqualTo("EM_IA");
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId))
                .isNull();
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId))
                .isNull();
    }

    private long contarTimeline(UUID leadId, String tipo) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM evento_timeline WHERE lead_id = ? AND tipo = ?", Long.class, leadId, tipo);
    }

    private long contarAuditoria(UUID leadId, String acao) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE lead_id = ? AND acao = ?", Long.class, leadId, acao);
    }

    private UUID leadDoAtendimento(UUID atendimentoId) {
        return jdbc.queryForObject("SELECT lead_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId);
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
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em,"
                        + " ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, NULL, 'IA'::status_basico_lead, ?, ?)",
                leadId,
                PREFIXO + nomeLead,
                Timestamp.from(Instant.now()),
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
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em,"
                        + " ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?, ?)",
                id,
                PREFIXO + nome,
                dono,
                Timestamp.from(ultimaInteracao),
                Timestamp.from(ultimaInteracao));
        return id;
    }
}
