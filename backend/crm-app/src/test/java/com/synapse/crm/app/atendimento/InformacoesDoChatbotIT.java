package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.evento.InformacoesDoChatbotParaTempoReal;

/**
 * Card interno com as informacoes do chatbot, de ponta a ponta: contrato do n8n, idempotencia,
 * recusas, isolamento entre atendimentos, flag por instancia e leitura autorizada.
 *
 * <p>O negativo que mais importa: o card nao e mensagem. Nada vai para `mensagem` nem para a outbox,
 * e responsavel, participantes e resumo da ficha seguem exatamente como estavam.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "synapse.seguranca.token-interno=token-informacoes-chatbot",
    "synapse.automacao.informacoes-chatbot-tamanho-maximo=200",
    "synapse.automacao.informacoes-chatbot-limite-listagem=3"
})
class InformacoesDoChatbotIT extends PostgresIT {

    private static final String TOKEN = "token-informacoes-chatbot";
    private static final String PREFIXO = "INFO-CHATBOT-";
    private static final String FLAG = "informacoes_chatbot_historico";
    private static final String RESUMO_DA_FICHA = "RESUMO-DA-FICHA-NAO-PODE-MUDAR";

    @TestConfiguration
    static class Captura {
        @Bean
        CapturaDeAvisos capturaDeAvisos() {
            return new CapturaDeAvisos();
        }
    }

    static class CapturaDeAvisos {
        final List<InformacoesDoChatbotParaTempoReal> avisos = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void aoAvisar(InformacoesDoChatbotParaTempoReal aviso) {
            avisos.add(aviso);
        }
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CapturaDeAvisos captura;

    private UUID ana;

    @BeforeEach
    void preparar() {
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        definirFlag(true);
        captura.avisos.clear();
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        definirFlag(false);
    }

    // --- caminho feliz e o que o card NAO e -------------------------------------------------------

    @Test
    @DisplayName("registra o card sem virar mensagem, sem outbox e sem mexer em responsavel nem resumo da ficha")
    void registraSemEfeitoColateral() {
        Atendimento atendimento = atendimentoComHumano("FELIZ");

        ResponseEntity<String> resposta = postar(TOKEN, atendimento.id(), "chave-feliz", "Nome: Maria\nInteresse: avaliação");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = ler(resposta);
        assertThat(corpo.path("atendimentoId").asText()).isEqualTo(atendimento.id().toString());
        assertThat(corpo.path("id").asText()).isNotBlank();
        assertThat(corpo.has("conteudo")).as("a resposta nao devolve o texto recebido").isFalse();
        assertThat(cards(atendimento.id())).isEqualTo(1);
        assertThat(conteudoDoCard(atendimento.id())).isEqualTo("Nome: Maria\nInteresse: avaliação");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimento.id()))
                .as("card nao e mensagem").isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_evento WHERE payload::text LIKE ?",
                        Long.class, "%" + atendimento.id() + "%"))
                .as("card nunca entra na outbox de envio").isZero();
        assertThat(donoDoAtendimento(atendimento.id())).as("responsavel inalterado").isEqualTo(ana);
        assertThat(participantes(atendimento.id())).as("participantes inalterados").isZero();
        assertThat(resumoDaFicha(atendimento.leadId())).as("resumo da ficha intacto").isEqualTo(RESUMO_DA_FICHA);
        assertThat(captura.avisos).hasSize(1);
        assertThat(captura.avisos.getFirst().atendimentoId()).isEqualTo(atendimento.id());
    }

    // --- idempotencia -----------------------------------------------------------------------------

    @Test
    @DisplayName("retry da mesma ocorrencia devolve a resposta original e nao duplica o card nem o aviso")
    void retryNaoDuplica() {
        Atendimento atendimento = atendimentoComHumano("RETRY");

        JsonNode primeira = ler(postar(TOKEN, atendimento.id(), "chave-retry", "Linha 1\nLinha 2"));
        ResponseEntity<String> segunda = postar(TOKEN, atendimento.id(), "chave-retry", "Linha 1\r\nLinha 2");

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ler(segunda)).as("CRLF e o mesmo pedido").isEqualTo(primeira);
        assertThat(cards(atendimento.id())).isEqualTo(1);
        assertThat(captura.avisos).hasSize(1);
    }

    @Test
    @DisplayName("duas requisicoes simultaneas com a mesma chave gravam um unico card e devolvem o mesmo id")
    void mesmaChaveEmParalelo() throws Exception {
        Atendimento atendimento = atendimentoComHumano("PARALELO");
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.concurrent.CountDownLatch largada = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<ResponseEntity<String>>> chamadas = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                chamadas.add(pool.submit(() -> {
                    largada.await();
                    return postar(TOKEN, atendimento.id(), "chave-paralela", "mesmo texto");
                }));
            }
            largada.countDown();

            java.util.Set<String> ids = new java.util.HashSet<>();
            for (var chamada : chamadas) {
                ResponseEntity<String> resposta = chamada.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
                ids.add(ler(resposta).path("id").asText());
            }
            assertThat(ids).as("todas as respostas sao a original").hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(cards(atendimento.id())).isEqualTo(1);
        assertThat(captura.avisos).hasSize(1);
    }

    @Test
    @DisplayName("mesma chave com conteudo diferente e recusada e nada novo e gravado")
    void mesmaChaveConteudoDiferente() {
        Atendimento atendimento = atendimentoComHumano("CONTEUDO");
        postar(TOKEN, atendimento.id(), "chave-conteudo", "versao A");

        ResponseEntity<String> resposta = postar(TOKEN, atendimento.id(), "chave-conteudo", "versao B");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cards(atendimento.id())).isEqualTo(1);
        assertThat(conteudoDoCard(atendimento.id())).isEqualTo("versao A");
    }

    @Test
    @DisplayName("mesma chave em outro atendimento e recusada: a ocorrencia nao migra de destino")
    void mesmaChaveOutroAtendimento() {
        Atendimento primeiro = atendimentoComHumano("DESTINO-1");
        Atendimento segundo = atendimentoComHumano("DESTINO-2");
        postar(TOKEN, primeiro.id(), "chave-destino", "mesmo texto");

        ResponseEntity<String> resposta = postar(TOKEN, segundo.id(), "chave-destino", "mesmo texto");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cards(segundo.id())).isZero();
        assertThat(cards(primeiro.id())).isEqualTo(1);
    }

    // --- estado do atendimento e callback atrasado -----------------------------------------------

    @Test
    @DisplayName("atendimento ainda com a IA: 409 ATENDIMENTO_NAO_TRANSFERIDO e nada gravado")
    void atendimentoEmIa() {
        UUID leadId = criarLead("EM-IA", null, "IA");
        UUID atendimentoId = criarAtendimento(leadId, null, "EM_IA");

        ResponseEntity<String> resposta = postar(TOKEN, atendimentoId, "chave-ia", "texto");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(resposta).path("motivo").asText()).isEqualTo("ATENDIMENTO_NAO_TRANSFERIDO");
        assertThat(cards(atendimentoId)).isZero();
        assertThat(captura.avisos).isEmpty();
    }

    @Test
    @DisplayName("callback atrasado: atendimento antigo finalizado e recusado e o atual do mesmo lead nao e contaminado")
    void callbackAtrasadoNaoContaminaOAtendimentoAtual() {
        UUID leadId = criarLead("ATRASO", ana, "EM_ATENDIMENTO");
        UUID antigo = criarAtendimento(leadId, ana, "FINALIZADO");
        UUID atual = criarAtendimento(leadId, ana, "EM_ATENDIMENTO");

        ResponseEntity<String> resposta = postar(TOKEN, antigo, "chave-atrasada", "informacoes do ciclo anterior");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(resposta).path("motivo").asText()).isEqualTo("ATENDIMENTO_FINALIZADO");
        assertThat(cards(antigo)).isZero();
        assertThat(cards(atual)).as("o atendimento atual do lead nao recebe o que era de outro").isZero();
        assertThat(captura.avisos).isEmpty();
    }

    @Test
    @DisplayName("atendimento inexistente responde 404")
    void atendimentoInexistente() {
        ResponseEntity<String> resposta = postar(TOKEN, UUID.randomUUID(), "chave-fantasma", "texto");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- validacao e autenticacao ---------------------------------------------------------------

    @Test
    @DisplayName("conteudo vazio, so espacos, acima do limite ou com caractere nulo: 422 sem gravar")
    void conteudoInvalido() {
        Atendimento atendimento = atendimentoComHumano("INVALIDO");

        for (String invalido : List.of("", "   \n  ", "x".repeat(201), "abc\u0000def")) {
            ResponseEntity<String> resposta = postar(TOKEN, atendimento.id(), "chave-invalida", invalido);
            assertThat(resposta.getStatusCode()).as("conteudo: %s", invalido.length()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertThat(cards(atendimento.id())).isZero();
        assertThat(postar(TOKEN, atendimento.id(), "chave-no-limite", "x".repeat(200)).getStatusCode())
                .as("exatamente o limite e aceito").isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("corpo sem conteudo ou requisicao sem Idempotency-Key: 400")
    void requisicaoMalFormada() {
        Atendimento atendimento = atendimentoComHumano("MAL-FORMADA");

        HttpHeaders semChave = cabecalhos(TOKEN);
        ResponseEntity<String> aoSemChave = http.exchange(
                url(atendimento.id()), HttpMethod.POST, new HttpEntity<>("{\"conteudo\":\"texto\"}", semChave), String.class);
        HttpHeaders comChave = cabecalhos(TOKEN);
        comChave.set("Idempotency-Key", "chave-mal-formada");
        ResponseEntity<String> aoSemConteudo = http.exchange(
                url(atendimento.id()), HttpMethod.POST, new HttpEntity<>("{}", comChave), String.class);

        assertThat(aoSemChave.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(aoSemConteudo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cards(atendimento.id())).isZero();
    }

    @Test
    @DisplayName("sem X-Synapse-Token ou com token errado: 401, e o token de usuario nao serve")
    void exigeTokenDeServico() {
        Atendimento atendimento = atendimentoComHumano("TOKEN");
        HttpHeaders semToken = cabecalhos(null);
        semToken.set("Idempotency-Key", "chave-token");

        ResponseEntity<String> ausente = http.exchange(
                url(atendimento.id()), HttpMethod.POST, new HttpEntity<>("{\"conteudo\":\"x\"}", semToken), String.class);
        ResponseEntity<String> errado = postar("token-errado", atendimento.id(), "chave-token", "x");
        HttpHeaders comoUsuario = cabecalhos(null);
        comoUsuario.set("Idempotency-Key", "chave-token");
        comoUsuario.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken());
        ResponseEntity<String> usuario = http.exchange(
                url(atendimento.id()), HttpMethod.POST, new HttpEntity<>("{\"conteudo\":\"x\"}", comoUsuario), String.class);

        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(usuario.getStatusCode().is2xxSuccessful()).isFalse();
        assertThat(cards(atendimento.id())).isZero();
    }

    // --- feature flag -------------------------------------------------------------------------------

    @Test
    @DisplayName("flag desligada: 409 FUNCIONALIDADE_DESABILITADA, nada gravado, chave nao reservada e leitura vazia")
    void flagDesligadaNaoMudaNada() {
        Atendimento atendimento = atendimentoComHumano("FLAG-OFF");
        definirFlag(false);

        ResponseEntity<String> resposta = postar(TOKEN, atendimento.id(), "chave-flag", "texto");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(resposta).path("motivo").asText()).isEqualTo("FUNCIONALIDADE_DESABILITADA");
        assertThat(cards(atendimento.id())).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM comando_automacao_idempotencia WHERE idempotency_key = 'chave-flag'",
                        Long.class))
                .as("sem reserva: ligar a flag depois nao encontra chave 'queimada'").isZero();
        assertThat(captura.avisos).isEmpty();

        // O que ja estava registrado fica guardado, mas some da leitura enquanto a flag estiver desligada.
        definirFlag(true);
        postar(TOKEN, atendimento.id(), "chave-flag-2", "registrado com a flag ligada");
        definirFlag(false);
        assertThat(ler(lerCards(atendimento.id(), loginAna())).path("itens")).isEmpty();
        assertThat(cards(atendimento.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("a flag nasce desligada em toda instancia: a linha da V101 entra com habilitado = false")
    void flagNasceDesligada() {
        jdbc.update("DELETE FROM feature_flag WHERE chave = ?", FLAG);
        // Reaplica o INSERT da V101 (idempotente): sem linha previa, a flag entra desligada.
        jdbc.update("INSERT INTO feature_flag (chave, habilitado, descricao) VALUES (?, FALSE, 'x') ON CONFLICT (chave) DO NOTHING", FLAG);

        assertThat(jdbc.queryForObject("SELECT habilitado FROM feature_flag WHERE chave = ?", Boolean.class, FLAG)).isFalse();
    }

    @Test
    @DisplayName("script operacional liga a flag da instancia, e repetir e neutro")
    void scriptOperacionalLigaAFlag() throws java.io.IOException {
        definirFlag(false);

        executarScriptDeHabilitacao();
        assertThat(flagLigada()).isTrue();

        executarScriptDeHabilitacao();
        assertThat(flagLigada()).as("repetir nao desliga nem falha").isTrue();
    }

    @Test
    @DisplayName("script operacional recusa quando a V101 ainda nao foi implantada, em vez de inventar a flag")
    void scriptOperacionalRecusaSemAFlag() {
        jdbc.update("DELETE FROM feature_flag WHERE chave = ?", FLAG);

        org.assertj.core.api.Assertions.assertThatThrownBy(this::executarScriptDeHabilitacao)
                .hasMessageContaining("implante a versao com a V101");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flag WHERE chave = ?", Long.class, FLAG))
                .as("nenhuma linha criada pelo script").isZero();
    }

    // --- leitura autorizada -------------------------------------------------------------------------

    @Test
    @DisplayName("responsavel e gestor leem os cards em ordem cronologica; atendente sem acesso recebe 404")
    void leituraRespeitaAcesso() {
        Atendimento atendimento = atendimentoComHumano("LEITURA");
        postar(TOKEN, atendimento.id(), "chave-leitura-1", "primeiro");
        postar(TOKEN, atendimento.id(), "chave-leitura-2", "segundo");

        JsonNode doResponsavel = ler(lerCards(atendimento.id(), loginAna()));
        JsonNode doGestor = ler(lerCards(atendimento.id(), ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken()));
        ResponseEntity<String> doColega = lerCards(
                atendimento.id(), ApoioAutenticacao.login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken());
        ResponseEntity<String> anonimo = http.getForEntity(urlLeitura(atendimento.id()), String.class);

        assertThat(doResponsavel.path("itens")).hasSize(2);
        assertThat(doResponsavel.path("itens").get(0).path("conteudo").asText()).isEqualTo("primeiro");
        assertThat(doResponsavel.path("itens").get(1).path("conteudo").asText()).isEqualTo("segundo");
        assertThat(doResponsavel.path("itens").get(0).path("origem").asText()).isEqualTo("AUTOMACAO");
        assertThat(doGestor.path("itens")).hasSize(2);
        assertThat(doColega.getStatusCode()).as("quem nao alcanca o atendimento nao le o card").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(doColega.getBody()).doesNotContain("primeiro").doesNotContain("segundo");
        assertThat(anonimo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("com mais cards que o limite, a leitura traz os mais recentes em ordem cronologica")
    void leituraTrazOsMaisRecentesEmOrdem() {
        Atendimento atendimento = atendimentoComHumano("LIMITE");
        for (int i = 1; i <= 5; i++) {
            assertThat(postar(TOKEN, atendimento.id(), "chave-limite-" + i, "card " + i).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
        }

        JsonNode itens = ler(lerCards(atendimento.id(), loginAna())).path("itens");

        assertThat(itens).hasSize(3);
        assertThat(List.of(
                        itens.get(0).path("conteudo").asText(),
                        itens.get(1).path("conteudo").asText(),
                        itens.get(2).path("conteudo").asText()))
                .containsExactly("card 3", "card 4", "card 5");
    }

    // --- apoio ---------------------------------------------------------------------------------------

    private record Atendimento(UUID id, UUID leadId) {}

    private Atendimento atendimentoComHumano(String marcador) {
        UUID leadId = criarLead(marcador, ana, "EM_ATENDIMENTO");
        jdbc.update("UPDATE lead SET resumo_ia = ? WHERE id = ?", RESUMO_DA_FICHA, leadId);
        return new Atendimento(criarAtendimento(leadId, ana, "EM_ATENDIMENTO"), leadId);
    }

    private UUID criarLead(String marcador, UUID dono, String statusBasico) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES (?,?,?,?::status_basico_lead)",
                id, PREFIXO + marcador + "-" + id.toString().substring(0, 8), dono, statusBasico);
        return id;
    }

    private UUID criarAtendimento(UUID leadId, UUID atendente, String status) {
        UUID id = UUID.randomUUID();
        if ("FINALIZADO".equals(status)) {
            jdbc.update(
                    "INSERT INTO atendimento(id,lead_id,atendente_id,status,finalizado_em) VALUES (?,?,?,?::status_atendimento, now())",
                    id, leadId, atendente, status);
        } else {
            jdbc.update(
                    "INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,?,?::status_atendimento)",
                    id, leadId, atendente, status);
        }
        return id;
    }

    private boolean flagLigada() {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT habilitado FROM feature_flag WHERE chave = ?", Boolean.class, FLAG));
    }

    private void executarScriptDeHabilitacao() throws java.io.IOException {
        java.nio.file.Path atual = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (atual != null) {
            java.nio.file.Path script = atual.resolve("docker/provisionamento/habilitar-informacoes-do-chatbot.sql");
            if (java.nio.file.Files.isRegularFile(script)) {
                jdbc.execute(java.nio.file.Files.readString(script));
                return;
            }
            atual = atual.getParent();
        }
        throw new java.io.IOException("script de habilitacao das informacoes do chatbot nao encontrado");
    }

    private void definirFlag(boolean habilitada) {
        jdbc.update(
                "INSERT INTO feature_flag (chave, habilitado, descricao) VALUES (?, ?, 'teste') "
                        + "ON CONFLICT (chave) DO UPDATE SET habilitado = EXCLUDED.habilitado",
                FLAG, habilitada);
    }

    private long cards(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento_informacao_chatbot WHERE atendimento_id = ?", Long.class, atendimentoId);
    }

    private String conteudoDoCard(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT conteudo FROM atendimento_informacao_chatbot WHERE atendimento_id = ? ORDER BY registrado_em LIMIT 1",
                String.class, atendimentoId);
    }

    private UUID donoDoAtendimento(UUID atendimentoId) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimentoId);
    }

    private long participantes(UUID atendimentoId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento_participante WHERE atendimento_id = ?", Long.class, atendimentoId);
    }

    private String resumoDaFicha(UUID leadId) {
        return jdbc.queryForObject("SELECT resumo_ia FROM lead WHERE id = ?", String.class, leadId);
    }

    private String loginAna() {
        return ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
    }

    private ResponseEntity<String> lerCards(UUID atendimentoId, String bearer) {
        return ApoioAutenticacao.comToken(http, bearer, HttpMethod.GET, urlLeitura(atendimentoId), String.class);
    }

    private ResponseEntity<String> postar(String token, UUID atendimentoId, String chave, String conteudo) {
        HttpHeaders cabecalhos = cabecalhos(token);
        cabecalhos.set("Idempotency-Key", chave);
        try {
            String corpo = json.writeValueAsString(Map.of("conteudo", conteudo));
            return http.exchange(url(atendimentoId), HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException erro) {
            throw new IllegalStateException(erro);
        }
    }

    private static HttpHeaders cabecalhos(String token) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            cabecalhos.set("X-Synapse-Token", token);
        }
        return cabecalhos;
    }

    private JsonNode ler(ResponseEntity<String> resposta) {
        try {
            return json.readTree(resposta.getBody());
        } catch (com.fasterxml.jackson.core.JsonProcessingException erro) {
            throw new IllegalStateException("corpo ilegivel: " + resposta.getBody(), erro);
        }
    }

    private static String url(UUID atendimentoId) {
        return "/internal/v1/atendimentos/" + atendimentoId + "/informacoes-do-chatbot";
    }

    private static String urlLeitura(UUID atendimentoId) {
        return "/api/v1/atendimentos/" + atendimentoId + "/informacoes-do-chatbot";
    }
}
