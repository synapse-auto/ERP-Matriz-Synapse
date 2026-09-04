package com.synapse.crm.app.tempo_real;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.atendimento.application.TransferirAtendimentoUseCase;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Chat em tempo real, ponta a ponta: WebSocket autenticado, autorizado por assinatura, replicado
 * entre instancias por Redis.
 *
 * <p>Tres coisas esta suite prova, na ordem de risco que a E06 descreveu: que a autorizacao de
 * assinatura realmente barra quem nao enxerga o atendimento (teste negativo pareado com o positivo, no
 * mesmo espirito dos testes de RLS da E02b); que a transferencia revoga a sessao do dono anterior; e
 * que o backplane entrega o que chega pelo Redis, nao so o que a propria instancia publicou — o que
 * prova o mecanismo multi-instancia sem precisar de duas JVMs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.outbox.intervalo-ms=3600000",
            // Sem credencial real da Meta configurada neste teste; o provedor
            // falso (ja usado pela E05) aceita o envio e devolve ENVIADO.
            "synapse.canal.whatsapp.provedor=fake",
            // TTL curto (producao usa 60s, ver TempoRealProperties) para o teste
            // de E07 §0 nao precisar dormir um minuto inteiro.
            "synapse.tempo-real.ttl-assinatura-segundos=2"
        })
class TempoRealIT extends PostgresIT {

    private static final String PREFIXO = "E06-";
    private static final Duration ESPERA_CURTA = Duration.ofSeconds(3);
    private static final Duration ESPERA_NEGATIVA = Duration.ofSeconds(2);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EnviarMensagemUseCase enviar;

    @Autowired
    private TransferirAtendimentoUseCase transferir;

    @Autowired
    private PublicadorDaOutbox publicador;

    @Autowired
    private StringRedisTemplate redis;

    private int porta;
    private WebSocketStompClient stomp;
    private UUID idAna;
    private UUID idBruno;
    private UUID idGestor;
    private UUID leadDaAna;
    private final List<StompSession> sessoesAbertas = new ArrayList<>();

    @Value("${local.server.port}")
    void definirPorta(int porta) {
        this.porta = porta;
    }

