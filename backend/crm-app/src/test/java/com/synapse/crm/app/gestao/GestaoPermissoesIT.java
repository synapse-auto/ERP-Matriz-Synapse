package com.synapse.crm.app.gestao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
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
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;

/**
 * Gestao ponta a ponta pelos pontos de entrada HTTP, contra Postgres real (docs/47).
 *
 * <p>Cada teste deixa perfis, excecoes e flags como encontrou: outras suites compartilham o banco e
 * nao podem herdar uma revogacao esquecida aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class GestaoPermissoesIT extends PostgresIT {

    private static final String BASE = "/api/v1/gestao/permissoes";
    private static final String PREFIXO = "Gestao IT ";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ResolvedorDePermissoesEfetivas resolvedor;

    private UUID ana;
    private UUID bruno;
    private UUID gestor;
    private UUID subgestor;
    private UUID admin;
    private UUID leadDaAna;
    private UUID leadDoBruno;
    private UUID tag;
    private final List<UUID> criados = new ArrayList<>();

    @BeforeEach
    void preparar() {
        ana = id(EMAIL_ANA);
        bruno = id(EMAIL_BRUNO);
        gestor = id(EMAIL_GESTOR);
        subgestor = id(EMAIL_SUBGESTOR);
        admin = id(EMAIL_ADMINISTRADOR);
        limparPermissoes();
        leadDaAna = criarLead("Ana", ana);
        leadDoBruno = criarLead("Bruno", bruno);
        tag = UUID.randomUUID();
        jdbc.update("INSERT INTO tag (id, nome, cor) VALUES (?, ?, '#000000')", tag, PREFIXO + tag);
    }

    @AfterEach
    void limpar() {
        limparPermissoes();
        jdbc.update("UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'dashboard'");
        jdbc.update("DELETE FROM lead_tag WHERE tag_id = ?", tag);
        jdbc.update("DELETE FROM tag WHERE id = ?", tag);
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        for (UUID u : criados) {
            jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", u);
        }
    }

    // --- acesso a Gestao --------------------------------------------------------------------------

    @Test
    @DisplayName("ATENDENTE nao acessa Gestao: leitura e escrita recusadas (403)")
    void atendenteNaoAcessaGestao() {
        String t = token(EMAIL_ANA, SENHA_ATENDENTE);
        assertThat(chamar(t, HttpMethod.GET, BASE + "/perfis", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(t, HttpMethod.GET, BASE + "/usuarios", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(t, HttpMethod.GET, BASE + "/catalogo", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(t, HttpMethod.PUT, BASE + "/perfis/ATENDENTE",
                corpoPerfil(0, Map.of(), Map.of())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(t, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(0, Map.of(), Map.of("tags.aplicar", true))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode minhas = ler(chamar(t, HttpMethod.GET, BASE + "/minhas", null));
        assertThat(minhas.path("acessaGestao").asBoolean()).isFalse();
        assertThat(minhas.path("capacidades").path("atendimentos.ver").path("alcance").asText()).isEqualTo("MEUS");
    }

    @Test
    @DisplayName("GESTOR ve perfis com contagem real e N de M calculado; SUBGESTOR le sem editar")
    void perfisComContagemReal() {
        JsonNode perfis = ler(chamar(token(EMAIL_GESTOR, SENHA_GESTOR), HttpMethod.GET, BASE + "/perfis", null));
        assertThat(perfis).hasSize(3);
        Map<String, JsonNode> porPapel = new HashMap<>();
        perfis.forEach(p -> porPapel.put(p.path("papel").asText(), p));
        Integer atendentesAtivos = jdbc.queryForObject(
                "SELECT count(*) FROM usuario WHERE ativo AND papel = 'ATENDENTE'", Integer.class);
        assertThat(porPapel.get("ATENDENTE").path("usuarios").asInt()).isEqualTo(atendentesAtivos);
        assertThat(porPapel.get("GESTOR").path("fixo").asBoolean()).isTrue();
        assertThat(porPapel.get("GESTOR").path("editavel").asBoolean()).isFalse();
        assertThat(porPapel.get("ATENDENTE").path("editavel").asBoolean()).isTrue();
        assertThat(porPapel.get("ATENDENTE").path("permitidas").asInt())
                .isEqualTo(porPapel.get("ATENDENTE").path("total").asInt());

        JsonNode doSub = ler(chamar(token(EMAIL_SUBGESTOR, SENHA_SUBGESTOR), HttpMethod.GET, BASE + "/perfis", null));
        doSub.forEach(p -> assertThat(p.path("editavel").asBoolean()).isFalse());
    }

    // --- heranca, negacao e restauracao com efeito real ------------------------------------------

    @Test
    @DisplayName("perfil nega, excecao permite so para Ana, restaurar volta a negar — no MESMO token (sem novo login)")
    void herancaNegacaoRestauracaoComSessaoAnterior() {
        String tokenAna = token(EMAIL_ANA, SENHA_ATENDENTE);
        String tokenBruno = token(EMAIL_BRUNO, SENHA_ATENDENTE);
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        assertThat(aplicarTag(tokenAna, leadDaAna)).isEqualTo(HttpStatus.OK);

        salvarPerfilAtendente(tokenGestor, Map.of("tags.aplicar", false));
        assertThat(aplicarTag(tokenAna, leadDaAna)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(aplicarTag(tokenBruno, leadDoBruno)).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> excecao = chamar(tokenGestor, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), Map.of("tags.aplicar", true)));
        assertThat(excecao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aplicarTag(tokenAna, leadDaAna)).isEqualTo(HttpStatus.OK);
        assertThat(aplicarTag(tokenBruno, leadDoBruno)).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode lista = ler(chamar(tokenGestor, HttpMethod.GET, BASE + "/usuarios", null));
        assertThat(badge(lista, ana)).isEqualTo(1);
        assertThat(badge(lista, bruno)).isZero();

        ResponseEntity<String> restaurar = chamar(tokenGestor, HttpMethod.DELETE,
                BASE + "/usuarios/" + ana + "/excecoes?revisaoEsperada=" + revisaoUsuario(ana), null);
        assertThat(restaurar.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aplicarTag(tokenAna, leadDaAna)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("mudar o perfil atualiza herdeiros sem apagar excecao explicita")
    void perfilNaoApagaExcecao() {
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        chamar(tokenGestor, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), Map.of("resumo_ia.solicitar", false)));
        salvarPerfilAtendente(tokenGestor, Map.of("tags.aplicar", false));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissao_usuario_excecao WHERE usuario_id = ?",
                Integer.class, ana)).isEqualTo(1);
        JsonNode detalhe = ler(chamar(tokenGestor, HttpMethod.GET, BASE + "/usuarios/" + ana, null));
        assertThat(capacidade(detalhe, "resumo_ia.solicitar").path("excecao").asBoolean(true)).isFalse();
        assertThat(capacidade(detalhe, "tags.aplicar").path("permitido").asBoolean()).isFalse();
        assertThat(capacidade(detalhe, "tags.aplicar").path("origem").asText()).isEqualTo("PERFIL");
    }

    @Test
    @DisplayName("resumo_ia.ver negado fecha as duas portas de leitura do resumo")
    void resumoIaFechaFicha() {
        jdbc.update("UPDATE lead SET resumo_ia = 'resumo secreto' WHERE id = ?", leadDaAna);
        String tokenAna = token(EMAIL_ANA, SENHA_ATENDENTE);
        assertThat(ler(chamar(tokenAna, HttpMethod.GET, "/api/v1/leads/" + leadDaAna, null)).path("resumoIa").asText())
                .isEqualTo("resumo secreto");
        salvarPerfilAtendente(token(EMAIL_GESTOR, SENHA_GESTOR),
                Map.of("resumo_ia.ver", false, "resumo_ia.solicitar", false));
        assertThat(ler(chamar(tokenAna, HttpMethod.GET, "/api/v1/leads/" + leadDaAna, null)).path("resumoIa").isNull()).isTrue();
    }

    // --- invariantes: lead de colega, estrutural, teto, flag ---------------------------------------

    @Test
    @DisplayName("excecoes nao ampliam visibilidade: Ana com tudo permitido continua sem alcancar lead do Bruno")
    void excecaoNaoAlcancaLeadDeColega() {
        chamar(token(EMAIL_GESTOR, SENHA_GESTOR), HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), Map.of("tags.aplicar", true, "contatos.editar", true)));
        String tokenAna = token(EMAIL_ANA, SENHA_ATENDENTE);
        assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(aplicarTag(tokenAna, leadDoBruno)).isNotEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("payload adulterado: Todos/assumir colega/fora do teto/desconhecido/estrutural sao 422 e nada persiste")
    void payloadAdulterado() {
        String t = token(EMAIL_GESTOR, SENHA_GESTOR);
        long revisao = revisaoPerfil("ATENDENTE");
        ResponseEntity<String> r = chamar(t, HttpMethod.PUT, BASE + "/perfis/ATENDENTE", corpoPerfil(revisao, Map.of(),
                mapa("atendimentos.ver", true, "atendimentos.assumir_de_colega", true, "tags.criar", true,
                        "equipe.perfis", true, "tags.aplicar", false)));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        JsonNode problema = ler(r);
        assertThat(problema.path("type").asText()).endsWith("permissao-invalida");
        List<String> codigos = new ArrayList<>();
        problema.path("violacoes").forEach(v -> codigos.add(v.path("codigo").asText()));
        assertThat(codigos).contains("DESCONHECIDA");
        // atomicidade: a acao valida (tags.aplicar=false) nao foi gravada; revisao intacta
        assertThat(revisaoPerfil("ATENDENTE")).isEqualTo(revisao);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissao_perfil_item WHERE papel = 'ATENDENTE'", Integer.class)).isZero();

        ResponseEntity<String> semDesconhecida = chamar(t, HttpMethod.PUT, BASE + "/perfis/ATENDENTE", corpoPerfil(revisao,
                mapa("equipe", "GERENCIAR"), mapa("atendimentos.ver", true, "tags.criar", true, "tags.aplicar", false)));
        List<String> codigos2 = new ArrayList<>();
        ler(semDesconhecida).path("violacoes").forEach(v -> codigos2.add(v.path("codigo").asText()));
        assertThat(codigos2).contains("ESTRUTURAL", "FORA_DO_TETO", "NIVEL_FORA_DO_LIMITE");
        assertThat(revisaoPerfil("ATENDENTE")).isEqualTo(revisao);

        assertThat(chamar(t, HttpMethod.PUT, BASE + "/perfis/GESTOR", corpoPerfil(0, Map.of(), Map.of()))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("acao fora da flag some do catalogo e e recusada no payload; religar a flag nao perde nada salvo")
    void acaoForaDaFlag() {
        String t = token(EMAIL_GESTOR, SENHA_GESTOR);
        salvarPerfil(t, "SUBGESTOR", Map.of(), Map.of("dashboard.ver", false));
        jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'dashboard'");
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                ler(chamar(t, HttpMethod.GET, BASE + "/catalogo", null)).path("capacidades").toString())
                .doesNotContain("dashboard.ver"));
        ResponseEntity<String> r = chamar(t, HttpMethod.PUT, BASE + "/perfis/SUBGESTOR",
                corpoPerfil(revisaoPerfil("SUBGESTOR"), Map.of(), Map.of("dashboard.ver", true)));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody()).contains("FLAG_DESLIGADA");
        // salvar outro modulo com a flag desligada preserva a revogacao do dashboard
        salvarPerfil(t, "SUBGESTOR", Map.of(), Map.of("tags.aplicar", true));
        assertThat(jdbc.queryForObject("SELECT valor FROM permissao_perfil_item WHERE papel = 'SUBGESTOR' AND alvo = 'dashboard.ver'",
                String.class)).isEqualTo("NEGAR");
        jdbc.update("UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'dashboard'");
        String tokenSub = token(EMAIL_SUBGESTOR, SENHA_SUBGESTOR);
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(
                chamar(tokenSub, HttpMethod.GET, "/api/v1/dashboard/visao-geral", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // --- concorrencia ------------------------------------------------------------------------------

    @Test
    @DisplayName("versao antiga devolve 409 sem sobrescrever; corrida de N gestores tem exatamente um vencedor")
    void revisaoEConcorrencia() throws Exception {
        String t = token(EMAIL_GESTOR, SENHA_GESTOR);
        long revisao = revisaoPerfil("ATENDENTE");
        assertThat(chamar(t, HttpMethod.PUT, BASE + "/perfis/ATENDENTE",
                corpoPerfil(revisao, Map.of(), Map.of("tags.aplicar", false))).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> antiga = chamar(t, HttpMethod.PUT, BASE + "/perfis/ATENDENTE",
                corpoPerfil(revisao, Map.of(), Map.of("resumo_ia.solicitar", false)));
        assertThat(antiga.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(antiga).path("revisaoAtual").asLong()).isEqualTo(revisao + 1);
        assertThat(jdbc.queryForList("SELECT alvo FROM permissao_perfil_item WHERE papel = 'ATENDENTE'", String.class))
                .containsExactly("tags.aplicar");

        long atual = revisaoPerfil("ATENDENTE");
        String tokenAdmin = token(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR);
        int paralelos = 6;
        ExecutorService pool = Executors.newFixedThreadPool(paralelos);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<HttpStatus>> resultados = new ArrayList<>();
        for (int i = 0; i < paralelos; i++) {
            String quem = i % 2 == 0 ? t : tokenAdmin;
            boolean valor = i % 2 == 0;
            Callable<HttpStatus> tarefa = () -> {
                largada.await();
                return (HttpStatus) chamar(quem, HttpMethod.PUT, BASE + "/perfis/ATENDENTE",
                        corpoPerfil(atual, Map.of(), Map.of("templates.criar", valor))).getStatusCode();
            };
            resultados.add(pool.submit(tarefa));
        }
        largada.countDown();
        int ok = 0;
        int conflito = 0;
        for (Future<HttpStatus> f : resultados) {
            HttpStatus s = f.get();
            if (s == HttpStatus.OK) ok++;
            if (s == HttpStatus.CONFLICT) conflito++;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        assertThat(conflito).isEqualTo(paralelos - 1);
        assertThat(revisaoPerfil("ATENDENTE")).isEqualTo(atual + 1);
    }

    // --- delegacao ---------------------------------------------------------------------------------

    @Test
    @DisplayName("SUBGESTOR sem delegacao nao edita; delegado edita so ATENDENTE, so o delegavel, so o que tem")
    void delegacaoDeSubgestor() {
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        String tokenSub = token(EMAIL_SUBGESTOR, SENHA_SUBGESTOR);
        ResponseEntity<String> semDelegacao = chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), Map.of("tags.aplicar", false)));
        assertThat(semDelegacao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // GESTOR delega: sobe o nivel de equipe e liga so a edicao de excecoes; nega resumo ao subgestor
        assertThat(chamar(tokenGestor, HttpMethod.PUT, BASE + "/usuarios/" + subgestor + "/excecoes",
                corpoExcecoes(revisaoUsuario(subgestor), mapa("equipe", "GERENCIAR"),
                        mapa("equipe.excecoes_atendentes", true, "resumo_ia.solicitar", false))).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), Map.of("tags.aplicar", false))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + subgestor + "/excecoes",
                corpoExcecoes(revisaoUsuario(subgestor), mapa("equipe", "GERENCIAR"),
                        mapa("equipe.excecoes_atendentes", true, "equipe.criar", true))))).isEqualTo("ALVO_PROPRIO");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + gestor + "/excecoes",
                corpoExcecoes(0, Map.of(), Map.of())))).isEqualTo("ALVO_FORA_DA_ALCADA");
        UUID outroSub = criarUsuario(tokenGestor, "SUBGESTOR");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + outroSub + "/excecoes",
                corpoExcecoes(0, Map.of(), Map.of("tags.aplicar", false))))).isEqualTo("ALVO_FORA_DA_ALCADA");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), mapa("tags.aplicar", false, "atendimentos.finalizar_lote", false)))))
                .isEqualTo("FORA_DO_CONJUNTO_DELEGAVEL");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), mapa("tags", "VER"), Map.of("tags.aplicar", false)))))
                .isEqualTo("NIVEL_NAO_DELEGAVEL");
        // Ana herda resumo_ia.solicitar=true; o subgestor nao tem e nao pode ligar explicitamente
        salvarPerfilAtendente(tokenGestor, Map.of("resumo_ia.solicitar", false));
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(revisaoUsuario(ana), Map.of(), mapa("tags.aplicar", false, "resumo_ia.solicitar", true)))))
                .isEqualTo("ACIMA_DA_PROPRIA_PERMISSAO");
        assertThat(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/ATENDENTE",
                corpoPerfil(revisaoPerfil("ATENDENTE"), Map.of(), Map.of())).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("SUBGESTOR com equipe.perfis edita so o perfil ATENDENTE, so o delegavel, so o que tem; nunca o proprio")
    void delegacaoDePerfis() {
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        String tokenSub = token(EMAIL_SUBGESTOR, SENHA_SUBGESTOR);
        // Ana herda do perfil; o subgestor fica sem resumo para provar que nao liga o que nao tem
        salvarPerfilAtendente(tokenGestor, Map.of("resumo_ia.solicitar", false));
        assertThat(chamar(tokenGestor, HttpMethod.PUT, BASE + "/usuarios/" + subgestor + "/excecoes",
                corpoExcecoes(revisaoUsuario(subgestor), mapa("equipe", "GERENCIAR"),
                        mapa("equipe.perfis", true, "resumo_ia.solicitar", false))).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        JsonNode minhas = ler(chamar(tokenSub, HttpMethod.GET, BASE + "/minhas", null));
        assertThat(minhas.path("editaPerfis").asBoolean()).isTrue();
        Map<String, JsonNode> perfis = new HashMap<>();
        ler(chamar(tokenSub, HttpMethod.GET, BASE + "/perfis", null)).forEach(p -> perfis.put(p.path("papel").asText(), p));
        assertThat(perfis.get("ATENDENTE").path("editavel").asBoolean()).isTrue();
        assertThat(perfis.get("SUBGESTOR").path("editavel").asBoolean()).isFalse();
        assertThat(perfis.get("GESTOR").path("editavel").asBoolean()).isFalse();

        // o que ele alcanca: desligar acao delegavel no perfil ATENDENTE, com efeito real na Ana
        assertThat(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/ATENDENTE", corpoPerfil(revisaoPerfil("ATENDENTE"),
                Map.of(), mapa("resumo_ia.solicitar", false, "tags.aplicar", false))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(aplicarTag(token(EMAIL_ANA, SENHA_ATENDENTE), leadDaAna)).isEqualTo(HttpStatus.FORBIDDEN);

        // o que ele nao alcanca, sem gravar nada
        long revisao = revisaoPerfil("ATENDENTE");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/SUBGESTOR",
                corpoPerfil(revisaoPerfil("SUBGESTOR"), mapa("equipe", "GERENCIAR"), mapa("equipe.criar", true)))))
                .isEqualTo("ALVO_FORA_DA_ALCADA");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/GESTOR", corpoPerfil(0, Map.of(), Map.of()))))
                .isEqualTo("ALVO_FORA_DA_ALCADA");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/ATENDENTE", corpoPerfil(revisao,
                mapa("tags", "VER"), mapa("resumo_ia.solicitar", false, "tags.aplicar", false)))))
                .isEqualTo("NIVEL_NAO_DELEGAVEL");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/ATENDENTE", corpoPerfil(revisao,
                Map.of(), mapa("resumo_ia.solicitar", false, "tags.aplicar", false, "atendimentos.finalizar_lote", false)))))
                .isEqualTo("FORA_DO_CONJUNTO_DELEGAVEL");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/ATENDENTE", corpoPerfil(revisao,
                Map.of(), mapa("tags.aplicar", false))))).isEqualTo("ACIMA_DA_PROPRIA_PERMISSAO");
        ResponseEntity<String> copia = chamar(tokenSub, HttpMethod.POST, BASE + "/perfis/ATENDENTE/copia/previa",
                Map.of("origem", "SUBGESTOR"));
        assertThat(copia.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(copia.getBody()).contains("ORIGEM_INVALIDA");
        assertThat(revisaoPerfil("ATENDENTE")).isEqualTo(revisao);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissao_perfil_item WHERE papel = 'SUBGESTOR'", Integer.class))
                .isZero();

        // tela desatualizada: o gestor baixou o nivel de Tags depois da leitura. Comparado ao salvo, o
        // rascunho antigo "subiria" o nivel (403); o que o subgestor precisa ver e o conflito (409).
        salvarPerfil(tokenGestor, "ATENDENTE", mapa("tags", "VER"), mapa("resumo_ia.solicitar", false, "tags.aplicar", false));
        ResponseEntity<String> desatualizada = chamar(tokenSub, HttpMethod.PUT, BASE + "/perfis/ATENDENTE",
                corpoPerfil(revisao, Map.of(), mapa("resumo_ia.solicitar", false, "tags.aplicar", false)));
        assertThat(desatualizada.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(desatualizada).path("revisaoAtual").asLong()).isEqualTo(revisao + 1);
    }

    @Test
    @DisplayName("SUBGESTOR delegado para usuarios: cria so ATENDENTE (IA OFF), nunca SUBGESTOR; nunca altera papel")
    void delegacaoDeCadastro() {
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        String tokenSub = token(EMAIL_SUBGESTOR, SENHA_SUBGESTOR);
        assertThat(chamar(tokenSub, HttpMethod.POST, "/api/v1/usuarios", novoUsuario("ATENDENTE")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        chamar(tokenGestor, HttpMethod.PUT, BASE + "/usuarios/" + subgestor + "/excecoes",
                corpoExcecoes(revisaoUsuario(subgestor), mapa("equipe", "GERENCIAR"),
                        mapa("equipe.criar", true, "equipe.editar", true, "equipe.desativar", true)));

        ResponseEntity<String> criado = chamar(tokenSub, HttpMethod.POST, "/api/v1/usuarios", novoUsuario("ATENDENTE"));
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode novo = ler(criado);
        UUID novoId = UUID.fromString(novo.path("id").asText());
        criados.add(novoId);
        assertThat(novo.path("disponivelParaIa").asBoolean(true)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COALESCE((SELECT disponivel_para_ia FROM disponibilidade_atendente_ia "
                + "WHERE atendente_id = ?), FALSE)", Boolean.class, novoId)).isFalse();

        assertThat(codigo(chamar(tokenSub, HttpMethod.POST, "/api/v1/usuarios", novoUsuario("SUBGESTOR"))))
                .isEqualTo("ALVO_FORA_DA_ALCADA");
        assertThat(codigo(chamar(tokenSub, HttpMethod.PUT, "/api/v1/usuarios/" + novoId,
                Map.of("nome", "Promovido", "email", novo.path("email").asText(), "papel", "SUBGESTOR"))))
                .isEqualTo("ALVO_FORA_DA_ALCADA");
        assertThat(chamar(tokenSub, HttpMethod.PATCH, "/api/v1/usuarios/" + gestor + "/desativar", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenSub, HttpMethod.PATCH, "/api/v1/usuarios/" + subgestor + "/desativar", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // papel adulterado no contrato comum: ADMINISTRADOR/GESTOR nao existem em PapelGerenciavel
        assertThat(chamar(tokenGestor, HttpMethod.POST, "/api/v1/usuarios", novoUsuario("ADMINISTRADOR")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(chamar(tokenGestor, HttpMethod.PUT, "/api/v1/usuarios/" + novoId,
                Map.of("nome", "X", "email", novo.path("email").asText(), "papel", "GESTOR")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- sessao: rebaixamento e desativacao --------------------------------------------------------

    @Test
    @DisplayName("rebaixar descarta excecoes, grava historico e derruba o JWT antigo (401); refresh traz o papel novo")
    void excecaoAposRebaixamento() {
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        UUID sub = criarUsuario(tokenGestor, "SUBGESTOR");
        assertThat(chamar(tokenGestor, HttpMethod.PUT, BASE + "/usuarios/" + sub + "/excecoes",
                corpoExcecoes(0, mapa("equipe", "GERENCIAR"), mapa("equipe.excecoes_atendentes", true)))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        ApoioAutenticacao.Tokens sessao = ApoioAutenticacao.login(http, emailDe(sub), "senha-gestao-it");
        assertThat(chamar(sessao.accessToken(), HttpMethod.GET, BASE + "/usuarios", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(chamar(tokenGestor, HttpMethod.PUT, "/api/v1/usuarios/" + sub,
                Map.of("nome", "Rebaixado", "email", emailDe(sub), "papel", "ATENDENTE")).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissao_usuario_excecao WHERE usuario_id = ?", Integer.class, sub)).isZero();
        assertThat(jdbc.queryForObject("SELECT operacao FROM permissao_historico WHERE usuario_id = ? ORDER BY criado_em DESC LIMIT 1",
                String.class, sub)).isEqualTo("MUDANCA_DE_PAPEL");
        ResponseEntity<String> antigo = chamar(sessao.accessToken(), HttpMethod.GET, BASE + "/usuarios", null);
        assertThat(antigo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(antigo.getBody()).contains("sessao-desatualizada");
        // qualquer rota, nao so Gestao: o papel antigo nao vale mais em lugar nenhum
        assertThat(chamar(sessao.accessToken(), HttpMethod.GET, "/api/v1/tags", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> renovado = ApoioAutenticacao.refresh(http, sessao.refreshToken());
        assertThat(renovado.getStatusCode()).isEqualTo(HttpStatus.OK);
        String novoToken = (String) renovado.getBody().get("accessToken");
        assertThat(chamar(novoToken, HttpMethod.GET, BASE + "/usuarios", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(novoToken, HttpMethod.GET, "/api/v1/tags", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("desativar corta a sessao em curso: access token antigo 401 e refresh recusado")
    void desativacaoCortaSessao() {
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        UUID atendente = criarUsuario(tokenGestor, "ATENDENTE");
        ApoioAutenticacao.Tokens sessao = ApoioAutenticacao.login(http, emailDe(atendente), "senha-gestao-it");
        assertThat(chamar(sessao.accessToken(), HttpMethod.GET, "/api/v1/tags", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(chamar(tokenGestor, HttpMethod.PATCH, "/api/v1/usuarios/" + atendente + "/desativar", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(chamar(sessao.accessToken(), HttpMethod.GET, "/api/v1/tags", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ApoioAutenticacao.refresh(http, sessao.refreshToken()).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- copia -------------------------------------------------------------------------------------

    @Test
    @DisplayName("copia de GESTOR e recusada; copia de SUBGESTOR para ATENDENTE mostra o impedido e nao transmite teto")
    void copia() {
        String t = token(EMAIL_GESTOR, SENHA_GESTOR);
        ResponseEntity<String> deGestor = chamar(t, HttpMethod.POST, BASE + "/usuarios/" + ana + "/copia/previa",
                Map.of("origemUsuarioId", gestor.toString()));
        assertThat(deGestor.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(deGestor.getBody()).contains("ORIGEM_INVALIDA");
        assertThat(chamar(t, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoesCopia(revisaoUsuario(ana), gestor)).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode previa = ler(chamar(t, HttpMethod.POST, BASE + "/usuarios/" + ana + "/copia/previa",
                Map.of("origemUsuarioId", subgestor.toString())));
        List<String> impedidos = new ArrayList<>();
        previa.path("impedidos").forEach(i -> impedidos.add(i.path("chave").asText()));
        assertThat(impedidos).contains("tags.criar", "equipe.ver");
        assertThat(previa.path("acoes").has("tags.criar")).isFalse();
        // nada foi salvo pela previa
        assertThat(jdbc.queryForObject("SELECT count(*) FROM permissao_usuario_excecao WHERE usuario_id = ?", Integer.class, ana)).isZero();

        ResponseEntity<String> perfilDeGestor = chamar(t, HttpMethod.POST, BASE + "/perfis/ATENDENTE/copia/previa",
                Map.of("origem", "GESTOR"));
        assertThat(perfilDeGestor.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // --- ultimo responsavel administrativo ---------------------------------------------------------

    @Test
    @DisplayName("GESTOR/ADMINISTRADOR nao sao alvo da gestao comum; corrida para rebaixar os ultimos tem um perdedor")
    void ultimoResponsavelAdministrativo() throws Exception {
        String t = token(EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR);
        assertThat(chamar(t, HttpMethod.PATCH, "/api/v1/usuarios/" + gestor + "/desativar", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(t, HttpMethod.PUT, "/api/v1/usuarios/" + gestor,
                Map.of("nome", "X", "email", EMAIL_GESTOR, "papel", "ATENDENTE")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<UUID> responsaveis = jdbc.queryForList(
                "SELECT id FROM usuario WHERE ativo AND papel IN ('GESTOR','ADMINISTRADOR')", UUID.class);
        ExecutorService pool = Executors.newFixedThreadPool(responsaveis.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> tentativas = new ArrayList<>();
        for (UUID r : responsaveis) {
            tentativas.add(pool.submit(() -> {
                largada.await();
                try {
                    jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", r);
                    return true;
                } catch (org.springframework.dao.DataAccessException e) {
                    return false;
                }
            }));
        }
        largada.countDown();
        List<UUID> desativados = new ArrayList<>();
        for (int i = 0; i < tentativas.size(); i++) {
            if (tentativas.get(i).get()) desativados.add(responsaveis.get(i));
        }
        pool.shutdown();
        try {
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM usuario WHERE ativo AND papel IN ('GESTOR','ADMINISTRADOR')", Integer.class))
                    .isGreaterThanOrEqualTo(1);
            assertThat(desativados).hasSizeLessThan(responsaveis.size());
        } finally {
            for (UUID d : desativados) {
                jdbc.update("UPDATE usuario SET ativo = TRUE WHERE id = ?", d);
            }
        }
    }

    // --- auditoria ---------------------------------------------------------------------------------

    @Test
    @DisplayName("historico grava autor, alvo, antes/depois, revisao e operacao; audit_log recebe o resumo sem segredo")
    void auditoria() {
        String t = token(EMAIL_GESTOR, SENHA_GESTOR);
        long antes = revisaoUsuario(ana);
        chamar(t, HttpMethod.PUT, BASE + "/usuarios/" + ana + "/excecoes",
                corpoExcecoes(antes, Map.of(), Map.of("tags.aplicar", false)));
        Map<String, Object> linha = jdbc.queryForMap("""
                SELECT autor_id, operacao, revisao_anterior, revisao_nova, antes::text AS antes, depois::text AS depois
                  FROM permissao_historico WHERE usuario_id = ? ORDER BY criado_em DESC LIMIT 1""", ana);
        assertThat(linha.get("autor_id")).isEqualTo(gestor);
        assertThat(linha.get("operacao")).isEqualTo("SALVAR");
        assertThat(((Number) linha.get("revisao_nova")).longValue()).isEqualTo(antes + 1);
        assertThat((String) linha.get("depois")).contains("tags.aplicar");
        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE acao = 'SALVAR_EXCECOES_DE_PERMISSAO' AND entidade_id = ? AND ator_id = ?",
                Integer.class, ana, gestor)).isGreaterThanOrEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE dados_depois::text ILIKE '%senha%' AND acao LIKE '%PERMISSAO%'",
                Integer.class)).isZero();
    }

    // --- desempenho --------------------------------------------------------------------------------

    @Test
    @DisplayName("cache: quente nao recalcula; alteracao recalcula uma vez por usuario (medicao impressa no log)")
    void desempenhoDoCache() {
        String tokenAna = token(EMAIL_ANA, SENHA_ATENDENTE);
        String tokenGestor = token(EMAIL_GESTOR, SENHA_GESTOR);
        chamar(tokenAna, HttpMethod.GET, "/api/v1/tags", null);

        ResolvedorDePermissoesEfetivas.Metricas m0 = resolvedor.metricas();
        long inicio = System.nanoTime();
        int quentes = 50;
        for (int i = 0; i < quentes; i++) {
            assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/tags", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
        double msQuente = (System.nanoTime() - inicio) / 1_000_000.0 / quentes;
        ResolvedorDePermissoesEfetivas.Metricas m1 = resolvedor.metricas();
        assertThat(m1.recalculos() - m0.recalculos()).as("recalculos em requisicoes quentes").isZero();

        salvarPerfilAtendente(tokenGestor, Map.of("templates.criar", false));
        ResolvedorDePermissoesEfetivas.Metricas m2 = resolvedor.metricas();
        long frio = System.nanoTime();
        chamar(tokenAna, HttpMethod.GET, "/api/v1/tags", null);
        double msPosAlteracao = (System.nanoTime() - frio) / 1_000_000.0;
        ResolvedorDePermissoesEfetivas.Metricas m3 = resolvedor.metricas();
        assertThat(m3.recalculos() - m2.recalculos()).as("recalculo apos alteracao").isEqualTo(1);

        System.out.printf("[GESTAO-MEDICAO] quente=%.2fms/req (n=%d, recalculos=%d, leiturasRevisao=%d) "
                        + "posAlteracao=%.2fms (recalculos=%d)%n",
                msQuente, quentes, m1.recalculos() - m0.recalculos(), m1.leiturasDaRevisao() - m0.leiturasDaRevisao(),
                msPosAlteracao, m3.recalculos() - m2.recalculos());
    }

    // --- apoio -------------------------------------------------------------------------------------

    private void limparPermissoes() {
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM permissao_usuario_excecao");
    }

    private UUID id(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private String emailDe(UUID usuario) {
        return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
    }

    private UUID criarLead(String nome, UUID dono) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?, ?)",
                id, PREFIXO + nome + " " + id, dono, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        return id;
    }

    private Map<String, Object> novoUsuario(String papel) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        return Map.of("nome", PREFIXO + sufixo, "email", "gestao-it-" + sufixo + "@teste.local",
                "senha", "senha-gestao-it", "papel", papel);
    }

    private UUID criarUsuario(String tokenGestor, String papel) {
        ResponseEntity<String> r = chamar(tokenGestor, HttpMethod.POST, "/api/v1/usuarios", novoUsuario(papel));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID novo = UUID.fromString(ler(r).path("id").asText());
        criados.add(novo);
        // primeiro acesso exige troca de senha; o IT fixa a senha como ja trocada para usar a sessao
        jdbc.update("UPDATE usuario SET senha_alterada_em = now() WHERE id = ?", novo);
        return novo;
    }

    private HttpStatus aplicarTag(String token, UUID lead) {
        ResponseEntity<String> r = chamar(token, HttpMethod.PUT, "/api/v1/leads/" + lead + "/tags/" + tag, null);
        if (r.getStatusCode().is2xxSuccessful()) {
            jdbc.update("DELETE FROM lead_tag WHERE lead_id = ? AND tag_id = ?", lead, tag);
        }
        return (HttpStatus) r.getStatusCode();
    }

    private void salvarPerfilAtendente(String token, Map<String, Boolean> acoes) {
        salvarPerfil(token, "ATENDENTE", Map.of(), acoes);
    }

    private void salvarPerfil(String token, String papel, Map<String, String> niveis, Map<String, Boolean> acoes) {
        ResponseEntity<String> r = chamar(token, HttpMethod.PUT, BASE + "/perfis/" + papel,
                corpoPerfil(revisaoPerfil(papel), niveis, acoes));
        assertThat(r.getStatusCode()).as(r.getBody()).isEqualTo(HttpStatus.OK);
    }

    private long revisaoPerfil(String papel) {
        return jdbc.queryForObject("SELECT revisao FROM permissao_perfil WHERE papel = CAST(? AS papel_usuario)", Long.class, papel);
    }

    private long revisaoUsuario(UUID usuario) {
        return jdbc.query("SELECT revisao FROM permissao_usuario WHERE usuario_id = ?", (r, i) -> r.getLong(1), usuario)
                .stream().findFirst().orElse(0L);
    }

    private static Map<String, Object> corpoPerfil(long revisao, Map<String, String> niveis, Map<String, Boolean> acoes) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("revisaoEsperada", revisao);
        corpo.put("niveis", niveis);
        corpo.put("acoes", acoes);
        return corpo;
    }

    private static Map<String, Object> corpoExcecoes(long revisao, Map<String, String> niveis, Map<String, Boolean> acoes) {
        return corpoPerfil(revisao, niveis, acoes);
    }

    private static Map<String, Object> corpoExcecoesCopia(long revisao, UUID origem) {
        Map<String, Object> corpo = corpoPerfil(revisao, Map.of(), Map.of());
        corpo.put("copiadoDe", origem.toString());
        return corpo;
    }

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> mapa(Object... pares) {
        Map<String, V> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], (V) pares[i + 1]);
        }
        return m;
    }

    private int badge(JsonNode lista, UUID usuario) {
        for (JsonNode u : lista) {
            if (u.path("id").asText().equals(usuario.toString())) return u.path("excecoes").asInt();
        }
        throw new AssertionError("usuario ausente da lista: " + usuario);
    }

    private static JsonNode capacidade(JsonNode detalhe, String id) {
        for (JsonNode c : detalhe.path("capacidades")) {
            if (c.path("id").asText().equals(id)) return c;
        }
        throw new AssertionError("capacidade ausente: " + id);
    }

    private String codigo(ResponseEntity<String> resposta) {
        return ler(resposta).path("codigo").asText(resposta.getStatusCode().toString());
    }

    private String token(String email, String senha) {
        return ApoioAutenticacao.login(http, email, senha).accessToken();
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
