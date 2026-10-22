package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.campanha.EnfileirarTemplateDeCampanhaUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * E233: a timeline de mensagens diz, por campo e não por texto, quais mensagens foram envios de
 * template. A E232 (composer reativo) depende disso: o corpo renderizado do template é gravado sem
 * prefixo técnico, então nenhuma heurística sobre o conteúdo o reconheceria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000"
        })
class MarcadorDeTemplateNaTimelineIT extends PostgresIT {

    private static final String PREFIXO = "E233-marcador-";
    private static final String CORPO = "Olá Maria, seja bem-vinda!";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CanalFake canal;

    @Autowired
    private EnfileirarTemplateDeCampanhaUseCase enfileirarCampanha;

    private UUID leadId;
    private UUID atendimentoId;

    @BeforeEach
    void preparar() {
        limpar();
        canal.limpar();
        leadId = UUID.randomUUID();
        atendimentoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico, ultima_interacao_em) VALUES (?, ?, ?, 'IA', now())",
                leadId,
                PREFIXO + UUID.randomUUID(),
                "5511" + (900000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 99999999L)));
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) VALUES (?, ?, 'EM_IA', now())",
                atendimentoId,
                leadId);
    }

    @AfterEach
    void limpar() {
        canal.limpar();
        String leads = "(SELECT id FROM lead WHERE nome LIKE ?)";
        String atendimentos = "(SELECT id FROM atendimento WHERE lead_id IN " + leads + ")";
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN " + leads, PREFIXO + "%");
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN " + leads, PREFIXO + "%");
        jdbc.update(
                "DELETE FROM outbox_evento WHERE payload->>'atendimentoId' IN "
                        + "(SELECT id::text FROM atendimento WHERE lead_id IN " + leads + ")",
                PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE lead_id IN " + leads, PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_origem_automacao WHERE lead_id IN " + leads, PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN " + atendimentos, PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN " + leads, PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("template do composer COM corpo renderizado: a timeline devolve template=true")
    void composerComCorpoRenderizado_marcaTemplate() throws Exception {
        var resposta = enviarTemplate(Map.of(
                "leadId", leadId.toString(),
                "nome", "reativacao",
                "idioma", "pt_BR",
                "parametros", List.of("Maria"),
                "corpoRenderizado", CORPO));

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        JsonNode mensagem = mensagemComConteudo(atendimentoId, CORPO);
        assertThat(mensagem.path("template").isBoolean()).as("campo template presente e booleano").isTrue();
        assertThat(mensagem.path("template").asBoolean()).isTrue();
        assertThat(mensagem.path("tipo").asText()).as("continua TEXTO: o campo é aditivo").isEqualTo("TEXTO");
    }

    @Test
    @DisplayName("template do composer SEM corpo renderizado também marca template=true")
    void composerSemCorpoRenderizado_marcaTemplate() throws Exception {
        var resposta = enviarTemplate(Map.of(
                "leadId", leadId.toString(),
                "nome", "reativacao",
                "idioma", "pt_BR",
                "parametros", List.of("Cliente")));

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mensagemComConteudo(atendimentoId, "[template reativacao] Cliente").path("template").asBoolean())
                .isTrue();
    }

    @Test
    @DisplayName("texto livre da equipe NÃO é template, nem quando imita o marcador técnico")
    void textoLivre_naoMarca() throws Exception {
        var resposta = comoAna(
                "/api/v1/atendimentos/mensagens",
                Map.of("leadId", leadId.toString(), "conteudo", "[template reativacao] Cliente"));

        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(mensagemComConteudo(atendimentoId, "[template reativacao] Cliente").path("template").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("mensagem recebida do cliente NÃO é template, mesmo com o texto do marcador")
    void mensagemDoCliente_naoMarca() throws Exception {
        inserirDoCliente("[template reativacao] Cliente");

        JsonNode mensagem = mensagemComConteudo(atendimentoId, "[template reativacao] Cliente");

        assertThat(mensagem.path("template").isBoolean()).isTrue();
        assertThat(mensagem.path("template").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("contato novo com template marca template=true; com texto livre, false")
    void contatoNovo_marcaSoOTemplate() throws Exception {
        var comTemplate = comoAna(
                "/api/v1/atendimentos/novo-contato",
                Map.of(
                        "nome", PREFIXO + "novo-template",
                        "telefone", telefoneNacional(),
                        "template", Map.of(
                                "nome", "hello_world",
                                "idioma", "pt_BR",
                                "parametros", List.of("Maria"),
                                "corpoRenderizado", CORPO)));
        var comTexto = comoAna(
                "/api/v1/atendimentos/novo-contato",
                Map.of(
                        "nome", PREFIXO + "novo-texto",
                        "telefone", telefoneNacional(),
                        "primeiraMensagem", "oi, tudo bem?"));

        assertThat(comTemplate.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(comTexto.getStatusCode().is2xxSuccessful()).isTrue();
        UUID atendimentoDoTemplate = UUID.fromString(json.readTree(comTemplate.getBody()).path("atendimentoId").asText());
        UUID atendimentoDoTexto = UUID.fromString(json.readTree(comTexto.getBody()).path("atendimentoId").asText());
        assertThat(mensagemComConteudo(atendimentoDoTemplate, CORPO).path("template").asBoolean()).isTrue();
        assertThat(mensagemComConteudo(atendimentoDoTexto, "oi, tudo bem?").path("template").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("template de campanha marca template=true")
    void campanha_marcaTemplate() throws Exception {
        AtomicReference<EnfileirarTemplateDeCampanhaUseCase.Resultado> resultado = new AtomicReference<>();
        // teste=true: sem política proativa nem recusa por atendimento aberto (o do @BeforeEach segue aberto).
        ContextoDeServico.executarComo("e233", () -> resultado.set(
                enfileirarCampanha.executar(new EnfileirarTemplateDeCampanhaUseCase.Pedido(
                        UUID.randomUUID(), leadId, "reativacao", "pt_BR", List.of("Maria"), CORPO, true))));

        assertThat(resultado.get()).isInstanceOf(EnfileirarTemplateDeCampanhaUseCase.Resultado.Enfileirado.class);
        var enfileirado = (EnfileirarTemplateDeCampanhaUseCase.Resultado.Enfileirado) resultado.get();
        assertThat(mensagemComConteudo(enfileirado.atendimentoId(), CORPO).path("template").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("backfill possível pela outbox: a consulta de verificação (só leitura) acha o template sem marcador")
    void consultaDeBackfillPelaOutbox_achaTemplateSemMarcador() {
        enviarTemplate(Map.of(
                "leadId", leadId.toString(),
                "nome", "reativacao",
                "idioma", "pt_BR",
                "parametros", List.of("Maria"),
                "corpoRenderizado", CORPO));
        // Simula uma linha anterior à V110: corpo renderizado, sem marcador. O que resta para
        // reconhecê-la é o payload da outbox, que guarda conteudo.tipo = TEMPLATE.
        jdbc.update("UPDATE mensagem SET envio_de_template = FALSE WHERE atendimento_id = ?", atendimentoId);

        Long candidatas = jdbc.queryForObject(
                """
                SELECT count(*)
                  FROM mensagem m
                  JOIN outbox_evento o
                    ON o.tipo = 'canal.mensagem.enviar'
                   AND o.payload ->> 'mensagemId' = m.id::text
                 WHERE o.payload -> 'conteudo' ->> 'tipo' = 'TEMPLATE'
                   AND NOT m.envio_de_template
                   AND m.atendimento_id = ?
                """,
                Long.class,
                atendimentoId);

        assertThat(candidatas).isOne();
    }

    private ResponseEntity<String> enviarTemplate(Map<String, ?> corpo) {
        return comoAna("/api/v1/atendimentos/mensagens/template", corpo);
    }

    private ResponseEntity<String> comoAna(String caminho, Map<String, ?> corpo) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(caminho, HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private JsonNode mensagemComConteudo(UUID atendimento, String conteudo) throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        var resposta = http.exchange(
                "/api/v1/atendimentos/" + atendimento + "/mensagens",
                HttpMethod.GET,
                new HttpEntity<>(cabecalhos),
                String.class);
        assertThat(resposta.getStatusCode().is2xxSuccessful()).as("timeline: %s", resposta.getBody()).isTrue();
        for (JsonNode mensagem : json.readTree(resposta.getBody()).path("mensagens")) {
            if (conteudo.equals(mensagem.path("conteudo").asText())) {
                return mensagem;
            }
        }
        throw new AssertionError("mensagem não encontrada na timeline: " + conteudo + " corpo=" + resposta.getBody());
    }

    private void inserirDoCliente(String conteudo) {
        jdbc.update(
                """
                INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, status_entrega, enviado_em)
                VALUES (?, ?, 'LEAD', 'TEXTO', ?, 'ENTREGUE', ?)
                """,
                UUID.randomUUID(),
                atendimentoId,
                conteudo,
                Timestamp.from(Instant.now()));
    }

    private static String telefoneNacional() {
        long sufixo = 900000000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 99999999L);
        return "(11) 9" + String.valueOf(sufixo).substring(1, 5) + "-" + String.valueOf(sufixo).substring(5, 9);
    }
}