    @BeforeEach
    void preparar() {
        stomp = new WebSocketStompClient(new StandardWebSocketClient());

        limpar();
        idAna = jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'ana@dev.local'", UUID.class);
        idBruno =
                jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'bruno@dev.local'", UUID.class);
        idGestor =
                jdbc.queryForObject("SELECT id FROM usuario WHERE email = 'gestor@dev.local'", UUID.class);

        leadDaAna = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico,"
                        + " ultima_interacao_em, ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO', now(), now())",
                leadDaAna,
                PREFIXO + "Cliente da Ana",
                idAna);
    }

    @AfterEach
    void encerrarSessoes() {
        sessoesAbertas.forEach(sessao -> {
            if (sessao.isConnected()) {
                sessao.disconnect();
            }
        });
        sessoesAbertas.clear();
        ApoioRls.sair();
    }

    @Nested
    @DisplayName("autenticacao no handshake")
    class Handshake {

        @Test
        @DisplayName("sem token, a conexao e recusada antes de qualquer frame STOMP")
        void semToken_conexaoRecusada() {
            CompletableFuture<StompSession> futuro =
                    stomp.connectAsync(urlWs(null), new StompSessionHandlerAdapter() {});

            assertThatThrownBy(() -> futuro.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
        }

        @Test
        @DisplayName("com token invalido, a conexao e recusada antes de qualquer frame STOMP")
        void tokenInvalido_conexaoRecusada() {
            CompletableFuture<StompSession> futuro = stomp.connectAsync(
                    urlWs("token-forjado-que-nao-decodifica"), new StompSessionHandlerAdapter() {});

            assertThatThrownBy(() -> futuro.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class);
        }

        @Test
        @DisplayName("com token valido, a conexao e aceita")
        void tokenValido_conexaoAceita() throws Exception {
            StompSession sessao = conectar(tokenDe("ana@dev.local"));
            assertThat(sessao.isConnected()).isTrue();
        }
    }

    @Nested
    @DisplayName("autorizacao de assinatura (RN-CRM-01 no WebSocket)")
    class Autorizacao {

        /**
         * O teste central da secao 1. Bruno assina o atendimento do lead da Ana e nao recebe nada — e
         * o gestor, assinado ao mesmo tempo, recebe. O par prova que o silencio de Bruno e recorte de
         * visibilidade, e nao um canal quebrado.
         */
        @Test
        @DisplayName("atendente que nao enxerga o lead assina e nao recebe nada")
        void assinatura_deLeadAlheio_naoRecebeNada() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();

            Captura capturaBruno = assinar(conectar(tokenDe("bruno@dev.local")), atendimentoId);
            Captura capturaGestor = assinar(conectar(tokenDe("gestor@dev.local")), atendimentoId);
            aguardarAssinatura();

            enviarComoAna("mensagem que so o gestor deveria ver");

            assertThat(capturaGestor.aguardar(ESPERA_CURTA)).contains("mensagem que so o gestor");
            assertThat(capturaBruno.aguardarNada(ESPERA_NEGATIVA)).isTrue();
        }

        @Test
        @DisplayName("dono do atendimento assina e recebe")
        void assinatura_doProprioAtendimento_recebe() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();
            Captura captura = assinar(conectar(tokenDe("ana@dev.local")), atendimentoId);
            aguardarAssinatura();

            enviarComoAna("ola, tudo bem?");

            assertThat(captura.aguardar(ESPERA_CURTA)).contains("ola, tudo bem");
        }
    }

    @Nested
    @DisplayName("revogacao em transferencia")
    class Revogacao {

        @Test
        @DisplayName("apos transferir, o dono anterior para de receber e e avisado")
        void transferencia_revogaODonoAnterior() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();

            StompSession sessaoAna = conectar(tokenDe("ana@dev.local"));
            Captura capturaAna = assinar(sessaoAna, atendimentoId);
            Captura revogacaoAna = new Captura();
            sessaoAna.subscribe("/user/queue/revogacoes", revogacaoAna);

            aguardarAssinatura();

            // TransferirAtendimentoUseCase e @PreAuthorize("isAuthenticated()"):
            // precisa do SecurityContext de um usuario real, nao do contexto de
            // servico (que so serve para RLS, sem passar pelo Spring Security).
            ApoioRls.entrarComo(idGestor, PapelUsuario.GESTOR);
            transferir.executar(atendimentoId, idBruno, idGestor);
            ApoioRls.sair();

            assertThat(revogacaoAna.aguardar(ESPERA_CURTA)).contains(atendimentoId.toString());

            // Bruno so assina DEPOIS de virar dono. Assinar antes seria negado —
            // provado pelo teste de Autorizacao — e uma autorizacao negada nao
            // "revive" sozinha quando a situacao muda: o cliente real reassina ao
            // ser notificado da nova atribuicao, exatamente como aqui.
            Captura capturaBruno = assinar(conectar(tokenDe("bruno@dev.local")), atendimentoId);
            aguardarAssinatura();

            enviarComoBruno("agora e comigo");

            assertThat(capturaBruno.aguardar(ESPERA_CURTA)).contains("agora e comigo");
            assertThat(capturaAna.aguardarNada(ESPERA_NEGATIVA)).isTrue();
        }

        @Test
        @DisplayName("devolver para IA entrega TRANSFERENCIA ao assinante e aviso ao dono anterior")
        void devolverParaIa_entregaTransferenciaEAviso() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();

            StompSession sessaoAna = conectar(tokenDe("ana@dev.local"));
            Captura capturaAna = assinar(sessaoAna, atendimentoId);
            Captura notificacoesAna = new Captura();
            sessaoAna.subscribe("/user/queue/notificacoes", notificacoesAna);
            aguardarAssinatura();

            ContextoDeServico.executarComo(
                    "teste-devolver-ia", () -> transferir.devolverParaIaPelaAutomacao(atendimentoId));

            String transferencia = capturaAna.aguardar(ESPERA_CURTA);
            assertThat(transferencia)
                    .contains("\"tipo\":\"TRANSFERENCIA\"")
                    .contains(atendimentoId.toString())
                    .contains(leadDaAna.toString())
                    .contains("\"status\":\"EM_IA\"")
                    .contains("\"paraAtendenteId\":null");

            String aviso = notificacoesAna.aguardar(ESPERA_CURTA);
            assertThat(aviso)
                    .contains("ATENDIMENTO_DEVOLVIDO_PARA_IA")
                    .contains(atendimentoId.toString())
                    .contains(leadDaAna.toString());
        }
    }

    @Nested
    @DisplayName("TTL do registro de assinaturas (E07 §0)")
    class TtlDoRegistro {

        /**
         * O cenario que {@link Revogacao} nao cobre: a mensagem de revogacao no Redis se perde (aqui,
         * simulada trocando o dono direto no banco, sem publicar nada — exatamente o efeito de um
         * publish que nunca chegou). Sem o TTL, Ana receberia mensagens do lead do Bruno para sempre.
         * Com ele, a proxima entrega apos o TTL vencer revalida, descobre que ela nao enxerga mais o
         * atendimento, revoga e avisa — o mesmo destino de revogacao que a transferencia usa.
         */
        @Test
        @DisplayName("com a revogacao perdida no Redis, o dono anterior para de receber apos o TTL")
        void revogacaoPerdidaNoRedis_paraDeVazarAposOTtl() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();

            StompSession sessaoAna = conectar(tokenDe("ana@dev.local"));
            Captura capturaAna = assinar(sessaoAna, atendimentoId);
            Captura revogacaoAna = new Captura();
            sessaoAna.subscribe("/user/queue/revogacoes", revogacaoAna);
            aguardarAssinatura();

            // A transferencia de verdade (TransferirAtendimentoUseCase) publicaria o
            // evento TRANSFERENCIA no Redis e revogaria Ana na hora. Aqui pulamos o
            // caso de uso e mexemos direto no banco: e o mesmo estado final de uma
            // transferencia cujo publish se perdeu.
            jdbc.update("UPDATE atendimento SET atendente_id = ? WHERE id = ?", idBruno, atendimentoId);

            publicarMensagemDireta(atendimentoId, "antes do ttl vencer, ainda vaza");
            assertThat(capturaAna.aguardar(ESPERA_CURTA)).contains("antes do ttl vencer");

            // TTL de teste = 2s (TestPropertySource da classe). Depois disto, a
            // proxima entrega precisa revalidar antes de confiar na assinatura.
            Thread.sleep(2500);

            publicarMensagemDireta(atendimentoId, "depois do ttl vencido, nao deveria vazar");

            assertThat(revogacaoAna.aguardar(ESPERA_CURTA)).contains(atendimentoId.toString());
            assertThat(capturaAna.aguardarNada(ESPERA_NEGATIVA)).isTrue();
        }
    }

    @Nested
    @DisplayName("ciclo de entrega")
    class CicloDeEntrega {

        @Test
        @DisplayName("PENDENTE -> ENVIADO chega na tela por WebSocket")
        void transicaoDeStatus_chegaNaTela() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();
            Captura captura = assinar(conectar(tokenDe("ana@dev.local")), atendimentoId);
            aguardarAssinatura();

            enviarComoAna("vou verificar o estoque");
            // A primeira mensagem que chega e o MENSAGEM (PENDENTE); a segunda,
            // apos o publisher rodar, e o STATUS (ENVIADO).
            captura.aguardar(ESPERA_CURTA);

            // Ponto de entrada real (@Scheduled), nao o metodo interno — ver o comentario
            // equivalente em CanalWhatsAppIT.rodarPublisher() sobre a E07b.
            publicador.publicarPendentes();

            String segunda = captura.aguardar(ESPERA_CURTA);
            assertThat(segunda).contains("\"tipo\":\"STATUS\"").contains("ENVIADO");
        }
    }

    @Nested
    @DisplayName("backplane multi-instancia")
    class Backplane {

        /**
         * Nao ha como subir uma segunda JVM neste teste, mas o mecanismo que faz o multi-instancia
         * funcionar e exatamente este: o assinante local entrega o que CHEGA PELO REDIS, nao o que a
         * propria instancia publicou. Publicar direto no canal — como uma instancia irma faria —
         * imita fielmente a origem remota; se a entrega funciona aqui, funciona entre instancias.
         */
        @Test
        @DisplayName("mensagem publicada direto no Redis chega ao assinante local em menos de 1s")
        void publicacaoNoRedis_chegaAoAssinanteLocalRapido() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();
            Captura captura = assinar(conectar(tokenDe("ana@dev.local")), atendimentoId);
            aguardarAssinatura();

            long inicio = System.nanoTime();
            String envelope = "{\"tipo\":\"MENSAGEM\",\"dados\":{\"atendimentoId\":\"" + atendimentoId
                    + "\",\"leadId\":\"" + leadDaAna + "\",\"mensagemId\":\"" + UUID.randomUUID()
                    + "\",\"remetenteTipo\":\"SISTEMA\",\"remetenteId\":null,"
                    + "\"conteudo\":\"veio direto do backplane\",\"statusEntrega\":\"ENVIADO\","
                    + "\"enviadoEm\":\"2026-01-01T00:00:00Z\"}}";
            redis.convertAndSend("synapse:atendimento:" + atendimentoId, envelope);

            String recebido = captura.aguardar(ESPERA_CURTA);
            long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;

            assertThat(recebido).contains("veio direto do backplane");
            assertThat(decorridoMs).isLessThan(1000);
        }
    }

    @Nested
    @DisplayName("reconciliacao apos reconexao")
    class Reconciliacao {

        @Test
        @DisplayName("mensagens enviadas com o socket fechado aparecem via REST ao reconectar")
        void reconexao_semPerda() throws Exception {
            UUID atendimentoId = abrirAtendimentoComoAna();
            StompSession sessao = conectar(tokenDe("ana@dev.local"));
            assinar(sessao, atendimentoId);

            Instant antesDaQueda = Instant.now();
            sessao.disconnect(); // simula a queda de rede

            enviarComoAna("primeira mensagem perdida na queda");
            enviarComoAna("segunda mensagem perdida na queda");

            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(tokenDe("ana@dev.local"));
            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/atendimentos/" + atendimentoId + "/mensagens/desde?desde=" + antesDaQueda,
                    HttpMethod.GET,
                    new HttpEntity<>(cabecalhos),
                    String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody())
                    .contains("primeira mensagem perdida")
                    .contains("segunda mensagem perdida");
            // Sem duplicata: cada texto aparece uma unica vez.
            assertThat(contarOcorrencias(resposta.getBody(), "primeira mensagem perdida")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("carga leve")
    class Carga {

        /**
         * 50 conexoes simultaneas nao podem competir pelo mesmo pool que atrasa o envio — e o que o
         * bulkhead da secao 5 existe para garantir. O teste nao mede latencia absoluta (o ambiente de
         * CI varia demais para isso), mas prova que o sistema aceita a carga e continua respondendo
         * dentro de um teto generoso.
         */
        @Test
        @DisplayName("50 conexoes simultaneas nao impedem o envio de completar rapido")
        void cinquentaConexoes_naoDegradamOEnvio() throws Exception {
            String token = tokenDe("ana@dev.local");
            List<StompSession> sessoes = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                sessoes.add(conectar(token));
            }
            assertThat(sessoes).allMatch(StompSession::isConnected);

            abrirAtendimentoComoAna();
            long inicio = System.nanoTime();
            enviarComoAna("mensagem sob carga de conexoes");
            long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;

            assertThat(decorridoMs).isLessThan(2000);
        }
    }

    // --- apoio: STOMP ----------------------------------------------------------

    private String urlWs(String token) {
        String base = "ws://localhost:" + porta + "/ws";
        return token == null ? base : base + "?access_token=" + token;
    }

    private StompSession conectar(String token) throws Exception {
        StompSession sessao =
                stomp.connectAsync(urlWs(token), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessoesAbertas.add(sessao);
        return sessao;
    }

    /**
     * O SUBSCRIBE trafega em canal assincrono separado do teste: {@code sessao.subscribe(...)} volta
     * antes de o servidor ter processado, autorizado e registrado a assinatura. Sem esta espera, um
     * envio disparado logo em seguida corre risco real de vencer a corrida — nao contra o mecanismo de
     * autorizacao, so contra o tempo que ele leva para terminar.
     */
    private void aguardarAssinatura() throws InterruptedException {
        Thread.sleep(400);
    }

    private Captura assinar(StompSession sessao, UUID atendimentoId) {
        Captura captura = new Captura();
        sessao.subscribe("/user/queue/atendimento." + atendimentoId, captura);
        return captura;
    }

    /** Frame handler que devolve o corpo cru como texto, e uma fila para o teste consumir. */
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

        String aguardar(Duration tempo) throws InterruptedException {
            String valor = recebidas.poll(tempo.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(valor).as("esperava receber uma mensagem e nao chegou nenhuma").isNotNull();
            return valor;
        }

        boolean aguardarNada(Duration tempo) throws InterruptedException {
            return recebidas.poll(tempo.toMillis(), TimeUnit.MILLISECONDS) == null;
        }
    }

    // --- apoio: dominio ---------------------------------------------------------

    private UUID abrirAtendimentoComoAna() {
        ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
        UUID atendimentoId = enviar.executar(leadDaAna, PREFIXO + "abertura").atendimento().id();
        ApoioRls.sair();
        return atendimentoId;
    }

    private void enviarComoAna(String texto) {
        ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
        enviar.executar(leadDaAna, texto);
        ApoioRls.sair();
    }

    private void enviarComoBruno(String texto) {
        ApoioRls.entrarComo(idBruno, PapelUsuario.ATENDENTE);
        enviar.executar(leadDaAna, texto);
        ApoioRls.sair();
    }

    private String tokenDe(String email) {
        String senha = "gestor@dev.local".equals(email)
                ? ApoioAutenticacao.SENHA_GESTOR
                : ApoioAutenticacao.SENHA_ATENDENTE;
        return ApoioAutenticacao.login(http, email, senha).accessToken();
    }

    /** Publica direto no canal do Redis, sem passar pela outbox nem pelo relay — ver {@link Backplane}. */
    private void publicarMensagemDireta(UUID atendimentoId, String texto) {
        String envelope = "{\"tipo\":\"MENSAGEM\",\"dados\":{\"atendimentoId\":\"" + atendimentoId
                + "\",\"leadId\":\"" + leadDaAna + "\",\"mensagemId\":\"" + UUID.randomUUID()
                + "\",\"remetenteTipo\":\"SISTEMA\",\"remetenteId\":null,"
                + "\"conteudo\":\"" + texto + "\",\"statusEntrega\":\"ENVIADO\","
                + "\"enviadoEm\":\"2026-01-01T00:00:00Z\"}}";
        redis.convertAndSend("synapse:atendimento:" + atendimentoId, envelope);
    }

    private static int contarOcorrencias(String texto, String trecho) {
        return texto.split(Pattern.quote(trecho), -1).length - 1;
    }

    private void limpar() {
        jdbc.update("DELETE FROM outbox_evento");
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }
}
