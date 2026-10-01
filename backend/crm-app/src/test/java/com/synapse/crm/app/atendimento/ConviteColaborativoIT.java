package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Convite colaborativo ponta a ponta (docs/51), pelo ponto de entrada HTTP e pelo WebSocket real.
 *
 * <p>A = Ana (responsável), B = Bruno (convidado), C = atendente criado aqui e nunca convidado. O
 * requisito: convidar e aceitar não mudam a propriedade; A e B leem e respondem a mesma conversa em
 * tempo real; o responsável comercial e o do atendimento continuam A depois dos dois envios. Cada
 * proteção tem o negativo que a viola: C, convite pendente, recusado, expirado, saída de B e
 * atendimento finalizado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.tempo-real.outbox.intervalo-ms=3600000",
            "synapse.canal.whatsapp.provedor=fake"
        })
class ConviteColaborativoIT extends PostgresIT {

    private static final String PREFIXO = "E-convite-colab-";
    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final Duration ESPERA_NEGATIVA = Duration.ofSeconds(2);

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SimpUserRegistry usuariosStomp;

    private int porta;
    private WebSocketStompClient stomp;
    private final List<StompSession> sessoes = new ArrayList<>();
    private UUID idAna;
    private UUID idBruno;
    private UUID idCaio;
    private String emailCaio;
    private UUID lead;
    private UUID atendimento;

    @Value("${local.server.port}")
    void definirPorta(int porta) {
        this.porta = porta;
    }

