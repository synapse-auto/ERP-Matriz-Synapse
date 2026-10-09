package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * Contrato do card de informacoes do chatbot, de ponta a ponta: contrato do n8n, idempotencia,
 * recusas, flag por instancia, paginacao da leitura e o contrato publicado.
 *
 * <p>O negativo que mais importa: o card nao e mensagem. Nada vai para `mensagem` nem para a outbox,
 * e responsavel, participantes e resumo da ficha seguem exatamente como estavam.
 */
class InformacoesDoChatbotIT extends InformacoesDoChatbotITBase {

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
    @DisplayName("replay de operacao concluida devolve a resposta original mesmo com a flag desligada depois")
    void replayAposDesligarAFlag() {
        Atendimento atendimento = atendimentoComHumano("REPLAY-FLAG");
        ResponseEntity<String> original = postar(TOKEN, atendimento.id(), "chave-replay-flag", "texto original");
        assertThat(original.getStatusCode()).isEqualTo(HttpStatus.OK);

        definirFlag(false);
        ResponseEntity<String> replay = postar(TOKEN, atendimento.id(), "chave-replay-flag", "texto original");
        ResponseEntity<String> nova = postar(TOKEN, atendimento.id(), "chave-outra", "texto novo");

        assertThat(replay.getStatusCode()).as("configuracao atual nao invalida o que ja foi concluido").isEqualTo(HttpStatus.OK);
        assertThat(ler(replay)).isEqualTo(ler(original));
        assertThat(nova.getStatusCode()).as("operacao nova continua validada pela flag").isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(nova).path("motivo").asText()).isEqualTo("FUNCIONALIDADE_DESABILITADA");
        assertThat(cards(atendimento.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("replay de operacao concluida devolve a resposta original mesmo depois de o atendimento ser finalizado")
    void replayAposFinalizacao() {
        Atendimento atendimento = atendimentoComHumano("REPLAY-FIM");
        ResponseEntity<String> original = postar(TOKEN, atendimento.id(), "chave-replay-fim", "texto original");
        assertThat(original.getStatusCode()).isEqualTo(HttpStatus.OK);

        jdbc.update("UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = now() WHERE id = ?", atendimento.id());
        ResponseEntity<String> replay = postar(TOKEN, atendimento.id(), "chave-replay-fim", "texto original");
        ResponseEntity<String> nova = postar(TOKEN, atendimento.id(), "chave-outra-fim", "texto novo");

        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ler(replay)).isEqualTo(ler(original));
        assertThat(nova.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(nova).path("motivo").asText()).isEqualTo("ATENDIMENTO_FINALIZADO");
        assertThat(cards(atendimento.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("quatro requisicoes simultaneas com a mesma chave gravam um unico card e devolvem o mesmo id")
    void mesmaChaveEmParalelo() throws Exception {
        Atendimento atendimento = atendimentoComHumano("PARALELO");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            CountDownLatch largada = new CountDownLatch(1);
            List<Future<ResponseEntity<String>>> chamadas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                chamadas.add(pool.submit(() -> {
                    largada.await();
                    return postar(TOKEN, atendimento.id(), "chave-paralela", "mesmo texto");
                }));
            }
            largada.countDown();

            Set<String> ids = new HashSet<>();
            for (var chamada : chamadas) {
                ResponseEntity<String> resposta = chamada.get(30, TimeUnit.SECONDS);
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
        HttpHeaders chaveEmBranco = cabecalhos(TOKEN);
        chaveEmBranco.set("Idempotency-Key", " ");
        ResponseEntity<String> aoChaveEmBranco = http.exchange(
                url(atendimento.id()), HttpMethod.POST, new HttpEntity<>("{\"conteudo\":\"\"}", chaveEmBranco), String.class);

        assertThat(aoSemChave.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(aoSemConteudo.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(aoChaveEmBranco.getStatusCode())
                .as("chave em branco e 400 antes do 422 de conteudo").isEqualTo(HttpStatus.BAD_REQUEST);
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
        JsonNode lido = ler(lerCards(atendimento.id(), loginAna()));
        assertThat(lido.path("itens")).isEmpty();
        assertThat(lido.path("proximoCursor").isNull()).isTrue();
        assertThat(cards(atendimento.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("a flag nasce desligada em toda instancia: a linha da V101 entra com habilitado = false")
    void flagNasceDesligada() {
        jdbc.update("DELETE FROM feature_flag WHERE chave = ?", FLAG);
        // Reaplica o INSERT da V101 (idempotente): sem linha previa, a flag entra desligada.
        jdbc.update("INSERT INTO feature_flag (chave, habilitado, descricao) VALUES (?, FALSE, 'x') ON CONFLICT (chave) DO NOTHING", FLAG);

        assertThat(flagLigada()).isFalse();
    }

    @Test
    @DisplayName("script operacional liga a flag da instancia, e repetir e neutro")
    void scriptOperacionalLigaAFlag() throws IOException {
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

        assertThatThrownBy(this::executarScriptDeHabilitacao).hasMessageContaining("implante a versao com a V101");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_flag WHERE chave = ?", Long.class, FLAG))
                .as("nenhuma linha criada pelo script").isZero();
    }

    // --- leitura: paginacao e acesso --------------------------------------------------------------

    @Test
    @DisplayName("nenhum card antigo some: a leitura pagina por cursor, em ordem cronologica, sem repetir")
    void leituraPaginadaNaoPerdeCard() {
        Atendimento atendimento = atendimentoComHumano("PAGINAS");
        Instant inicio = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        inserirCardsDatados(atendimento.id(), inicio, 7);
        String bearer = loginAna();

        JsonNode primeira = ler(lerCards(atendimento.id(), bearer));
        assertThat(conteudos(primeira)).containsExactly("card 5", "card 6", "card 7");
        assertThat(primeira.path("proximoCursor").asText()).isNotBlank();

        JsonNode segunda = ler(lerCards(atendimento.id(), bearer, "?cursor=" + primeira.path("proximoCursor").asText()));
        assertThat(conteudos(segunda)).containsExactly("card 2", "card 3", "card 4");

        JsonNode terceira = ler(lerCards(atendimento.id(), bearer, "?cursor=" + segunda.path("proximoCursor").asText()));
        assertThat(conteudos(terceira)).containsExactly("card 1");
        assertThat(terceira.path("proximoCursor").isNull()).as("ultima pagina nao tem cursor").isTrue();
    }

    @Test
    @DisplayName("'desde' alinha os cards ao trecho de mensagens carregado, sem trazer o historico inteiro")
    void leituraComJanela() {
        Atendimento atendimento = atendimentoComHumano("JANELA");
        Instant inicio = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        inserirCardsDatados(atendimento.id(), inicio, 5);

        // card 3 esta exatamente em inicio+30s: o limite e inclusivo.
        JsonNode janela = ler(lerCards(atendimento.id(), loginAna(), "?desde=" + inicio.plusSeconds(30)));

        assertThat(conteudos(janela)).containsExactly("card 3", "card 4", "card 5");
        assertThat(janela.path("proximoCursor").isNull()).isTrue();
    }

    @Test
    @DisplayName("cursor ou 'desde' malformado: 400, sem consultar nem vazar nada")
    void leituraRecusaParametroMalformado() {
        Atendimento atendimento = atendimentoComHumano("MALFORMADO");
        String bearer = loginAna();

        assertThat(lerCards(atendimento.id(), bearer, "?cursor=%23%23%23").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(lerCards(atendimento.id(), bearer, "?desde=ontem").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("responsavel e gestor leem os cards em ordem cronologica; atendente sem acesso recebe 404")
    void leituraRespeitaAcesso() {
        Atendimento atendimento = atendimentoComHumano("LEITURA");
        postar(TOKEN, atendimento.id(), "chave-leitura-1", "primeiro");
        postar(TOKEN, atendimento.id(), "chave-leitura-2", "segundo");

        JsonNode doResponsavel = ler(lerCards(atendimento.id(), loginAna()));
        JsonNode doGestor = ler(lerCards(atendimento.id(), bearerDe(EMAIL_GESTOR, SENHA_GESTOR)));
        ResponseEntity<String> doColega = lerCards(atendimento.id(), bearerDe(EMAIL_BRUNO, SENHA_ATENDENTE));
        ResponseEntity<String> anonimo = http.getForEntity(urlLeitura(atendimento.id()), String.class);

        assertThat(conteudos(doResponsavel)).containsExactly("primeiro", "segundo");
        assertThat(doResponsavel.path("itens").get(0).path("origem").asText()).isEqualTo("AUTOMACAO");
        assertThat(doGestor.path("itens")).hasSize(2);
        assertThat(doColega.getStatusCode()).as("quem nao alcanca o atendimento nao le o card").isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(doColega.getBody()).doesNotContain("primeiro").doesNotContain("segundo");
        assertThat(anonimo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- contrato publicado -------------------------------------------------------------------------

    @Test
    @DisplayName("o contrato publicado no OpenAPI e o que os testes exercitam: parametros, seguranca e codigos de resposta")
    void contratoPublicadoCorrespondeAoComportamento() {
        JsonNode raiz = ler(http.getForEntity("/v3/api-docs", String.class));

        JsonNode escrita = raiz.path("paths").path("/internal/v1/atendimentos/{id}/informacoes-do-chatbot").path("post");
        assertThat(escrita.isMissingNode()).as("rota interna publicada").isFalse();
        assertThat(codigos(escrita)).containsExactlyInAnyOrder("200", "400", "401", "403", "404", "409", "422");
        List<String> parametros = new ArrayList<>();
        escrita.path("parameters").forEach(parametro -> parametros.add(
                parametro.path("in").asText() + ":" + parametro.path("name").asText() + ":" + parametro.path("required").asBoolean()));
        assertThat(parametros).containsExactlyInAnyOrder("path:id:true", "header:Idempotency-Key:true");
        assertThat(escrita.path("security").toString()).contains("synapseToken");
        assertThat(raiz.path("components").path("schemas").path("InformacoesRequisicao").path("required").toString())
                .contains("conteudo");

        JsonNode leitura = raiz.path("paths").path("/api/v1/atendimentos/{atendimentoId}/informacoes-do-chatbot").path("get");
        assertThat(leitura.isMissingNode()).as("rota de leitura publicada").isFalse();
        assertThat(codigos(leitura)).contains("200", "400", "404");
        List<String> consulta = new ArrayList<>();
        leitura.path("parameters").forEach(parametro -> consulta.add(parametro.path("name").asText()));
        assertThat(consulta).contains("desde", "cursor");
    }

    // --- apoio --------------------------------------------------------------------------------------

    private void inserirCardsDatados(UUID atendimentoId, Instant inicio, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            jdbc.update(
                    "INSERT INTO atendimento_informacao_chatbot (id, atendimento_id, chave_idempotencia, conteudo, registrado_em)"
                            + " VALUES (?,?,?,?,?)",
                    UUID.randomUUID(), atendimentoId, "chave-datada-" + atendimentoId + "-" + i, "card " + i,
                    Timestamp.from(inicio.plusSeconds(i * 10L)));
        }
    }

    private static List<String> conteudos(JsonNode pagina) {
        List<String> conteudos = new ArrayList<>();
        pagina.path("itens").forEach(item -> conteudos.add(item.path("conteudo").asText()));
        return conteudos;
    }

    private static List<String> codigos(JsonNode operacao) {
        List<String> codigos = new ArrayList<>();
        operacao.path("responses").fieldNames().forEachRemaining(codigos::add);
        return codigos;
    }

    private void executarScriptDeHabilitacao() throws IOException {
        Path atual = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (atual != null) {
            Path script = atual.resolve("docker/provisionamento/habilitar-informacoes-do-chatbot.sql");
            if (Files.isRegularFile(script)) {
                jdbc.execute(Files.readString(script));
                return;
            }
            atual = atual.getParent();
        }
        throw new IOException("script de habilitacao das informacoes do chatbot nao encontrado");
    }
}
