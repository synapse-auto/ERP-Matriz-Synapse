package com.synapse.crm.app.cancelamentos;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.relatorios.application.cancelamentos.FonteCancelamentosException;

/** HTTP real, PostgreSQL de permissoes real; fonte de dados externa substituida APENAS no teste. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(FonteCancelamentosDeTeste.Config.class)
class CancelamentosIT extends PostgresIT {
    private static final String LISTA = "/api/v1/cancelamentos?inicio=2026-10-01&fim=2026-10-31";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired FonteCancelamentosDeTeste fonte;
    private String gestor, ana;

    @BeforeEach void preparar() {
        fonte.limpar();
        limpar();
        gestor = login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        ana = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        flag(true);
    }
    @AfterEach void limpar() {
        jdbc.update("UPDATE feature_flag SET habilitado=false WHERE chave='cancelamentos'");
        jdbc.update("DELETE FROM permissao_perfil_item WHERE alvo IN ('cancelamentos','cancelamentos.ver')");
    }
    void flag(boolean valor) {
        jdbc.update("UPDATE feature_flag SET habilitado=? WHERE chave='cancelamentos'", valor);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(get(gestor, "/api/v1/cancelamentos/configuracao").getStatusCode().value()).isEqualTo(valor ? 200 : 403));
    }
    ResponseEntity<JsonNode> get(String token, String rota) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token);
        return http.exchange(java.net.URI.create(http.getRootUri()+rota), HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }
    void conceder() {
        long revisao = jdbc.queryForObject("SELECT revisao FROM permissao_perfil WHERE papel='ATENDENTE'", Long.class);
        var headers = new HttpHeaders(); headers.setBearerAuth(gestor); headers.setContentType(MediaType.APPLICATION_JSON);
        var r = http.exchange("/api/v1/gestao/permissoes/perfis/ATENDENTE", HttpMethod.PUT,
            new HttpEntity<>(Map.of("revisaoEsperada", revisao, "niveis", Map.of("cancelamentos", "VER"),
                "acoes", Map.of("cancelamentos.ver",true)), headers), String.class);
        assertThat(r.getStatusCode().value()).as(r.getBody()).isEqualTo(200);
    }
    @Test void flagNasceDesligadaENegaUrlDiretaInclusiveGestao() {
        flag(false);
        assertThat(get(gestor,LISTA).getStatusCode().value()).isEqualTo(403);
        assertThat(get(gestor,"/api/v1/cancelamentos/teste-3").getStatusCode().value()).isEqualTo(403);
        assertThat(fonte.ultimoUsuario).isNull();
    }
    @Test void semPermissaoOuSemSessaoNaoAlcancaListaNemDetalhe() {
        assertThat(get(ana,LISTA).getStatusCode().value()).isEqualTo(403);
        assertThat(get(ana,"/api/v1/cancelamentos/teste-3").getStatusCode().value()).isEqualTo(403);
        assertThat(http.getForEntity(LISTA,JsonNode.class).getStatusCode().value()).isEqualTo(401);
        assertThat(fonte.ultimoUsuario).isNull();
    }
    @Test void concessaoPelaGestaoNaoAmpliaEscopoEDetalheAlheioInexistenteSaoIndistinguiveis() {
        conceder();
        var r = get(ana,LISTA);
        assertThat(r.getStatusCode().value()).isEqualTo(200);
        assertThat(r.getBody().path("itens")).hasSize(1);
        assertThat(fonte.ultimoUsuario.enxergaTodosOsLeads()).isFalse();
        assertThat(r.getBody().toString()).contains("teste-3").doesNotContain("teste-2", "Contato sem correlacao");
        assertThat(get(ana,"/api/v1/cancelamentos/teste-2").getStatusCode().value()).isEqualTo(404);
        assertThat(get(ana,"/api/v1/cancelamentos/inexistente").getStatusCode().value()).isEqualTo(404);
        assertThat(get(ana,"/api/v1/cancelamentos/teste-3").getStatusCode().value()).isEqualTo(200);
    }
    @Test void filtraNoServidorCombinaCriteriosEPaginaSemTotalGlobal() {
        var r = get(gestor,LISTA+"&tamanho=1");
        assertThat(r.getBody().path("itens").get(0).path("id").asText()).isEqualTo("teste-3");
        assertThat(r.getBody().path("temProxima").asBoolean()).isTrue();
        assertThat(r.getBody().has("total")).isFalse();
        var segunda = get(gestor,LISTA+"&pagina=1&tamanho=1");
        assertThat(segunda.getBody().path("itens").get(0).path("id").asText()).isEqualTo("teste-2");
        var filtro = get(gestor,LISTA+"&nome=Ana&profissional=Profissional%20A&tipo=Consulta&descricao=COM_DESCRICAO");
        assertThat(filtro.getBody().path("itens")).hasSize(1);
        assertThat(fonte.ultimoFiltro.profissional()).isEqualTo("Profissional A");
        var vazio = get(gestor,LISTA+"&nome=NaoExiste");
        assertThat(vazio.getStatusCode().value()).isEqualTo(200);
        assertThat(vazio.getBody().path("itens")).isEmpty();
        assertThat(get(gestor,LISTA+"&descricao=SEM_DESCRICAO").getBody().path("itens")).hasSize(2);
    }
    @Test void distingueBaseTemporalFusoELimiteLocal() {
        assertThat(get(gestor,"/api/v1/cancelamentos?inicio=2026-10-01&fim=2026-10-01").getBody().path("itens")).hasSize(1);
        assertThat(fonte.ultimoFiltro.inicio()).hasToString("2026-10-01T03:00:00Z");
        assertThat(get(gestor,"/api/v1/cancelamentos?inicio=2026-10-25&fim=2026-10-25&base=AGENDAMENTO").getBody().path("itens")).hasSize(1);
    }
    @Test void falhasDaFonteNaoViramVazioNemVazamDados() {
        for (var codigo : FonteCancelamentosException.Codigo.values()) {
            fonte.falha = codigo;
            for (String rota : new String[] {LISTA,"/api/v1/cancelamentos/teste-3"}) {
                var r = get(gestor,rota);
                assertThat(r.getStatusCode().value()).isEqualTo(503);
                assertThat(r.getHeaders().getContentType().toString()).contains("application/problem+json");
                assertThat(r.getBody().path("codigo").asText()).isEqualTo(codigo.name());
                assertThat(r.getBody().toString()).doesNotContain("Contato", "Profissional", "jdbc:", "token", "stackTrace");
            }
        }
    }
    @Test void limitesInvalidosFalhamAntesDaFonteSemEcoarFiltros() {
        for (String extra : new String[] {"&pagina=-1","&tamanho=101","&pagina=100","&base=ERRADA"})
            assertThat(get(gestor,LISTA+extra).getStatusCode().value()).isEqualTo(400);
        assertThat(get(gestor,"/api/v1/cancelamentos?inicio=2020-01-01&fim=2026-10-31").getStatusCode().value()).isEqualTo(400);
        assertThat(fonte.ultimoUsuario).isNull();
    }
    @Test void detalhePreservaQuebrasEAusenciaDeCamposNaoInventaHorarioOuMotivo() {
        assertThat(get(gestor,"/api/v1/cancelamentos/teste-3").getBody().path("descricao").asText()).contains("\n");
        var vazio = get(gestor,"/api/v1/cancelamentos/teste-1").getBody();
        assertThat(vazio.path("agendadoPara").isNull()).isTrue();
        assertThat(vazio.path("descricao").isNull()).isTrue();
        assertThat(vazio.has("canceladoEm")).isFalse();
    }
}