    @BeforeEach
    void preparar() {
        limpar();
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        idAna = idDe(EMAIL_ANA);
        idBruno = idDe(EMAIL_BRUNO);
        idCaio = UUID.randomUUID();
        emailCaio = PREFIXO + idCaio + "@it.test";
        jdbc.update(
                "INSERT INTO usuario (id, nome, email, senha_hash, papel, senha_alterada_em)"
                        + " SELECT ?, ?, ?, senha_hash, 'ATENDENTE', now() FROM usuario WHERE email = ?",
                idCaio, PREFIXO + "Caio", emailCaio, EMAIL_ANA);
        lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em,"
                        + " ultima_mensagem_do_lead_em) VALUES (?, ?, ?, 'EM_ATENDIMENTO', now(), now())",
                lead, PREFIXO + "cliente", idAna);
        ResponseEntity<String> abertura = enviar(EMAIL_ANA, "abertura da Ana");
        assertThat(abertura.getStatusCode()).isEqualTo(HttpStatus.OK);
        atendimento = extrairUuid(abertura.getBody(), "atendimentoId");
    }

    @AfterEach
    void encerrar() {
        sessoes.forEach(sessao -> {
            if (sessao.isConnected()) sessao.disconnect();
        });
        sessoes.clear();
        if (stomp != null) stomp.stop();
        await().atMost(ESPERA).until(() -> usuariosStomp.getUserCount() == 0);
        limpar();
    }

    @Test
    @DisplayName("A convida, B aceita, A e B respondem em tempo real e A continua responsavel")
    void conviteAceite_doisAtendentesRespondemSemTransferencia() throws Exception {
        StompSession sessaoA = conectar(EMAIL_ANA);
        Captura conversaA = assinar(sessaoA, "/user/queue/atendimento." + atendimento);
        Captura revogacaoA = assinar(sessaoA, "/user/queue/revogacoes");

        UUID pedido = convidarBruno();
        assertResponsavelAna();

        assertThat(chamar(EMAIL_BRUNO, HttpMethod.POST, "/api/v1/atendimentos/pedidos-entrada/" + pedido + "/aprovar", null)
                        .getStatusCode().is2xxSuccessful())
                .isTrue();
        assertResponsavelAna();
        assertThat(chamar(EMAIL_ANA, HttpMethod.GET, "/api/v1/atendimentos/" + atendimento + "/participantes", null).getBody())
                .contains(idBruno.toString());
        assertThat(origemDaParticipacao(idBruno)).isEqualTo("CONVITE");

        assertThat(historico(EMAIL_ANA).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> historicoB = historico(EMAIL_BRUNO);
        assertThat(historicoB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historicoB.getBody()).contains("abertura da Ana");

        Captura conversaB = assinar(conectar(EMAIL_BRUNO), "/user/queue/atendimento." + atendimento);

        ResponseEntity<String> envioB = enviar(EMAIL_BRUNO, "B respondendo junto");
        assertThat(envioB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envioB.getBody()).contains("\"transferiuOLead\":false");
        assertThat(conversaA.aguardarContendo("B respondendo junto")).contains(idBruno.toString());
        assertThat(conversaB.aguardarContendo("B respondendo junto")).contains(idBruno.toString());
        assertResponsavelAna();

        ResponseEntity<String> envioA = enviar(EMAIL_ANA, "A segue no atendimento");
        assertThat(envioA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(envioA.getBody()).contains("\"transferiuOLead\":false");
        assertThat(conversaB.aguardarContendo("A segue no atendimento")).contains(idAna.toString());
        assertResponsavelAna();

        assertThat(autorDa("B respondendo junto")).isEqualTo(idBruno);
        assertThat(autorDa("A segue no atendimento")).isEqualTo(idAna);
        assertThat(revogacaoA.nadaEm(ESPERA_NEGATIVA)).isTrue();
    }

    @Test
    @DisplayName("C nao convidado nao le, nao envia e nao recebe a conversa")
    void terceiroNaoConvidado_semAcesso() throws Exception {
        convidarBruno();
        Captura conversaC = new Captura();
        conectar(emailCaio).subscribe("/user/queue/atendimento." + atendimento, conversaC);

        assertThat(historico(emailCaio).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviar(emailCaio, "C tentando").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        enviar(EMAIL_ANA, "so A e convidados veem");

        assertThat(conversaC.nadaEm(ESPERA_NEGATIVA)).isTrue();
        assertThat(contarMensagens("C tentando")).isZero();
        assertResponsavelAna();
    }

    @Test
    @DisplayName("convite pendente so permite ler: B nao envia nem herda o lead antes de aceitar")
    void convitePendente_naoPermiteEnviar() {
        convidarBruno();

        assertThat(historico(EMAIL_BRUNO).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(enviar(EMAIL_BRUNO, "antes de aceitar").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(contarMensagens("antes de aceitar")).isZero();
        assertResponsavelAna();
    }

    @Test
    @DisplayName("convite recusado nao concede acesso")
    void conviteRecusado_semAcesso() {
        UUID pedido = convidarBruno();

        assertThat(chamar(EMAIL_BRUNO, HttpMethod.POST, "/api/v1/atendimentos/pedidos-entrada/" + pedido + "/recusar", null)
                        .getStatusCode().is2xxSuccessful())
                .isTrue();

        assertThat(historico(EMAIL_BRUNO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviar(EMAIL_BRUNO, "depois de recusar").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertResponsavelAna();
    }

    @Test
    @DisplayName("convite expirado nao concede acesso, aceitar responde 409 e um convite novo e criado")
    void conviteExpirado_semAcessoERenovavel() {
        UUID pedido = convidarBruno();
        jdbc.update("UPDATE pedido_entrada_atendimento SET solicitado_em = now() - interval '3 hours' WHERE id = ?", pedido);

        assertThat(historico(EMAIL_BRUNO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(EMAIL_BRUNO, HttpMethod.GET, "/api/v1/atendimentos?visao=PENDENTES", null).getBody())
                .doesNotContain(atendimento.toString());
        assertThat(chamar(EMAIL_BRUNO, HttpMethod.POST, "/api/v1/atendimentos/pedidos-entrada/" + pedido + "/aprovar", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<String> novo = chamar(EMAIL_ANA, HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimento + "/convidar", Map.of("atendenteId", idBruno.toString()));
        assertThat(novo.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(novo.getBody()).contains("\"jaExistia\":false").doesNotContain(pedido.toString());
        assertThat(jdbc.queryForObject("SELECT status FROM pedido_entrada_atendimento WHERE id = ?", String.class, pedido))
                .isEqualTo("EXPIRADO");
    }

    @Test
    @DisplayName("convite repetido e idempotente")
    void conviteRepetido_idempotente() {
        UUID primeiro = convidarBruno();
        ResponseEntity<String> segundo = chamar(EMAIL_ANA, HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimento + "/convidar", Map.of("atendenteId", idBruno.toString()));

        assertThat(segundo.getBody()).contains("\"jaExistia\":true").contains(primeiro.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM pedido_entrada_atendimento WHERE atendimento_id = ? AND tipo = 'CONVITE'",
                        Integer.class, atendimento))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("saida de B retira o acesso dele sem afetar A")
    void saidaDoConvidado_retiraAcessoSemAfetarResponsavel() {
        aceitarConviteDoBruno();

        assertThat(chamar(EMAIL_BRUNO, HttpMethod.POST, "/api/v1/atendimentos/" + atendimento + "/sair", null)
                        .getStatusCode().is2xxSuccessful())
                .isTrue();

        assertThat(historico(EMAIL_BRUNO).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviar(EMAIL_BRUNO, "depois de sair").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(historico(EMAIL_ANA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(enviar(EMAIL_ANA, "A continua").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertResponsavelAna();
    }

    @Test
    @DisplayName("atendimento finalizado nao aceita convite nem envio do participante")
    void atendimentoFinalizado_semConviteNemEnvio() {
        aceitarConviteDoBruno();
        assertThat(chamar(EMAIL_ANA, HttpMethod.POST, "/api/v1/atendimentos/" + atendimento + "/finalizar", null)
                        .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> convite = chamar(EMAIL_ANA, HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimento + "/convidar", Map.of("atendenteId", idCaio.toString()));
        assertThat(convite.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(enviar(EMAIL_BRUNO, "depois de finalizar").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(contarMensagens("depois de finalizar")).isZero();
    }

    @Test
    @DisplayName("transferencia explicita continua transferindo")
    void transferenciaExplicita_continuaTransferindo() {
        aceitarConviteDoBruno();

        ResponseEntity<String> transferencia = chamar(EMAIL_ANA, HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimento + "/transferir", Map.of("paraAtendenteId", idBruno.toString()));

        assertThat(transferencia.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responsavelDoLead()).isEqualTo(idBruno);
        assertThat(responsavelDoAtendimento()).isEqualTo(idBruno);
    }

    // --- apoio ------------------------------------------------------------------

    private UUID convidarBruno() {
        ResponseEntity<String> resposta = chamar(EMAIL_ANA, HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimento + "/convidar", Map.of("atendenteId", idBruno.toString()));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return extrairUuid(resposta.getBody(), "pedidoId");
    }

    private void aceitarConviteDoBruno() {
        UUID pedido = convidarBruno();
        assertThat(chamar(EMAIL_BRUNO, HttpMethod.POST, "/api/v1/atendimentos/pedidos-entrada/" + pedido + "/aprovar", null)
                        .getStatusCode().is2xxSuccessful())
                .isTrue();
    }

    private void assertResponsavelAna() {
        assertThat(responsavelDoLead()).isEqualTo(idAna);
        assertThat(responsavelDoAtendimento()).isEqualTo(idAna);
    }

    private UUID responsavelDoLead() {
        return jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead);
    }

    private UUID responsavelDoAtendimento() {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    private String origemDaParticipacao(UUID usuario) {
        return jdbc.queryForObject(
                "SELECT origem FROM atendimento_participante WHERE atendimento_id = ? AND usuario_id = ? AND saiu_em IS NULL",
                String.class, atendimento, usuario);
    }

    private UUID autorDa(String conteudo) {
        return jdbc.queryForObject(
                "SELECT remetente_id FROM mensagem WHERE atendimento_id = ? AND conteudo = ?",
                UUID.class, atendimento, conteudo);
    }

    private int contarMensagens(String conteudo) {
        return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE conteudo = ?", Integer.class, conteudo);
    }

    private ResponseEntity<String> historico(String email) {
        return chamar(email, HttpMethod.GET, "/api/v1/atendimentos/" + atendimento + "/mensagens", null);
    }

    private ResponseEntity<String> enviar(String email, String conteudo) {
        Map<String, Object> corpo = atendimento == null
                ? Map.of("leadId", lead.toString(), "conteudo", conteudo)
                : Map.of("leadId", lead.toString(), "atendimentoId", atendimento.toString(), "conteudo", conteudo);
        return chamar(email, HttpMethod.POST, "/api/v1/atendimentos/mensagens", corpo);
    }

    private ResponseEntity<String> chamar(String email, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token(email));
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private String token(String email) {
        return ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
    }

    private StompSession conectar(String email) throws Exception {
        String url = "ws://localhost:" + porta + "/ws?access_token=" + token(email);
        StompSession sessao = stomp.connectAsync(url, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessoes.add(sessao);
        return sessao;
    }

    private Captura assinar(StompSession sessao, String destino) {
        int antes = usuariosStomp.findSubscriptions(s -> destino.equals(s.getDestination())).size();
        Captura captura = new Captura();
        sessao.subscribe(destino, captura);
        await().atMost(ESPERA).until(() ->
                usuariosStomp.findSubscriptions(s -> destino.equals(s.getDestination())).size() > antes);
        return captura;
    }

    private UUID idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private static UUID extrairUuid(String json, String campo) {
        return UUID.fromString(json.replaceAll(".*\"" + campo + "\":\"([^\"]+)\".*", "$1"));
    }

    private void limpar() {
        String filtroLead = "SELECT id FROM lead WHERE nome LIKE '" + PREFIXO + "%'";
        String filtroAtendimento = "SELECT id FROM atendimento WHERE lead_id IN (" + filtroLead + ")";
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE lead_id IN (" + filtroLead + ")");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (" + filtroAtendimento + ")");
        jdbc.update("DELETE FROM pedido_entrada_atendimento WHERE atendimento_id IN (" + filtroAtendimento + ")");
        jdbc.update("DELETE FROM atendimento_participante WHERE atendimento_id IN (" + filtroAtendimento + ")");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (" + filtroLead + ")");
        jdbc.update("DELETE FROM lead WHERE nome LIKE '" + PREFIXO + "%'");
        jdbc.update("DELETE FROM usuario WHERE email LIKE '" + PREFIXO + "%'");
    }

    /** Corpo cru de cada frame STOMP, numa fila que o teste consome por condição. */
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

        String aguardarContendo(String trecho) {
            List<String> vistos = new ArrayList<>();
            await().atMost(ESPERA).until(() -> {
                String valor = recebidas.poll();
                if (valor != null) vistos.add(valor);
                return vistos.stream().anyMatch(v -> v.contains(trecho));
            });
            return vistos.stream().filter(v -> v.contains(trecho)).findFirst().orElseThrow();
        }

        boolean nadaEm(Duration tempo) throws InterruptedException {
            return recebidas.poll(tempo.toMillis(), TimeUnit.MILLISECONDS) == null;
        }
    }
}
