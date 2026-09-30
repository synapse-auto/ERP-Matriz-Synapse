package com.synapse.crm.app.gestao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
 * Contrato "Gestão ↔ endpoint" das ações de Atendimentos e do Resumo por IA (docs/47 §14).
 *
 * <p>Para cada capacidade mapeada: com o perfil padrão a ATENDENTE executa a ação na conversa que é dela;
 * revogada no perfil, o mesmo pedido — com o token emitido antes da revogação — responde 403 e não
 * altera nada. É o teste que reprova quando alguém esconde o botão e esquece o {@code @PreAuthorize}, ou
 * quando o interruptor da Gestão deixa de valer no backend. Uma ação nova de Atendimentos entra aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class CapacidadesDeAtendimentoIT extends PostgresIT {

    private static final String PREFIXO = "Capacidade IT ";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private UUID ana;
    private UUID bruno;
    private String tokenAna;
    private String tokenGestor;

    /** Uma linha da matriz: como chamar, e o que prova que a ação aconteceu (ou não). */
    record Acao(
            String capacidade,
            BiFunction<CapacidadesDeAtendimentoIT, Conversa, ResponseEntity<String>> executar,
            BiFunction<CapacidadesDeAtendimentoIT, Conversa, Object> efeito,
            Object semEfeito) {
        @Override
        public String toString() {
            return capacidade;
        }
    }

    record Conversa(UUID lead, UUID atendimento) {}

    static Stream<Arguments> matriz() {
        return Stream.of(
                Arguments.of(new Acao(
                        "atendimentos.transferir",
                        (t, c) -> t.chamar(t.tokenAna, HttpMethod.POST, "/api/v1/atendimentos/" + c.atendimento() + "/transferir",
                                Map.of("paraAtendenteId", t.bruno.toString())),
                        (t, c) -> t.responsavelDoLead(c) + "|" + t.atendenteDoAtendimento(c),
                        "ANA|ANA")),
                Arguments.of(new Acao(
                        "atendimentos.devolver_ia",
                        (t, c) -> t.chamar(t.tokenAna, HttpMethod.POST, "/api/v1/atendimentos/" + c.atendimento() + "/transferir",
                                nulo("paraAtendenteId")),
                        (t, c) -> t.statusDoAtendimento(c),
                        "EM_ATENDIMENTO")),
                Arguments.of(new Acao(
                        "atendimentos.finalizar",
                        (t, c) -> t.chamar(t.tokenAna, HttpMethod.POST, "/api/v1/atendimentos/" + c.atendimento() + "/finalizar", null),
                        (t, c) -> t.statusDoAtendimento(c),
                        "EM_ATENDIMENTO")),
                Arguments.of(new Acao(
                        "resumo_ia.ver",
                        (t, c) -> t.chamar(t.tokenAna, HttpMethod.GET, "/api/v1/atendimentos/" + c.atendimento() + "/resumo-ia", null),
                        (t, c) -> t.resumoNaFicha(c),
                        "(sem resumo)")),
                Arguments.of(new Acao(
                        "resumo_ia.solicitar",
                        (t, c) -> t.solicitarResumo(c),
                        (t, c) -> t.solicitacoesDeResumo(c),
                        0)));
    }

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM permissao_usuario_excecao");
        ana = id(EMAIL_ANA);
        bruno = id(EMAIL_BRUNO);
        tokenAna = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM permissao_usuario_excecao");
        String leads = "(SELECT id FROM lead WHERE nome LIKE '" + PREFIXO + "%')";
        jdbc.update("DELETE FROM solicitacao_resumo_ia WHERE lead_id IN " + leads);
        jdbc.update("DELETE FROM outbox_evento WHERE payload->>'leadId' IN (SELECT id::text FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN " + leads + ")");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN " + leads);
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    @ParameterizedTest(name = "{0}: revogada no perfil responde 403 e não altera nada")
    @MethodSource("matriz")
    void revogadaNoPerfilNegaNoBackend(Acao acao) {
        Conversa conversa = conversaDaAna();
        salvarPerfilAtendente(Map.of(acao.capacidade(), false));

        ResponseEntity<String> resposta = acao.executar().apply(this, conversa);

        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(acao.efeito().apply(this, conversa)).isEqualTo(acao.semEfeito());
    }

    @ParameterizedTest(name = "{0}: permitida no perfil padrão, a atendente executa na própria conversa")
    @MethodSource("matriz")
    void permitidaExecuta(Acao acao) {
        Conversa conversa = conversaDaAna();

        ResponseEntity<String> resposta = acao.executar().apply(this, conversa);

        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isNotIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
        if (!acao.capacidade().equals("resumo_ia.solicitar")) {
            // Sem webhook configurado no IT, a solicitação responde 503 antes de gravar; as demais mudam estado.
            assertThat(resposta.getStatusCode().is2xxSuccessful()).as(resposta.getBody()).isTrue();
        }
    }

    @Test
    @DisplayName("transferir permitido: atendimento e lead terminam com o mesmo responsável (Bruno)")
    void transferirPermitidoMoveAtendimentoELeadJuntos() {
        Conversa conversa = conversaDaAna();

        ResponseEntity<String> resposta = chamar(tokenAna, HttpMethod.POST,
                "/api/v1/atendimentos/" + conversa.atendimento() + "/transferir", Map.of("paraAtendenteId", bruno.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responsavelDoLead(conversa)).isEqualTo("BRUNO");
        assertThat(atendenteDoAtendimento(conversa)).isEqualTo("BRUNO");
    }

    @Test
    @DisplayName("só resumo_ia.solicitar negado: gerar recusa, ler o resumo pronto continua (ficha e estado)")
    void semSolicitarLeituraContinua() throws Exception {
        Conversa conversa = conversaDaAna();
        jdbc.update("UPDATE lead SET resumo_ia = 'resumo pronto' WHERE id = ?", conversa.lead());
        salvarPerfilAtendente(Map.of("resumo_ia.solicitar", false));

        assertThat(solicitarResumo(conversa).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/atendimentos/" + conversa.atendimento() + "/resumo-ia", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(resumoNaFicha(conversa)).isEqualTo("resumo pronto");
    }

    @Test
    @DisplayName("resumo_ia.ver negado: ficha sem o texto e estado do ciclo recusado; restaurar devolve os dois")
    void semVerFechaFichaEEstado() throws Exception {
        Conversa conversa = conversaDaAna();
        jdbc.update("UPDATE lead SET resumo_ia = 'resumo pronto' WHERE id = ?", conversa.lead());
        salvarPerfilAtendente(Map.of("resumo_ia.ver", false, "resumo_ia.solicitar", false));

        assertThat(resumoNaFicha(conversa)).isEqualTo("(sem resumo)");
        assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/atendimentos/" + conversa.atendimento() + "/resumo-ia", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode minhas = ler(chamar(tokenAna, HttpMethod.GET, "/api/v1/gestao/permissoes/minhas", null));
        assertThat(minhas.at("/capacidades/resumo_ia.ver/permitido").asBoolean(true)).isFalse();

        salvarPerfilAtendente(Map.of());
        assertThat(resumoNaFicha(conversa)).isEqualTo("resumo pronto");
    }

    // --- apoio -------------------------------------------------------------------------------

    private Conversa conversaDaAna() {
        UUID lead = UUID.randomUUID();
        Instant agora = Instant.now();
        jdbc.update("INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?, ?)",
                lead, PREFIXO + lead, ana, Timestamp.from(agora), Timestamp.from(agora));
        UUID atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_atendimento, ?)",
                atendimento, lead, ana, Timestamp.from(agora.minusSeconds(60)));
        return new Conversa(lead, atendimento);
    }

    private ResponseEntity<String> solicitarResumo(Conversa conversa) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(tokenAna);
        cabecalhos.set("Idempotency-Key", UUID.randomUUID().toString());
        return http.exchange("/api/v1/atendimentos/" + conversa.atendimento() + "/resumo-ia", HttpMethod.POST,
                new HttpEntity<>(cabecalhos), String.class);
    }

    private String resumoNaFicha(Conversa conversa) {
        JsonNode ficha = ler(chamar(tokenAna, HttpMethod.GET, "/api/v1/leads/" + conversa.lead(), null));
        return ficha.path("resumoIa").isNull() || ficha.path("resumoIa").isMissingNode()
                ? "(sem resumo)"
                : ficha.path("resumoIa").asText();
    }

    private int solicitacoesDeResumo(Conversa conversa) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM solicitacao_resumo_ia WHERE atendimento_id = ?", Integer.class, conversa.atendimento());
    }

    private String responsavelDoLead(Conversa conversa) {
        return quem(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, conversa.lead()));
    }

    private String atendenteDoAtendimento(Conversa conversa) {
        return quem(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, conversa.atendimento()));
    }

    private String statusDoAtendimento(Conversa conversa) {
        return jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, conversa.atendimento());
    }

    private String quem(UUID usuario) {
        if (ana.equals(usuario)) return "ANA";
        if (bruno.equals(usuario)) return "BRUNO";
        return String.valueOf(usuario);
    }

    private void salvarPerfilAtendente(Map<String, Boolean> acoes) {
        long revisao = jdbc.queryForObject(
                "SELECT revisao FROM permissao_perfil WHERE papel = CAST('ATENDENTE' AS papel_usuario)", Long.class);
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("revisaoEsperada", revisao);
        corpo.put("niveis", Map.of());
        corpo.put("acoes", acoes);
        ResponseEntity<String> r = chamar(tokenGestor, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE", corpo);
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    private static Map<String, Object> nulo(String chave) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put(chave, null);
        return corpo;
    }

    private UUID id(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private ResponseEntity<String> chamar(String token, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private JsonNode ler(ResponseEntity<String> resposta) {
        try {
            return json.readTree(resposta.getBody() == null ? "{}" : resposta.getBody());
        } catch (Exception e) {
            throw new AssertionError("corpo ilegivel: " + resposta.getBody(), e);
        }
    }
}
