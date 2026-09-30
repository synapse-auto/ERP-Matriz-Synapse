package com.synapse.crm.app.canal;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.canal.TemplateDoCanal;

/**
 * Templates cujo nome contem "interno" sao do ADMINISTRADOR — e so dele. Tudo pelo HTTP real, com
 * JWT real, Postgres real e o provedor fake contando o que chega a ele.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.canal.whatsapp.provedor=fake")
class TemplatesRestritosIT extends PostgresIT {

    private static final String PREFIXO = "tpl-restrito-it-";
    private static final String URL = "/api/v1/whatsapp/templates";

    private static final TemplateDoCanal COMUM = template("meta-comum", "aviso_cliente");
    private static final TemplateDoCanal INTERNACIONAL = template("meta-internacional", "internacional_boas_vindas");
    private static final TemplateDoCanal RESTRITO = template("meta-restrito", "aviso_interno_cliente");
    private static final TemplateDoCanal RESTRITO_MAIUSCULO = template("meta-restrito-2", "AVISO_Interno_Equipe");

    /** Papeis que nao podem alcancar template restrito. GESTOR nao e excecao. */
    enum NaoAdministrador {
        ATENDENTE(EMAIL_ANA, SENHA_ATENDENTE),
        SUBGESTOR(EMAIL_SUBGESTOR, SENHA_SUBGESTOR),
        GESTOR(EMAIL_GESTOR, SENHA_GESTOR);

        final String email;
        final String senha;

        NaoAdministrador(String email, String senha) {
            this.email = email;
            this.senha = senha;
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CanalFake canal;

    @BeforeEach
    void semear() {
        canal.limpar();
        List.of(COMUM, INTERNACIONAL, RESTRITO, RESTRITO_MAIUSCULO).forEach(canal::semearTemplate);
    }

    @AfterEach
    void limpar() {
        canal.limpar();
        String like = PREFIXO + "%";
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", like);
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", like);
        jdbc.update(
                "DELETE FROM outbox_evento WHERE payload->>'atendimentoId' IN "
                        + "(SELECT id::text FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                like);
        jdbc.update(
                "DELETE FROM mensagem_envio_idempotencia WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                like);
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN "
                        + "(SELECT id FROM lead WHERE nome LIKE ?))",
                like);
        jdbc.update(
                "DELETE FROM atendimento_participante WHERE atendimento_id IN (SELECT id FROM atendimento "
                        + "WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                like);
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", like);
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", like);
    }

    // --- listagem ---------------------------------------------------------------

    @Test
    @DisplayName("ADMINISTRADOR recebe templates comuns e restritos, em qualquer caixa")
    void administradorListaTudo() throws Exception {
        ResponseEntity<String> resposta = chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR, HttpMethod.GET, URL, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nomes(resposta)).containsExactlyInAnyOrder(
                "aviso_cliente", "internacional_boas_vindas", "aviso_interno_cliente", "AVISO_Interno_Equipe");
    }

    @ParameterizedTest
    @EnumSource(NaoAdministrador.class)
    @DisplayName("ATENDENTE, SUBGESTOR e GESTOR recebem apenas os comuns — internacional nao e filtrado")
    void naoAdministradorRecebeSoComuns(NaoAdministrador papel) throws Exception {
        ResponseEntity<String> resposta = chamar(papel.email, papel.senha, HttpMethod.GET, URL, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nomes(resposta)).containsExactlyInAnyOrder("aviso_cliente", "internacional_boas_vindas");
        assertThat(resposta.getBody()).doesNotContainIgnoringCase("interno");
    }

    // --- criacao ----------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(NaoAdministrador.class)
    @DisplayName("criar template com nome restrito vira 403 e nao chega ao provedor")
    void naoAdministradorNaoCriaRestrito(NaoAdministrador papel) {
        ResponseEntity<String> resposta = chamar(
                papel.email, papel.senha, HttpMethod.POST, URL, pedidoDeCriacao("Novo INTERNO aviso"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(canal.mutacoesDeTemplate()).isZero();
    }

    @Test
    @DisplayName("administrador continua criando template restrito; gestor continua criando comum")
    void criacaoPermitidaPreservada() {
        ResponseEntity<String> doAdministrador = chamar(
                EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR, HttpMethod.POST, URL, pedidoDeCriacao("novo_interno"));
        ResponseEntity<String> doGestor = chamar(
                EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.POST, URL, pedidoDeCriacao("novo_comum"));

        assertThat(doAdministrador.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(doGestor.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(canal.mutacoesDeTemplate()).isEqualTo(2);
    }

    // --- edicao e exclusao ------------------------------------------------------

    @Test
    @DisplayName("gestao nao edita nem exclui variante restrita: 404, nada chega ao provedor")
    void gestaoNaoEditaNemExcluiRestrito() {
        List<HttpStatus> respostas = new ArrayList<>();
        for (NaoAdministrador papel : List.of(NaoAdministrador.SUBGESTOR, NaoAdministrador.GESTOR)) {
            respostas.add(status(chamar(papel.email, papel.senha, HttpMethod.PUT,
                    URL + "/" + RESTRITO.id(), Map.of("corpo", "Texto novo"))));
            respostas.add(status(chamar(papel.email, papel.senha, HttpMethod.DELETE,
                    URL + "/" + RESTRITO.id() + "?nome=" + RESTRITO.nome(), null)));
            // Nome comum nao acoberta o ID restrito.
            respostas.add(status(chamar(papel.email, papel.senha, HttpMethod.DELETE,
                    URL + "/" + RESTRITO.id() + "?nome=" + COMUM.nome(), null)));
        }

        assertThat(respostas).containsOnly(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
        assertThat(respostas).doesNotContain(HttpStatus.NO_CONTENT);
        assertThat(canal.mutacoesDeTemplate()).isZero();
        assertThat(canal.listarTemplates()).extracting(TemplateDoCanal::id).contains(RESTRITO.id());
    }

    @Test
    @DisplayName("atendente continua barrado pelas permissoes anteriores (403) em editar/excluir")
    void atendenteContinuaSemEdicao() {
        ResponseEntity<String> edicao = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.PUT,
                URL + "/" + COMUM.id(), Map.of("corpo", "Texto novo"));

        assertThat(edicao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(canal.mutacoesDeTemplate()).isZero();
    }

    @Test
    @DisplayName("gestor edita e exclui comum; administrador edita e exclui restrito")
    void edicaoEExclusaoPermitidasPreservadas() {
        assertThat(status(chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.PUT,
                URL + "/" + COMUM.id(), Map.of("corpo", "Texto novo")))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(chamar(EMAIL_GESTOR, SENHA_GESTOR, HttpMethod.DELETE,
                URL + "/" + COMUM.id() + "?nome=" + COMUM.nome(), null))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR, HttpMethod.PUT,
                URL + "/" + RESTRITO.id(), Map.of("corpo", "Texto novo")))).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(status(chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR, HttpMethod.DELETE,
                URL + "/" + RESTRITO.id() + "?nome=" + RESTRITO.nome(), null))).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // --- envio e novo contato ---------------------------------------------------

    @ParameterizedTest
    @EnumSource(NaoAdministrador.class)
    @DisplayName("enviar template restrito vira 403: sem mensagem, sem outbox, sem troca de dono")
    void naoAdministradorNaoEnviaRestrito(NaoAdministrador papel) {
        UUID leadId = leadEmIa();

        ResponseEntity<String> resposta = chamar(papel.email, papel.senha, HttpMethod.POST,
                "/api/v1/atendimentos/mensagens/template", pedidoDeEnvio(leadId, "Aviso_INTERNO_Cliente"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(contarMensagensEOutbox(leadId)).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId))
                .isNull();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    @DisplayName("template comum continua sendo enviado por atendente; administrador envia restrito")
    void envioPermitidoPreservado() {
        UUID leadDoAtendente = leadEmIa();
        UUID leadDoAdministrador = leadEmIa();

        ResponseEntity<String> comum = chamar(EMAIL_ANA, SENHA_ATENDENTE, HttpMethod.POST,
                "/api/v1/atendimentos/mensagens/template", pedidoDeEnvio(leadDoAtendente, COMUM.nome()));
        ResponseEntity<String> restrito = chamar(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR, HttpMethod.POST,
                "/api/v1/atendimentos/mensagens/template", pedidoDeEnvio(leadDoAdministrador, RESTRITO.nome()));

        assertThat(comum.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restrito.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contarMensagensEOutbox(leadDoAtendente)).isPositive();
        assertThat(contarMensagensEOutbox(leadDoAdministrador)).isPositive();
    }

    @ParameterizedTest
    @EnumSource(NaoAdministrador.class)
    @DisplayName("novo contato com template restrito vira 403 e nao cria lead")
    void naoAdministradorNaoIniciaContatoComRestrito(NaoAdministrador papel) {
        String nome = PREFIXO + UUID.randomUUID();

        ResponseEntity<String> resposta = chamar(papel.email, papel.senha, HttpMethod.POST,
                "/api/v1/atendimentos/novo-contato",
                Map.of(
                        "nome", nome,
                        "telefone", telefoneNacional(),
                        "template", Map.of("nome", "aviso_interno_cliente", "idioma", "pt_BR", "parametros", List.of())));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lead WHERE nome = ?", Long.class, nome)).isZero();
        assertThat(canal.enviados()).isEmpty();
    }

    // --- apoio ------------------------------------------------------------------

    private UUID leadEmIa() {
        UUID leadId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, status_basico, ultima_interacao_em) VALUES (?, ?, 'IA', now())",
                leadId,
                PREFIXO + UUID.randomUUID());
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, status, iniciado_em) VALUES (?, ?, 'EM_IA', now())",
                UUID.randomUUID(),
                leadId);
        return leadId;
    }

    private long contarMensagensEOutbox(UUID leadId) {
        Long mensagens = jdbc.queryForObject(
                "SELECT count(*) FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)",
                Long.class,
                leadId);
        Long outbox = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_evento WHERE payload->>'atendimentoId' IN "
                        + "(SELECT id::text FROM atendimento WHERE lead_id = ?)",
                Long.class,
                leadId);
        return mensagens + outbox;
    }

    private static Map<String, Object> pedidoDeCriacao(String nome) {
        return Map.of("nome", nome, "idioma", "pt_BR", "categoria", "UTILIDADE", "corpo", "Ola {{1}}");
    }

    private static Map<String, Object> pedidoDeEnvio(UUID leadId, String nome) {
        return Map.of("leadId", leadId.toString(), "nome", nome, "idioma", "pt_BR", "parametros", List.of());
    }

    private List<String> nomes(ResponseEntity<String> resposta) throws Exception {
        List<String> nomes = new ArrayList<>();
        for (JsonNode item : json.readTree(resposta.getBody())) {
            nomes.add(item.path("nome").asText());
        }
        return nomes;
    }

    private static HttpStatus status(ResponseEntity<String> resposta) {
        return HttpStatus.valueOf(resposta.getStatusCode().value());
    }

    private ResponseEntity<String> chamar(String email, String senha, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static String telefoneNacional() {
        long n = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L;
        return String.format("839%08d", n);
    }

    private static TemplateDoCanal template(String id, String nome) {
        return new TemplateDoCanal(
                id, nome, "pt_BR", TemplateDoCanal.Categoria.UTILIDADE, TemplateDoCanal.Status.APROVADO, "Ola", 0);
    }
}
