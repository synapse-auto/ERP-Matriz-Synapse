package com.synapse.crm.app.gestao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.application.EnviarMensagemUseCase;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;

/**
 * Gestao no tempo real (docs/47): mudanca de papel derruba as assinaturas do rebaixado, avisa a fila
 * pessoal para recarregar permissoes sem F5 e o JWT antigo nao reabre o WebSocket. E Redis fora do ar
 * nao abre nem trava permissao.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "synapse.canal.outbox.intervalo-ms=3600000",
    "synapse.tempo-real.outbox.intervalo-ms=3600000",
    "synapse.canal.whatsapp.provedor=fake"
})
class GestaoTempoRealIT extends PostgresIT {

    private static final String PREFIXO = "Gestao TR ";
    private static final Duration ESPERA = Duration.ofSeconds(5);

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private SimpUserRegistry usuariosStomp;
    @Autowired private EnviarMensagemUseCase enviar;

    private int porta;
    private WebSocketStompClient stomp;
    private final List<StompSession> sessoes = new ArrayList<>();
    private final List<UUID> criados = new ArrayList<>();
    private UUID ana;
    private UUID leadDaAna;

    @Value("${local.server.port}")
    void definirPorta(int porta) {
        this.porta = porta;
    }

    @BeforeEach
    void preparar() {
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, ApoioAutenticacao.EMAIL_ANA);
        leadDaAna = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em)"
                + " VALUES (?, ?, ?, 'EM_ATENDIMENTO', now(), now())", leadDaAna, PREFIXO + leadDaAna, ana);
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM permissao_usuario_excecao");
    }

    @AfterEach
    void encerrar() {
        sessoes.forEach(s -> {
            if (s.isConnected()) s.disconnect();
        });
        stomp.stop();
        await().atMost(ESPERA).until(() -> usuariosStomp.getUserCount() == 0);
        ApoioRls.sair();
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM permissao_usuario_excecao");
        criados.forEach(u -> jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", u));
        // Nada deste teste pode sobrar para outra suite: um atendimento aberto esquecido aqui seria
        // finalizado por quem testa inatividade e geraria avaliacao alheia (ex.: EquipeAvaliacoesIT).
        jdbc.update("DELETE FROM outbox_evento WHERE payload::text LIKE ?", "%" + leadDaAna + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id = ?)", leadDaAna);
        jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", leadDaAna);
        jdbc.update("DELETE FROM lead WHERE id = ?", leadDaAna);
    }

    @Test
    @DisplayName("rebaixar subgestor: aviso ACESSO_ALTERADO na fila pessoal, assinatura derrubada e JWT antigo sem WebSocket")
    void rebaixamentoRevalidaAssinaturas() throws Exception {
        String tokenGestor = login(ApoioAutenticacao.EMAIL_GESTOR, ApoioAutenticacao.SENHA_GESTOR);
        UUID sub = criarSubgestor(tokenGestor);
        String tokenSub = login(emailDe(sub), "senha-gestao-tr");
        UUID atendimento = abrirAtendimentoComoAna();

        StompSession sessao = conectar(tokenSub);
        Captura avisos = assinar(sessao, "/user/queue/notificacoes");
        Captura conversa = assinar(sessao, "/user/queue/atendimento." + atendimento);
        publicarMensagem(atendimento, "antes do rebaixamento");
        assertThat(conversa.aguardarContendo("antes do rebaixamento")).isTrue();

        ResponseEntity<String> rebaixar = chamar(tokenGestor, HttpMethod.PUT, "/api/v1/usuarios/" + sub,
                Map.of("nome", "Rebaixado TR", "email", emailDe(sub), "papel", "ATENDENTE"));
        assertThat(rebaixar.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(avisos.aguardarContendo("ACESSO_ALTERADO")).isTrue();
        publicarMensagem(atendimento, "depois do rebaixamento");
        assertThat(conversa.nadaContendo("depois do rebaixamento", Duration.ofSeconds(2))).isTrue();

        assertThatThrownBy(() -> conectar(tokenSub)).isInstanceOf(java.util.concurrent.ExecutionException.class);
    }

    @Test
    @DisplayName("Redis fora do ar: salvar perfil responde rapido e a revogacao vale mesmo assim")
    void redisForaNaoAbreNemTrava() {
        String tokenGestor = login(ApoioAutenticacao.EMAIL_GESTOR, ApoioAutenticacao.SENHA_GESTOR);
        String tokenAna = login(ApoioAutenticacao.EMAIL_ANA, ApoioAutenticacao.SENHA_ATENDENTE);
        assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/mensagens-rapidas", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        long revisao = jdbc.queryForObject("SELECT revisao FROM permissao_perfil WHERE papel = 'ATENDENTE'", Long.class);

        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            long inicio = System.nanoTime();
            ResponseEntity<String> salvar = chamar(tokenGestor, HttpMethod.PUT, "/api/v1/gestao/permissoes/perfis/ATENDENTE",
                    Map.of("revisaoEsperada", revisao, "niveis", Map.of(), "acoes", Map.of("mensagens_rapidas.usar", false)));
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            assertThat(salvar.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(ms).as("salvar nao espera o Redis").isLessThan(5_000);
            assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/mensagens-rapidas", null).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }

    // --- apoio -------------------------------------------------------------------------------------

    private UUID criarSubgestor(String tokenGestor) {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> r = chamar(tokenGestor, HttpMethod.POST, "/api/v1/usuarios", Map.of(
                "nome", PREFIXO + sufixo, "email", "gestao-tr-" + sufixo + "@teste.local",
                "senha", "senha-gestao-tr", "papel", "SUBGESTOR"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, "gestao-tr-" + sufixo + "@teste.local");
        jdbc.update("UPDATE usuario SET senha_alterada_em = now() WHERE id = ?", id);
        criados.add(id);
        return id;
    }

    private UUID abrirAtendimentoComoAna() {
        ApoioRls.entrarComo(ana, PapelUsuario.ATENDENTE);
        try {
            return enviar.executar(leadDaAna, PREFIXO + "abertura").atendimento().id();
        } finally {
            ApoioRls.sair();
        }
    }

    private void publicarMensagem(UUID atendimento, String texto) {
        redis.convertAndSend("synapse:atendimento:" + atendimento, "{\"tipo\":\"MENSAGEM\",\"dados\":{\"atendimentoId\":\""
                + atendimento + "\",\"leadId\":\"" + leadDaAna + "\",\"mensagemId\":\"" + UUID.randomUUID()
                + "\",\"remetenteTipo\":\"SISTEMA\",\"remetenteId\":null,\"conteudo\":\"" + texto
                + "\",\"statusEntrega\":\"ENVIADO\",\"enviadoEm\":\"2026-01-01T00:00:00Z\"}}");
    }

    private String emailDe(UUID usuario) {
        return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
    }

    private String login(String email, String senha) {
        return ApoioAutenticacao.login(http, email, senha).accessToken();
    }

    private ResponseEntity<String> chamar(String token, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, h), String.class);
    }

    private StompSession conectar(String token) throws Exception {
        StompSession s = stomp.connectAsync("ws://localhost:" + porta + "/ws?access_token=" + token,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessoes.add(s);
        return s;
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

        boolean aguardarContendo(String trecho) throws InterruptedException {
            long limite = System.nanoTime() + ESPERA.toNanos();
            while (System.nanoTime() < limite) {
                String v = recebidas.poll(200, TimeUnit.MILLISECONDS);
                if (v != null && v.contains(trecho)) return true;
            }
            return false;
        }

        boolean nadaContendo(String trecho, Duration janela) throws InterruptedException {
            long limite = System.nanoTime() + janela.toNanos();
            while (System.nanoTime() < limite) {
                String v = recebidas.poll(100, TimeUnit.MILLISECONDS);
                if (v != null && v.contains(trecho)) return false;
            }
            return true;
        }
    }
}
