package com.synapse.crm.app.automacao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Ponta a ponta dos contratos E51, incluindo os negativos de seguranca e catalogo. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.seguranca.token-interno=e51-token",
            "synapse.suporte.tamanho-pagina=2",
            "synapse.automacao.resumo-ia-tamanho-maximo=24"
        })
class ContratosInternosAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "e51-token";
    private static final String PREFIXO = "E51-";
    private static final Instant INICIO = Instant.parse("2035-01-10T10:00:00Z");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM comando_automacao_idempotencia WHERE atendimento_id IN "
                        + "(SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM audit_log WHERE entidade_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM lembrete WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM lead_tag WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM tag WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    void emAndamentoExcluiFinalizadoFiltraAtividadeLimitaPaginaENaoVazaConversa()
            throws Exception {
        UUID responsavel = usuario("ana@dev.local");
        UUID antigo = criarAtendimento("ANTIGO", "EM_IA", null, INICIO.minusSeconds(3_600));
        UUID ia = criarAtendimento("IA", "EM_IA", null, INICIO);
        UUID humano = criarAtendimento("HUMANO", "EM_ATENDIMENTO", responsavel, INICIO.plusSeconds(60));
        UUID terceiro = criarAtendimento("TERCEIRO", "EM_IA", null, INICIO.plusSeconds(120));
        UUID finalizado = criarAtendimento("FINAL", "FINALIZADO", responsavel, INICIO.plusSeconds(180));
        inserirMensagem(ia, "segredo que nao pode vazar", INICIO.plusSeconds(30));

        ResponseEntity<String> limitada = chamar(
                HttpMethod.GET,
                "/internal/v1/atendimentos/em-andamento?atividadeDesde=" + INICIO + "&tamanho=99",
                null,
                null);

        assertThat(limitada.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode pagina = json.readTree(limitada.getBody());
        assertThat(pagina.path("tamanho").asInt()).isEqualTo(2);
        assertThat(pagina.path("atendimentos")).hasSize(2);
        assertThat(pagina.path("temMais").asBoolean()).isTrue();
        assertThat(limitada.getBody())
                .contains(humano.toString(), terceiro.toString())
                .doesNotContain(antigo.toString(), finalizado.toString(), "segredo", "conteudo", "mensagens");

        ResponseEntity<String> apenasIa = chamar(
                HttpMethod.GET,
                "/internal/v1/atendimentos/em-andamento?atividadeDesde=2035-01-10T10:00:20Z&atividadeAte=2035-01-10T10:00:40Z",
                null,
                null);
        assertThat(apenasIa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apenasIa.getBody())
                .contains(ia.toString(), "2035-01-10T10:00:30Z")
                .doesNotContain(humano.toString(), terceiro.toString(), finalizado.toString());
    }

    @Test
    void lembretePertenceAoResponsavelRetryCriaUmaLinhaESemResponsavelRecusa() {
        UUID responsavel = usuario("ana@dev.local");
        UUID atendimento = criarAtendimento("LEMBRETE", "EM_ATENDIMENTO", responsavel, INICIO);
        Map<String, Object> corpo = Map.of(
                "texto", " Retornar o orçamento ",
                "dataHora", "2035-01-11T13:00:00Z");

        ResponseEntity<String> primeira = chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/lembretes",
                "lembrete-e51",
                corpo);
        ResponseEntity<String> retry = chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/lembretes",
                "lembrete-e51",
                corpo);

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody()).isEqualTo(primeira.getBody());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lembrete WHERE lead_id = (SELECT lead_id FROM atendimento WHERE id = ?)",
                        Integer.class,
                        atendimento))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_id FROM lembrete WHERE lead_id = (SELECT lead_id FROM atendimento WHERE id = ?)",
                        UUID.class,
                        atendimento))
                .isEqualTo(responsavel);
        assertThat(jdbc.queryForObject(
                        "SELECT origem_automatica FROM lembrete WHERE lead_id = (SELECT lead_id FROM atendimento WHERE id = ?)",
                        Boolean.class,
                        atendimento))
                .isTrue();

        UUID semResponsavel = criarAtendimento("SEM-RESPONSAVEL", "EM_IA", null, INICIO);
        ResponseEntity<String> recusado = chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + semResponsavel + "/lembretes",
                "lembrete-sem-responsavel",
                corpo);
        assertThat(recusado.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(recusado.getBody()).contains("nao possui atendente responsavel");
    }

    @Test
    void resumoSobrescreveApareceNaFichaERecusaAcimaDoLimite() {
        UUID responsavel = usuario("ana@dev.local");
        UUID atendimento = criarAtendimento("RESUMO", "EM_ATENDIMENTO", responsavel, INICIO);
        UUID lead = leadDo(atendimento);

        assertThat(chamar(
                                HttpMethod.POST,
                                "/internal/v1/atendimentos/" + atendimento + "/resumo",
                                null,
                                Map.of("resumo", "Primeiro resumo"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(
                                HttpMethod.POST,
                                "/internal/v1/atendimentos/" + atendimento + "/resumo",
                                null,
                                Map.of("resumo", "Resumo final"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        String tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenGestor);
        ResponseEntity<String> ficha = http.exchange(
                "/api/v1/leads/" + lead,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ficha.getBody()).contains("\"resumoIa\":\"Resumo final\"");

        ResponseEntity<String> longo = chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/resumo",
                null,
                Map.of("resumo", "x".repeat(25)));
        assertThat(longo.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(longo.getBody()).contains("limite de 24 caracteres");
        assertThat(jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, lead))
                .isEqualTo("Resumo final");
    }

    @Test
    void tagsUsamCatalogoReaplicacaoESucessoEAAutomacaoFicaAuditada() {
        UUID atendimento = criarAtendimento("TAG", "EM_IA", null, INICIO);
        UUID lead = leadDo(atendimento);
        UUID tag = criarTag("CATALOGO");

        ResponseEntity<String> catalogo = chamar(HttpMethod.GET, "/internal/v1/tags", null, null);
        assertThat(catalogo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(catalogo.getBody()).contains(tag.toString(), PREFIXO + "CATALOGO");

        Map<String, Object> corpo = Map.of("tagId", tag.toString());
        ResponseEntity<String> primeira = chamar(
                HttpMethod.POST, "/internal/v1/leads/" + lead + "/tags", null, corpo);
        ResponseEntity<String> reaplicada = chamar(
                HttpMethod.POST, "/internal/v1/leads/" + lead + "/tags", null, corpo);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reaplicada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lead_tag WHERE lead_id = ? AND tag_id = ?",
                        Integer.class,
                        lead,
                        tag))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE entidade_id = ? AND acao = 'APLICAR_TAG_PELA_AUTOMACAO' AND ator_tipo = 'AUTOMACAO' AND ator_id IS NULL",
                        Integer.class,
                        lead))
                .isEqualTo(2);

        UUID inexistente = UUID.randomUUID();
        ResponseEntity<String> invalida = chamar(
                HttpMethod.POST,
                "/internal/v1/leads/" + lead + "/tags",
                null,
                Map.of("tagId", inexistente.toString()));
        assertThat(invalida.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalida.getBody()).contains(inexistente.toString(), "nao existe no catalogo");
    }

    @Test
    void todosOsContratosRecusamChamadaSemRoleServico() {
        UUID responsavel = usuario("ana@dev.local");
        UUID atendimento = criarAtendimento("SEGURANCA", "EM_ATENDIMENTO", responsavel, INICIO);
        UUID lead = leadDo(atendimento);
        UUID tag = criarTag("SEGURANCA");

        assertSemServico(HttpMethod.GET, "/internal/v1/atendimentos/em-andamento", null, null);
        assertSemServico(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/lembretes",
                "sem-servico",
                Map.of("texto", "teste", "dataHora", "2035-01-11T13:00:00Z"));
        assertSemServico(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/resumo",
                null,
                Map.of("resumo", "teste"));
        assertSemServico(HttpMethod.GET, "/internal/v1/tags", null, null);
        assertSemServico(
                HttpMethod.POST,
                "/internal/v1/leads/" + lead + "/tags",
                null,
                Map.of("tagId", tag.toString()));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lembrete WHERE lead_id = ?", Integer.class, lead))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lead_tag WHERE lead_id = ?", Integer.class, lead))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, lead))
                .isNull();
    }

    private void assertSemServico(
            HttpMethod metodo, String url, String chave, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        if (chave != null) {
            headers.set("Idempotency-Key", chave);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta =
                http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> chamar(
            HttpMethod metodo, String url, String chave, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        if (chave != null) {
            headers.set("Idempotency-Key", chave);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private UUID criarAtendimento(String marcador, String status, UUID responsavel, Instant inicio) {
        UUID lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id,nome,atendente_responsavel_id,status_basico,ultima_interacao_em) VALUES (?,?,?,?::status_basico_lead,?)",
                lead,
                PREFIXO + marcador,
                responsavel,
                responsavel == null ? "IA" : status,
                Timestamp.from(inicio));
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em,finalizado_em) VALUES (?,?,?,?::status_atendimento,?,?)",
                atendimento,
                lead,
                responsavel,
                status,
                Timestamp.from(inicio),
                "FINALIZADO".equals(status) ? Timestamp.from(inicio.plusSeconds(1)) : null);
        return atendimento;
    }

    private void inserirMensagem(UUID atendimento, String conteudo, Instant enviadaEm) {
        jdbc.update(
                "INSERT INTO mensagem (id,atendimento_id,remetente_tipo,tipo,conteudo,status_entrega,enviado_em) VALUES (?,?, 'LEAD','TEXTO',?,'ENVIADO',?)",
                UUID.randomUUID(),
                atendimento,
                conteudo,
                Timestamp.from(enviadaEm));
    }

    private UUID criarTag(String marcador) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO tag (id,nome,cor,icone) VALUES (?,?,?,?)",
                id,
                PREFIXO + marcador,
                "primary",
                "tag");
        return id;
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID leadDo(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT lead_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }
}
