package com.synapse.crm.app.campanha;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** A API REST de campanhas: permissoes por papel, formato dos erros, fluxo completo do administrador e CSV. */
class CampanhaApiIT extends CampanhaITBase {

    private static final String BASE = "/api/v1/campanhas";

    @Autowired
    private TestRestTemplate http;

    private String tokenAdmin;
    private String tokenGestor;
    private String tokenAtendente;

    @BeforeEach
    void entrar() {
        tokenAdmin = ApoioAutenticacao.login(http, EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR).accessToken();
        tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        tokenAtendente = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
    }

    @Test
    @DisplayName("atendente recebe 403 em toda rota de campanhas; sem token, 401")
    void atendente_naoAcessa() {
        assertThat(chamar(tokenAtendente, HttpMethod.GET, BASE, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenAtendente, HttpMethod.GET, BASE + "/templates", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenAtendente, HttpMethod.GET, BASE + "/configuracao", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(null, HttpMethod.GET, BASE, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("gestor le a lista, os templates e a configuracao, mas nao cria, dispara nem altera nada")
    void gestor_leMasNaoAge() {
        criarLeadsElegiveis(2);
        String id = campanhaCriadaPeloAdmin();

        ResponseEntity<Map> lista = chamar(tokenGestor, HttpMethod.GET, BASE, null);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lista.getBody()).containsKeys("itens", "indicadores", "tetoDiario", "enfileiradasHoje", "total");
        assertThat(chamarLista(tokenGestor, BASE + "/templates").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chamar(tokenGestor, HttpMethod.GET, BASE + "/configuracao", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(tokenGestor, HttpMethod.GET, BASE + "/" + id, null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(chamar(tokenGestor, HttpMethod.POST, BASE, corpoDaCampanha("Outra")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        for (String acao : List.of("iniciar", "pausar", "retomar", "cancelar")) {
            assertThat(chamar(tokenGestor, HttpMethod.POST, BASE + "/" + id + "/" + acao, null).getStatusCode())
                    .as(acao)
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(chamar(tokenGestor, HttpMethod.PUT, BASE + "/" + id + "/limite", Map.of("limiteDiario", 5)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenGestor, HttpMethod.PUT, BASE + "/configuracao", Map.of("tetoDiarioDaInstancia", 50))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("revogacao de campanhas.ver no perfil bloqueia a API real sem alterar o teto do administrador")
    void campanhasRevogadasNoPerfilBloqueiamEndpoint() {
        try {
            jdbc.update("""
                    INSERT INTO permissao_perfil_item (papel, tipo, alvo, valor)
                    VALUES ('SUBGESTOR', 'ACAO', 'campanhas.ver', 'NEGAR')
                    ON CONFLICT (papel, tipo, alvo) DO UPDATE SET valor = EXCLUDED.valor
                    """);
            String tokenSubgestor = ApoioAutenticacao.login(http,
                    ApoioAutenticacao.EMAIL_SUBGESTOR, ApoioAutenticacao.SENHA_SUBGESTOR).accessToken();
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                    chamar(tokenSubgestor, HttpMethod.GET, BASE, null).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
            assertThat(chamar(tokenAdmin, HttpMethod.GET, BASE, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        } finally {
            jdbc.update("DELETE FROM permissao_perfil_item WHERE papel = 'SUBGESTOR' AND alvo = 'campanhas.ver'");
        }
    }

    @Test
    @DisplayName("revogar registro de opt-out bloqueia apenas a escrita e preserva leitura da campanha")
    void registroDeOptOutRevogadoNoPerfil() {
        UUID lead = criarLeadsElegiveis(1).get(0);
        try {
            jdbc.update("""
                    INSERT INTO permissao_perfil_item (papel, tipo, alvo, valor)
                    VALUES ('SUBGESTOR', 'ACAO', 'campanhas.registrar_opt_out', 'NEGAR')
                    ON CONFLICT (papel, tipo, alvo) DO UPDATE SET valor = EXCLUDED.valor
                    """);
            String tokenSubgestor = ApoioAutenticacao.login(http,
                    ApoioAutenticacao.EMAIL_SUBGESTOR, ApoioAutenticacao.SENHA_SUBGESTOR).accessToken();
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                    chamar(tokenSubgestor, HttpMethod.PUT, BASE + "/optouts/" + lead, Map.of("motivo", "pediu"))
                            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThat(chamar(tokenSubgestor, HttpMethod.GET, BASE, null).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM contato_optout WHERE lead_id = ?", Integer.class, lead))
                    .isZero();
        } finally {
            jdbc.update("DELETE FROM permissao_perfil_item WHERE papel = 'SUBGESTOR' AND alvo = 'campanhas.registrar_opt_out'");
        }
    }

    @Test
    @DisplayName("fluxo do administrador: templates, previa, criar, iniciar, enviar, detalhe, destinatarios e CSV")
    void administrador_fluxoCompleto() {
        criarLeadsElegiveis(4);

        ResponseEntity<List> templatesDaConta = chamarLista(tokenAdmin, BASE + "/templates");
        assertThat(templatesDaConta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(templatesDaConta.getBody()).anySatisfy(item -> {
            Map<?, ?> template = (Map<?, ?>) item;
            assertThat(template.get("nome")).isEqualTo(TEMPLATE);
            assertThat(template.get("elegivel")).isEqualTo(true);
        });

        ResponseEntity<Map> previa =
                chamar(tokenAdmin, HttpMethod.POST, BASE + "/previa", Map.of("filtro", Map.of("busca", PREFIXO)));
        assertThat(previa.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(previa.getBody()).containsEntry("elegiveis", 4).containsEntry("total", 4);

        ResponseEntity<Map> projecao = chamar(
                tokenAdmin,
                HttpMethod.POST,
                BASE + "/projecao",
                Map.of(
                        "filtro", Map.of("busca", PREFIXO),
                        "limiteDiario", 2,
                        "janela", janela(),
                        "ritmoPorMinuto", 600));
        assertThat(projecao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(projecao.getBody()).containsEntry("destinatarios", 4).containsEntry("completa", true);
        assertThat((List<?>) projecao.getBody().get("dias")).hasSize(2);

        ResponseEntity<Map> criada = chamar(tokenAdmin, HttpMethod.POST, BASE, corpoDaCampanha("Natal"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(criada.getHeaders().getLocation()).isNotNull();
        String id = (String) criada.getBody().get("id");
        assertThat(criada.getBody()).containsEntry("status", "RASCUNHO");

        ResponseEntity<Map> iniciada = chamar(tokenAdmin, HttpMethod.POST, BASE + "/" + id + "/iniciar", null);
        assertThat(iniciada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(iniciada.getBody()).containsEntry("status", "EM_ANDAMENTO");

        rodarCiclo();

        Map<?, ?> detalhe = chamar(tokenAdmin, HttpMethod.GET, BASE + "/" + id, null).getBody();
        Map<?, ?> contadores = (Map<?, ?>) ((Map<?, ?>) detalhe.get("campanha")).get("contadores");
        assertThat(contadores.get("enfileirados")).isEqualTo(2);
        assertThat(contadores.get("pendentes")).isEqualTo(2);
        assertThat(detalhe.get("limiteEfetivoHoje")).isEqualTo(2);
        assertThat((List<?>) detalhe.get("porDia")).hasSize(1);

        ResponseEntity<Map> destinatarios = chamar(
                tokenAdmin, HttpMethod.GET, BASE + "/" + id + "/destinatarios?status=ENFILEIRADO&tamanho=10", null);
        assertThat(destinatarios.getBody()).containsEntry("total", 2);
        assertThat((List<?>) destinatarios.getBody().get("itens")).hasSize(2);

        ResponseEntity<String> csv = http.exchange(
                BASE + "/" + id + "/destinatarios.csv",
                HttpMethod.GET,
                new HttpEntity<>(cabecalhos(tokenAdmin)),
                String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(csv.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        List<String> linhas = csv.getBody().lines().toList();
        assertThat(linhas.get(0)).startsWith("nome,telefone,status,motivo");
        assertThat(linhas).hasSize(5);

        assertThat(chamar(tokenAdmin, HttpMethod.PUT, BASE + "/" + id + "/limite", Map.of("limiteDiario", 4)).getBody())
                .containsEntry("limiteDiario", 4);
        assertThat(chamar(tokenAdmin, HttpMethod.POST, BASE + "/" + id + "/pausar", null).getBody())
                .containsEntry("status", "PAUSADA");
        assertThat(chamar(tokenAdmin, HttpMethod.PUT, BASE + "/" + id + "/interruptor", Map.of("desligada", true))
                        .getBody())
                .containsEntry("desligada", true);
        assertThat(chamar(tokenAdmin, HttpMethod.POST, BASE + "/" + id + "/retomar", null).getBody())
                .containsEntry("status", "EM_ANDAMENTO");
        assertThat(chamar(tokenAdmin, HttpMethod.POST, BASE + "/" + id + "/cancelar", null).getBody())
                .containsEntry("status", "CANCELADA");
    }

    @Test
    @DisplayName("erros em RFC 7807 com codigo estavel: 404, 409 de status, 422 de regra, 4xx de validacao")
    void erros() {
        String id = campanhaCriadaPeloAdmin();

        ResponseEntity<Map> naoExiste = chamar(tokenAdmin, HttpMethod.GET, BASE + "/" + UUID.randomUUID(), null);
        assertThat(naoExiste.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(naoExiste.getBody()).containsEntry("codigo", "CAMPANHA_NAO_ENCONTRADA");

        ResponseEntity<Map> status = chamar(tokenAdmin, HttpMethod.POST, BASE + "/" + id + "/retomar", null);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(status.getBody()).containsEntry("codigo", "STATUS_INVALIDO").containsEntry("statusAtual", "RASCUNHO");

        ResponseEntity<Map> semDestinatarios = chamar(tokenAdmin, HttpMethod.POST, BASE + "/" + id + "/iniciar", null);
        assertThat(semDestinatarios.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(semDestinatarios.getBody()).containsEntry("codigo", "SEM_DESTINATARIOS");

        ResponseEntity<Map> acimaDoTeto =
                chamar(tokenAdmin, HttpMethod.PUT, BASE + "/" + id + "/limite", Map.of("limiteDiario", 9999));
        assertThat(acimaDoTeto.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        ResponseEntity<Map> semNome = chamar(tokenAdmin, HttpMethod.POST, BASE, Map.of("templateNome", TEMPLATE));
        assertThat(semNome.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("sem a funcionalidade habilitada toda rota responde 404, como se nao existisse")
    void funcionalidadeDesligada_404() {
        jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'campanhas'");

        ResponseEntity<Map> resposta = chamar(tokenAdmin, HttpMethod.GET, BASE, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).containsEntry("codigo", "CAMPANHAS_INDISPONIVEIS");
    }

    @Test
    @DisplayName("opt-out pela API: a gestao registra, so o administrador desfaz")
    void optOut() {
        UUID lead = criarLeadsElegiveis(1).get(0);

        assertThat(chamar(tokenGestor, HttpMethod.PUT, BASE + "/optouts/" + lead, Map.of("motivo", "pediu"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<Map> lista = chamar(tokenGestor, HttpMethod.GET, BASE + "/optouts", null);
        assertThat(lista.getBody()).containsEntry("total", 1);
        assertThat(chamar(tokenGestor, HttpMethod.DELETE, BASE + "/optouts/" + lead, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenAdmin, HttpMethod.DELETE, BASE + "/optouts/" + lead, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(chamar(tokenAdmin, HttpMethod.DELETE, BASE + "/optouts/" + lead, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("configuracao pela API: o administrador altera, o valor fora da faixa e recusado")
    void configuracaoPelaApi() {
        ResponseEntity<Map> alterada = chamar(
                tokenAdmin,
                HttpMethod.PUT,
                BASE + "/configuracao",
                Map.of("tetoDiarioDaInstancia", 300, "limiteMetaInformado", 1000));
        assertThat(alterada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(alterada.getBody())
                .containsEntry("tetoDiarioDaInstancia", 300)
                .containsEntry("limiteMetaInformado", 1000);

        assertThat(chamar(tokenAdmin, HttpMethod.PUT, BASE + "/configuracao", Map.of("tetoDiarioDaInstancia", 0))
                        .getStatusCode()
                        .is4xxClientError())
                .isTrue();
    }

    // --- apoio ----------------------------------------------------------------------------------------

    private String campanhaCriadaPeloAdmin() {
        ResponseEntity<Map> criada = chamar(tokenAdmin, HttpMethod.POST, BASE, corpoDaCampanha("Natal"));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) criada.getBody().get("id");
    }

    private static Map<String, Object> janela() {
        return Map.of("inicio", "00:00", "fim", "23:59:59", "dias", List.of(1, 2, 3, 4, 5, 6, 7));
    }

    private static Map<String, Object> corpoDaCampanha(String nome) {
        return Map.of(
                "nome", nome,
                "templateNome", TEMPLATE,
                "templateIdioma", IDIOMA,
                "variaveis", List.of(Map.of("posicao", 1, "campo", "PRIMEIRO_NOME", "reserva", "cliente")),
                "filtro", Map.of("busca", PREFIXO),
                "limiteDiario", 2,
                "janela", janela(),
                "ritmoPorMinuto", 600);
    }

    private HttpHeaders cabecalhos(String token) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (token != null) {
            cabecalhos.setBearerAuth(token);
        }
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.setAccept(List.of(MediaType.ALL));
        return cabecalhos;
    }

    private ResponseEntity<Map> chamar(String token, HttpMethod metodo, String url, Object corpo) {
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos(token)), Map.class);
    }

    private ResponseEntity<List> chamarLista(String token, String url) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(cabecalhos(token)), List.class);
    }
}
