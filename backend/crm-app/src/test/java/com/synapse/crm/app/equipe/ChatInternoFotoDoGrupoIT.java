package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.avatar.ArmazenamentoDeFotoDeGrupoFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.app.seguranca.ApoioAutenticacao.Tokens;
import com.synapse.crm.equipe.application.chat.ChatInternoRepositorio;

/**
 * Foto do grupo do chat interno pelo endpoint real: autorizacao no backend, validacao do arquivo,
 * storage com compensacao, versao na URL e atualizacao dos demais participantes por STOMP. O storage
 * e um fake em memoria; o Postgres, o Redis, o WebSocket e a RLS sao reais.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "synapse.canal.outbox.intervalo-ms=3600000",
    "synapse.tempo-real.outbox.intervalo-ms=3600000"
})
class ChatInternoFotoDoGrupoIT extends PostgresIT {

    private static final String PREFIXO = "FotoGrupo ";
    private static final Duration ESPERA = Duration.ofSeconds(5);

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired ArmazenamentoDeFotoDeGrupoFake armazenamento;
    @Autowired SimpUserRegistry usuariosStomp;
    @MockitoSpyBean ChatInternoRepositorio repositorio;

    private Tokens ana;
    private Tokens bruno;
    private Tokens gestor;
    private Tokens admin;
    private UUID idAna;
    private UUID idBruno;
    private UUID idGestor;
    private String limiteOriginalDeImagem;
    private int porta;
    private WebSocketStompClient stomp;
    private final List<StompSession> sessoes = new ArrayList<>();

    @Value("${local.server.port}")
    void definirPorta(int porta) {
        this.porta = porta;
    }

    @BeforeEach
    void preparar() {
        armazenamento.limpar();
        ana = ApoioAutenticacao.login(rest, EMAIL_ANA, SENHA_ATENDENTE);
        bruno = ApoioAutenticacao.login(rest, EMAIL_BRUNO, SENHA_ATENDENTE);
        gestor = ApoioAutenticacao.login(rest, EMAIL_GESTOR, SENHA_GESTOR);
        admin = ApoioAutenticacao.login(rest, EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR);
        idAna = idDo(EMAIL_ANA);
        idBruno = idDo(EMAIL_BRUNO);
        idGestor = idDo(EMAIL_GESTOR);
        limiteOriginalDeImagem = db.queryForObject(
                "SELECT valor FROM configuracao_automacao WHERE chave = 'anexo.tamanho_maximo_imagem_mb'", String.class);
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void limpar() {
        sessoes.forEach(s -> {
            if (s.isConnected()) s.disconnect();
        });
        stomp.stop();
        await().atMost(ESPERA).until(() -> usuariosStomp.getUserCount() == 0);
        db.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = 'anexo.tamanho_maximo_imagem_mb'",
                limiteOriginalDeImagem);
        String grupos = "SELECT id FROM chat_interno_conversa WHERE nome LIKE '" + PREFIXO + "%'";
        db.update("DELETE FROM audit_log WHERE entidade_tipo = 'CHAT_INTERNO_CONVERSA' AND entidade_id IN (" + grupos + ")");
        db.update("DELETE FROM chat_interno_mensagem WHERE conversa_id IN (" + grupos + ")");
        db.update("DELETE FROM chat_interno_participante WHERE conversa_id IN (" + grupos + ")");
        db.update("DELETE FROM chat_interno_conversa WHERE nome LIKE ?", PREFIXO + "%");
        armazenamento.limpar();
    }

    // ------------------------------------------------------------------ exibicao e troca

    @Nested
    @DisplayName("exibicao")
    class Exibicao {

        @Test
        @DisplayName("grupo sem foto: fotoUrl nulo, criador pode alterar, outro participante nao")
        void semFoto_fallback() throws Exception {
            String grupo = criarGrupo("sem foto");

            JsonNode daAna = conversa(ana, grupo);
            JsonNode doBruno = conversa(bruno, grupo);

            assertThat(daAna.path("fotoUrl").isNull()).isTrue();
            assertThat(daAna.path("podeAlterarFoto").asBoolean()).isTrue();
            assertThat(doBruno.path("fotoUrl").isNull()).isTrue();
            assertThat(doBruno.path("podeAlterarFoto").asBoolean()).isFalse();
            assertThat(enviarFoto(bruno, grupo, "grupo.png", "image/png", png(200, 200)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(baixarFoto(ana, grupo).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("conversa direta nunca e alteravel nem aceita foto; a foto do outro usuario segue como antes")
        void conversaDireta_intacta() throws Exception {
            ResponseEntity<String> direta = chamar(ana, HttpMethod.POST, "/api/v1/chat-interno/conversas/direta",
                    "{\"usuarioId\":\"" + idBruno + "\"}");
            String diretaId = json.readTree(direta.getBody()).path("id").asText();

            ResponseEntity<String> tentativa = enviarFoto(ana, diretaId, "grupo.png", "image/png", png(200, 200));

            assertThat(tentativa.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(conversa(ana, diretaId).path("podeAlterarFoto").asBoolean()).isFalse();
            assertThat(armazenamento.quantidade()).isZero();
        }

        @Test
        @DisplayName("a inbox unificada tambem mostra a foto do grupo")
        void inboxUnificada() throws Exception {
            String grupo = criarGrupo("na inbox");
            assertThat(enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);

            ResponseEntity<String> inbox = chamar(gestor, HttpMethod.GET,
                    "/api/v1/atendimentos/inbox?visao=TODOS&limite=100", null);

            assertThat(inbox.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode item = encontrar(json.readTree(inbox.getBody()).path("itens"), grupo);
            assertThat(item.path("avatarUrl").asText()).isEqualTo(conversa(gestor, grupo).path("fotoUrl").asText());
            assertThat(item.path("avatarUrl").asText()).contains("/conversas/" + grupo + "/foto?v=");
        }

        private JsonNode encontrar(JsonNode itens, String grupo) {
            for (JsonNode item : itens) {
                if (item.toString().contains(grupo)) {
                    return item;
                }
            }
            throw new AssertionError("grupo " + grupo + " nao esta na inbox: " + itens);
        }
    }

    @Nested
    @DisplayName("troca e remocao pelo criador")
    class Troca {

        @Test
        @DisplayName("JPEG: grava PNG quadrado reprocessado, versiona a URL e todos os participantes a enxergam")
        void jpegValido() throws Exception {
            String grupo = criarGrupo("jpeg");
            byte[] original = jpeg(320, 180);

            ResponseEntity<String> resposta = enviarFoto(ana, grupo, "equipe.JPG", "image/jpeg", original);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getHeaders().getContentType().toString()).contains("application/json");
            String url = json.readTree(resposta.getBody()).path("fotoUrl").asText();
            assertThat(url).matches("/api/v1/chat-interno/conversas/" + grupo + "/foto\\?v=\\d+");

            var armazenada = armazenamento.unicoArquivo();
            assertThat(armazenada.mimetype()).isEqualTo("image/png");
            assertThat(armazenada.conteudo()).isNotEqualTo(original);
            BufferedImage pronta = ImageIO.read(new ByteArrayInputStream(armazenada.conteudo()));
            assertThat(pronta.getWidth()).isEqualTo(256);
            assertThat(pronta.getHeight()).isEqualTo(256);

            Map<String, Object> linha = db.queryForMap(
                    "SELECT foto_referencia, foto_atualizada_em FROM chat_interno_conversa WHERE id = ?::uuid", grupo);
            assertThat(linha.get("foto_referencia").toString()).startsWith("grupo/").endsWith(".png");
            assertThat(linha.get("foto_atualizada_em")).isNotNull();

            for (Tokens participante : List.of(ana, bruno, gestor)) {
                assertThat(conversa(participante, grupo).path("fotoUrl").asText()).isEqualTo(url);
                ResponseEntity<byte[]> imagem = baixarFoto(participante, grupo);
                assertThat(imagem.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(imagem.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
                assertThat(imagem.getHeaders().getCacheControl()).contains("private").contains("no-cache");
                assertThat(imagem.getBody()).isEqualTo(armazenada.conteudo());
            }
            assertThat(conversa(ana, grupo).path("podeAlterarFoto").asBoolean()).isTrue();
            assertThat(conversa(bruno, grupo).path("podeAlterarFoto").asBoolean()).isFalse();
            assertThat(mensagensDeSistema(bruno, grupo)).anyMatch(c -> c.contains("FOTO_ALTERADA"));
        }

        @Test
        @DisplayName("troca e remocao ficam na auditoria com o ator; a tentativa recusada nao deixa registro")
        void auditoria() {
            String grupo = criarGrupo("auditoria");

            enviarFoto(bruno, grupo, "invasor.png", "image/png", png(200, 200));
            enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200));
            chamar(ana, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/foto", null);

            await().atMost(ESPERA).untilAsserted(() -> {
                List<Map<String, Object>> linhas = db.queryForList(
                        "SELECT acao, ator_id FROM audit_log WHERE entidade_tipo = 'CHAT_INTERNO_CONVERSA' AND entidade_id = ?::uuid"
                                + " ORDER BY criado_em",
                        grupo);
                assertThat(linhas).extracting(l -> l.get("acao"))
                        .containsExactly("ALTERAR_FOTO_GRUPO_CHAT_INTERNO", "REMOVER_FOTO_GRUPO_CHAT_INTERNO");
                assertThat(linhas).extracting(l -> l.get("ator_id")).containsOnly(idAna);
            });
        }

        @Test
        @DisplayName("PNG valido e aceito")
        void pngValido() throws Exception {
            String grupo = criarGrupo("png");

            assertThat(enviarFoto(ana, grupo, "grupo.png", "image/png", png(300, 300)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(armazenamento.quantidade()).isOne();
        }

        @Test
        @DisplayName("WebP valido e aceito (decodificado pelo plugin de producao)")
        void webpValido() throws Exception {
            String grupo = criarGrupo("webp");

            ResponseEntity<String> resposta = enviarFoto(ana, grupo, "grupo.webp", "image/webp", webp());

            assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
            BufferedImage pronta = ImageIO.read(new ByteArrayInputStream(armazenamento.unicoArquivo().conteudo()));
            assertThat(pronta.getWidth()).isEqualTo(256);
        }

        @Test
        @DisplayName("substituir apaga a foto anterior do storage, muda a versao e deixa um unico objeto")
        void substituicao() throws Exception {
            String grupo = criarGrupo("substituicao");
            String primeira = urlDe(enviarFoto(ana, grupo, "um.png", "image/png", png(200, 200)));
            String referenciaAntiga = referenciaNoBanco(grupo);

            String segunda = urlDe(enviarFoto(ana, grupo, "dois.png", "image/png", png(220, 220)));

            assertThat(segunda).isNotEqualTo(primeira);
            assertThat(armazenamento.quantidade()).isOne();
            assertThat(armazenamento.existe(referenciaAntiga)).isFalse();
            assertThat(armazenamento.existe(referenciaNoBanco(grupo))).isTrue();
            assertThat(armazenamento.remocoes()).isOne();
            assertThat(conversa(bruno, grupo).path("fotoUrl").asText()).isEqualTo(segunda);
        }

        @Test
        @DisplayName("remover volta ao avatar padrao, apaga o objeto e e idempotente (uma so mensagem de sistema)")
        void remocao() throws Exception {
            String grupo = criarGrupo("remocao");
            enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200));

            ResponseEntity<String> removida = chamar(ana, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/foto", null);
            ResponseEntity<String> repetida = chamar(ana, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/foto", null);

            assertThat(removida.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(removida.getBody()).path("fotoUrl").isNull()).isTrue();
            assertThat(repetida.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(armazenamento.quantidade()).isZero();
            assertThat(conversa(bruno, grupo).path("fotoUrl").isNull()).isTrue();
            assertThat(baixarFoto(bruno, grupo).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(mensagensDeSistema(ana, grupo).stream().filter(c -> c.contains("FOTO_REMOVIDA")).count()).isOne();
            assertThat(db.queryForObject("SELECT foto_referencia FROM chat_interno_conversa WHERE id = ?::uuid",
                    String.class, grupo)).isNull();
        }

        @Test
        @DisplayName("12 trocas simultaneas do criador: sobra exatamente o objeto apontado pelo banco")
        void trocasSimultaneas_naoDeixamOrfao() throws Exception {
            String grupo = criarGrupo("simultaneas");
            enviarFoto(ana, grupo, "inicial.png", "image/png", png(200, 200));
            int concorrentes = 12;
            CountDownLatch largada = new CountDownLatch(1);
            ExecutorService threads = Executors.newFixedThreadPool(concorrentes);
            try {
                List<Future<HttpStatus>> resultados = new ArrayList<>();
                for (int i = 0; i < concorrentes; i++) {
                    byte[] imagem = png(200 + i, 200 + i);
                    Callable<HttpStatus> tarefa = () -> {
                        largada.await();
                        return HttpStatus.valueOf(enviarFoto(ana, grupo, "g.png", "image/png", imagem).getStatusCode().value());
                    };
                    resultados.add(threads.submit(tarefa));
                }
                largada.countDown();
                for (Future<HttpStatus> resultado : resultados) {
                    assertThat(resultado.get(60, TimeUnit.SECONDS)).isEqualTo(HttpStatus.OK);
                }
            } finally {
                threads.shutdownNow();
            }

            await().atMost(ESPERA).untilAsserted(() -> assertThat(armazenamento.quantidade()).isOne());
            assertThat(armazenamento.existe(referenciaNoBanco(grupo))).isTrue();
        }
    }

    // ------------------------------------------------------------------ autorizacao

    @Nested
    @DisplayName("autorizacao validada no backend")
    class Autorizacao {

        @Test
        @DisplayName("participante que nao criou o grupo recebe 403 em POST e DELETE, direto pela API, sem alterar nada")
        void participanteComum() throws Exception {
            String grupo = criarGrupo("so o criador");
            String urlOriginal = urlDe(enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200)));
            String referencia = referenciaNoBanco(grupo);
            int mensagensAntes = mensagensDeSistema(ana, grupo).size();

            for (Tokens intruso : List.of(bruno, gestor)) {
                ResponseEntity<String> troca = enviarFoto(intruso, grupo, "invasor.png", "image/png", png(210, 210));
                ResponseEntity<String> remocao = chamar(intruso, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/foto", null);

                assertThat(troca.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(remocao.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                JsonNode problema = json.readTree(troca.getBody());
                assertThat(problema.path("title").asText()).isEqualTo("Sem permissao para alterar a foto");
                assertThat(problema.path("status").asInt()).isEqualTo(403);
            }

            assertThat(referenciaNoBanco(grupo)).isEqualTo(referencia);
            assertThat(conversa(ana, grupo).path("fotoUrl").asText()).isEqualTo(urlOriginal);
            assertThat(armazenamento.quantidade()).isOne();
            assertThat(armazenamento.salvamentos()).as("o arquivo do intruso nem foi gravado").isOne();
            assertThat(mensagensDeSistema(ana, grupo)).hasSize(mensagensAntes);
        }

        @Test
        @DisplayName("o proprio UPDATE exige o criador: a RLS deixa qualquer participante escrever, entao o banco e a ultima barreira")
        void updateCondicionadoAoCriador() {
            String grupo = criarGrupo("barreira no banco");
            UUID id = UUID.fromString(grupo);
            java.time.Instant agora = java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

            assertThat(repositorio.definirFotoDoGrupo(id, idBruno, "grupo/invasor.png", agora))
                    .as("participante que nao criou").isFalse();
            assertThat(repositorio.definirFotoDoGrupo(id, UUID.randomUUID(), "grupo/qualquer.png", agora))
                    .as("usuario qualquer").isFalse();
            assertThat(referenciaNoBanco(grupo)).isNull();

            assertThat(repositorio.definirFotoDoGrupo(id, idAna, "grupo/legitima.png", agora)).isTrue();
            assertThat(referenciaNoBanco(grupo)).isEqualTo("grupo/legitima.png");
            assertThat(repositorio.definirFotoDoGrupo(id, idBruno, null, null)).as("remocao por quem nao criou").isFalse();
            assertThat(referenciaNoBanco(grupo)).isEqualTo("grupo/legitima.png");
        }

        @Test
        @DisplayName("quem nao participa do grupo recebe 403 em POST, DELETE e GET")
        void naoParticipante() throws Exception {
            String grupo = criarGrupo("de fora");
            enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200));
            int salvamentos = armazenamento.salvamentos();

            assertThat(enviarFoto(admin, grupo, "x.png", "image/png", png(200, 200)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chamar(admin, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/foto", null).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(baixarFoto(admin, grupo).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(armazenamento.salvamentos()).isEqualTo(salvamentos);
            assertThat(armazenamento.quantidade()).isOne();
        }

        @Test
        @DisplayName("sem token a API responde 401; grupo inexistente e 403 (nao revela existencia)")
        void semTokenEInexistente() {
            HttpHeaders semToken = new HttpHeaders();
            assertThat(rest.exchange("/api/v1/chat-interno/conversas/" + UUID.randomUUID() + "/foto", HttpMethod.GET,
                    new HttpEntity<>(null, semToken), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(enviarFoto(ana, UUID.randomUUID().toString(), "x.png", "image/png", png(200, 200)).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("criador removido do grupo por outro participante perde o direito de alterar a foto")
        void criadorRemovido() throws Exception {
            String grupo = criarGrupo("criador sai");
            chamar(bruno, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/participantes/" + idAna, null);

            assertThat(enviarFoto(ana, grupo, "x.png", "image/png", png(200, 200)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(enviarFoto(bruno, grupo, "x.png", "image/png", png(200, 200)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(armazenamento.quantidade()).isZero();
        }
    }

    // ------------------------------------------------------------------ validacao do arquivo

    @Nested
    @DisplayName("validacao do arquivo, em RFC 7807, sem gravar nada")
    class Validacao {

        @Test
        @DisplayName("tipo declarado invalido, extensao falsa, tipo incompativel, corrompido, vazio e nome com caminho: 422")
        void recusasComProblemDetail() throws Exception {
            String grupo = criarGrupo("validacao");
            byte[] executavel = new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF};
            byte[] corrompido = new byte[300];
            java.util.Arrays.fill(corrompido, (byte) 0x41);
            System.arraycopy(png(100, 100), 0, corrompido, 0, 12);

            record Caso(String descricao, String nome, String tipo, byte[] conteudo) {}
            List<Caso> casos = List.of(
                    new Caso("MIME declarado nao e imagem", "grupo.png", "application/x-msdownload", png(200, 200)),
                    new Caso("executavel com extensao de imagem", "grupo.png", "image/png", executavel),
                    new Caso("script renomeado", "grupo.png", "image/png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)),
                    new Caso("PNG declarado como JPEG", "grupo.jpg", "image/jpeg", png(200, 200)),
                    new Caso("arquivo corrompido", "grupo.png", "image/png", corrompido),
                    new Caso("extensao que nao e de imagem", "grupo.exe", "image/png", png(200, 200)),
                    new Caso("sem extensao", "grupo", "image/png", png(200, 200)),
                    new Caso("path traversal no nome", "../../etc/passwd.png", "image/png", png(200, 200)),
                    new Caso("barra no nome", "pasta/grupo.png", "image/png", png(200, 200)),
                    new Caso("menor lado abaixo do minimo", "grupo.png", "image/png", png(32, 400)),
                    new Caso("cabecalho declara 30000x30000", "grupo.png", "image/png", comDimensoesNoCabecalho(png(100, 100), 30_000, 30_000)),
                    new Caso("arquivo vazio", "grupo.png", "image/png", new byte[0]));

            for (Caso caso : casos) {
                ResponseEntity<String> resposta = enviarFoto(ana, grupo, caso.nome(), caso.tipo(), caso.conteudo());

                assertThat(resposta.getStatusCode()).as(caso.descricao()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(resposta.getHeaders().getContentType().toString()).as(caso.descricao()).contains("json");
                JsonNode problema = json.readTree(resposta.getBody());
                assertThat(problema.path("title").asText()).as(caso.descricao()).isEqualTo("Foto invalida");
                assertThat(problema.path("detail").asText()).as(caso.descricao()).isNotBlank();
            }

            assertThat(armazenamento.salvamentos()).isZero();
            assertThat(referenciaNoBanco(grupo)).isNull();
        }

        @Test
        @DisplayName("arquivo acima do limite configurado: 413 e nada gravado; o limite e o da gestao, editavel")
        void acimaDoLimite() throws Exception {
            String grupo = criarGrupo("grande");
            db.update("UPDATE configuracao_automacao SET valor = '1' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");
            byte[] ruido = pngComRuido(800, 800);
            assertThat(ruido.length).as("o PNG de ruido precisa passar de 1 MB").isGreaterThan(1024 * 1024);

            ResponseEntity<String> grande = enviarFoto(ana, grupo, "grupo.png", "image/png", ruido);

            assertThat(grande.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(json.readTree(grande.getBody()).path("title").asText()).isEqualTo("Foto excede o limite");
            assertThat(armazenamento.salvamentos()).isZero();

            db.update("UPDATE configuracao_automacao SET valor = '5' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");
            assertThat(enviarFoto(ana, grupo, "grupo.png", "image/png", ruido).getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("sem a parte multipart 'arquivo' a resposta e 400")
        void semArquivo() {
            String grupo = criarGrupo("sem arquivo");
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ana.accessToken());
            cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
            MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
            corpo.add("outro", "x");

            ResponseEntity<String> resposta = rest.exchange("/api/v1/chat-interno/conversas/" + grupo + "/foto",
                    HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ------------------------------------------------------------------ falhas

    @Nested
    @DisplayName("falhas de storage e de transacao")
    class Falhas {

        @Test
        @DisplayName("storage indisponivel: 503 em RFC 7807 e a foto anterior permanece intacta")
        void falhaNoStorage() throws Exception {
            String grupo = criarGrupo("storage fora");
            String urlAnterior = urlDe(enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200)));
            String referenciaAnterior = referenciaNoBanco(grupo);
            armazenamento.falharAoSalvar(true);

            ResponseEntity<String> resposta = enviarFoto(ana, grupo, "novo.png", "image/png", png(220, 220));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(json.readTree(resposta.getBody()).path("title").asText()).isEqualTo("Armazenamento indisponivel");
            assertThat(referenciaNoBanco(grupo)).isEqualTo(referenciaAnterior);
            assertThat(conversa(bruno, grupo).path("fotoUrl").asText()).isEqualTo(urlAnterior);
            assertThat(armazenamento.existe(referenciaAnterior)).isTrue();
        }

        @Test
        @DisplayName("falha DEPOIS de gravar no storage: a transacao reverte, o objeto novo e apagado e a foto antiga fica")
        void falhaDuranteOUpload_revertePreservaAntigaELimpaANova() throws Exception {
            String grupo = criarGrupo("falha no meio");
            enviarFoto(ana, grupo, "antiga.png", "image/png", png(200, 200));
            String referenciaAntiga = referenciaNoBanco(grupo);
            int mensagensAntes = mensagensDeSistema(ana, grupo).size();
            doThrow(new IllegalStateException("falha apos o storage (simulada)"))
                    .when(repositorio).salvarMensagemSistema(any(), any(), contains("FOTO_ALTERADA"));

            ResponseEntity<String> resposta = enviarFoto(ana, grupo, "nova.png", "image/png", png(230, 230));

            assertThat(resposta.getStatusCode().is5xxServerError()).isTrue();
            assertThat(referenciaNoBanco(grupo)).as("o banco reverteu").isEqualTo(referenciaAntiga);
            await().atMost(ESPERA).untilAsserted(() -> assertThat(armazenamento.quantidade())
                    .as("o objeto novo foi apagado; so a antiga ficou").isOne());
            assertThat(armazenamento.existe(referenciaAntiga)).isTrue();
            assertThat(mensagensDeSistema(ana, grupo)).hasSize(mensagensAntes);
        }
    }

    // ------------------------------------------------------------------ tempo real

    @Nested
    @DisplayName("atualizacao em tempo real e apos reconexao")
    class TempoReal {

        @Test
        @DisplayName("participante conectado recebe o aviso por STOMP, sem F5, na troca e na remocao")
        void participanteConectadoRecebe() throws Exception {
            String grupo = criarGrupo("tempo real");
            StompSession sessaoDoBruno = conectar(bruno);
            Captura avisos = assinar(sessaoDoBruno, "/user/queue/notificacoes");

            enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200));

            assertThat(avisos.aguardarContendo("FOTO_ALTERADA", grupo)).isTrue();
            assertThat(conversa(bruno, grupo).path("fotoUrl").asText()).contains("/conversas/" + grupo + "/foto?v=");

            chamar(ana, HttpMethod.DELETE, "/api/v1/chat-interno/conversas/" + grupo + "/foto", null);

            assertThat(avisos.aguardarContendo("FOTO_REMOVIDA", grupo)).isTrue();
            assertThat(conversa(bruno, grupo).path("fotoUrl").isNull()).isTrue();
        }

        @Test
        @DisplayName("quem estava offline ve a foto certa depois de reconectar e depois de recarregar (fonte de verdade e o banco)")
        void aposReconexaoERecarga() throws Exception {
            String grupo = criarGrupo("reconexao");
            StompSession sessaoDoBruno = conectar(bruno);
            sessaoDoBruno.disconnect();

            String url = urlDe(enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200)));

            StompSession reconectada = conectar(bruno);
            assertThat(reconectada.isConnected()).isTrue();
            assertThat(conversa(bruno, grupo).path("fotoUrl").asText()).isEqualTo(url);
            assertThat(baixarFoto(bruno, grupo).getBody()).isEqualTo(armazenamento.unicoArquivo().conteudo());
            Tokens novaSessao = ApoioAutenticacao.login(rest, EMAIL_BRUNO, SENHA_ATENDENTE);
            assertThat(conversa(novaSessao, grupo).path("fotoUrl").asText()).isEqualTo(url);
        }

        @Test
        @DisplayName("quem nao e do grupo nao recebe o aviso")
        void foraDoGrupoNaoRecebe() throws Exception {
            String grupo = criarGrupo("privado");
            StompSession sessaoDoAdmin = conectar(admin);
            Captura avisos = assinar(sessaoDoAdmin, "/user/queue/notificacoes");

            enviarFoto(ana, grupo, "grupo.png", "image/png", png(200, 200));

            assertThat(avisos.nadaContendo(grupo, Duration.ofSeconds(2))).isTrue();
        }
    }

    // ------------------------------------------------------------------ apoio

    private String criarGrupo(String nome) {
        String corpo = """
                {"nome":"%s","participantes":["%s","%s","%s"]}
                """.formatted(PREFIXO + nome, idAna, idBruno, idGestor);
        ResponseEntity<String> criado = chamar(ana, HttpMethod.POST, "/api/v1/chat-interno/conversas/grupo", corpo);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try {
            return json.readTree(criado.getBody()).path("id").asText();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private ResponseEntity<String> enviarFoto(Tokens quem, String grupo, String nome, String tipo, byte[] conteudo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(quem.accessToken());
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpHeaders parte = new HttpHeaders();
        parte.setContentType(MediaType.parseMediaType(tipo));
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new HttpEntity<>(new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nome;
            }
        }, parte));
        return rest.exchange("/api/v1/chat-interno/conversas/" + grupo + "/foto", HttpMethod.POST,
                new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private ResponseEntity<byte[]> baixarFoto(Tokens quem, String grupo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(quem.accessToken());
        return rest.exchange("/api/v1/chat-interno/conversas/" + grupo + "/foto", HttpMethod.GET,
                new HttpEntity<>(null, cabecalhos), byte[].class);
    }

    private ResponseEntity<String> chamar(Tokens quem, HttpMethod metodo, String url, String corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(quem.accessToken());
        if (corpo != null) {
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private JsonNode conversa(Tokens quem, String conversaId) throws IOException {
        ResponseEntity<String> lista = chamar(quem, HttpMethod.GET, "/api/v1/chat-interno/conversas", null);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode item : json.readTree(lista.getBody())) {
            if (conversaId.equals(item.path("id").asText())) {
                return item;
            }
        }
        throw new AssertionError("conversa " + conversaId + " nao esta na lista");
    }

    private List<String> mensagensDeSistema(Tokens quem, String grupo) throws IOException {
        ResponseEntity<String> resposta = chamar(quem, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + grupo + "/mensagens", null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> conteudos = new ArrayList<>();
        for (JsonNode mensagem : json.readTree(resposta.getBody()).path("mensagens")) {
            if ("SISTEMA".equals(mensagem.path("tipo").asText())) {
                conteudos.add(mensagem.path("conteudo").asText());
            }
        }
        return conteudos;
    }

    private String urlDe(ResponseEntity<String> resposta) throws IOException {
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody()).path("fotoUrl").asText();
    }

    private String referenciaNoBanco(String grupo) {
        return db.queryForObject("SELECT foto_referencia FROM chat_interno_conversa WHERE id = ?::uuid", String.class, grupo);
    }

    private UUID idDo(String email) {
        return db.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private static byte[] png(int largura, int altura) {
        return imagem("png", largura, altura);
    }

    private static byte[] jpeg(int largura, int altura) {
        return imagem("jpg", largura, altura);
    }

    private static byte[] imagem(String formato, int largura, int altura) {
        try {
            BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < largura; x++) {
                imagem.setRGB(x, x % altura, 0xC81E1E);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(imagem, formato, bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** PNG que nao comprime: precisa passar do limite de 1 MB com dimensoes normais. */
    private static byte[] pngComRuido(int largura, int altura) throws IOException {
        Random aleatorio = new Random(42);
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < largura; x++) {
            for (int y = 0; y < altura; y++) {
                imagem.setRGB(x, y, aleatorio.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", bytes);
        return bytes.toByteArray();
    }

    private static byte[] webp() throws IOException {
        try (InputStream fixture = ChatInternoFotoDoGrupoIT.class.getResourceAsStream("/avatar/foto-grupo-128x96.webp")) {
            return fixture.readAllBytes();
        }
    }

    private static byte[] comDimensoesNoCabecalho(byte[] png, int largura, int altura) {
        byte[] copia = png.clone();
        ByteBuffer.wrap(copia).putInt(16, largura).putInt(20, altura);
        CRC32 crc = new CRC32();
        crc.update(copia, 12, 17);
        ByteBuffer.wrap(copia).putInt(29, (int) crc.getValue());
        return copia;
    }

    private StompSession conectar(Tokens quem) throws Exception {
        StompSession sessao = stomp.connectAsync("ws://localhost:" + porta + "/ws?access_token=" + quem.accessToken(),
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessoes.add(sessao);
        return sessao;
    }

    private Captura assinar(StompSession sessao, String destino) {
        int antes = usuariosStomp.findSubscriptions(a -> destino.equals(a.getDestination())).size();
        Captura captura = new Captura();
        sessao.subscribe(destino, captura);
        await().atMost(ESPERA).until(() -> usuariosStomp.findSubscriptions(a -> destino.equals(a.getDestination())).size() > antes);
        return captura;
    }

    private static final class Captura implements StompFrameHandler {
        private final BlockingQueue<String> recebidas = new LinkedBlockingQueue<>();

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            recebidas.add(new String((byte[]) payload, StandardCharsets.UTF_8));
        }

        boolean aguardarContendo(String evento, String conversaId) throws InterruptedException {
            long limite = System.nanoTime() + ESPERA.toNanos();
            while (System.nanoTime() < limite) {
                String quadro = recebidas.poll(200, TimeUnit.MILLISECONDS);
                if (quadro != null && quadro.contains(evento) && quadro.contains(conversaId)) {
                    return true;
                }
            }
            return false;
        }

        boolean nadaContendo(String trecho, Duration janela) throws InterruptedException {
            long limite = System.nanoTime() + janela.toNanos();
            while (System.nanoTime() < limite) {
                String quadro = recebidas.poll(100, TimeUnit.MILLISECONDS);
                if (quadro != null && quadro.contains(trecho)) {
                    return false;
                }
            }
            return true;
        }
    }
}
