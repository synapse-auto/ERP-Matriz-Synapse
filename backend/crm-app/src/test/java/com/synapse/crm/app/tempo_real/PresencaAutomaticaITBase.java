package com.synapse.crm.app.tempo_real;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.tempo_real.PresencaAutomatica;

/**
 * Base dos ITs da presenca automatica (E223, PR B): sessoes STOMP reais, a usuaria de seed Ana (restaurada no fim), a
 * chave {@code presenca.automatica} e a varredura chamada como o runtime a chama (o agendamento fica desligado em
 * teste). Tempo real, nunca {@code Thread.sleep}: espera-se por condicao com Awaitility.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class PresencaAutomaticaITBase extends PostgresIT {

    protected static final String CHAVE = "presenca.automatica";

    @Autowired protected TestRestTemplate http;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper mapeador;
    @Autowired protected PresencaAutomatica automatica;
    @Autowired protected SimpUserRegistry registro;

    protected UUID ana;
    private int porta;
    private WebSocketStompClient stomp;
    private final List<StompSession> abertas = new ArrayList<>();
    private String presencaDaAnaAntes;
    private Boolean flagDaAnaAntes;
    private String chaveAntes;

    @Value("${local.server.port}")
    void definirPorta(int porta) {
        this.porta = porta;
    }

    @BeforeEach
    void guardarEstado() {
        stomp = new WebSocketStompClient(new StandardWebSocketClient());
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        presencaDaAnaAntes =
                jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id = ?", String.class, ana);
        flagDaAnaAntes = jdbc.query(
                "SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                ana);
        chaveAntes = jdbc.queryForObject("SELECT valor FROM configuracao_automacao WHERE chave = ?", String.class, CHAVE);
        jdbc.update("DELETE FROM presenca_historico WHERE usuario_id = ?", ana);
        await().atMost(Duration.ofSeconds(5)).until(() -> registro.getUserCount() == 0);
        // O bean e singleton: sem isto, reconectar dentro da tolerancia do cenario anterior vira "continuacao".
        automatica.limparEstadoDe(ana);
    }

    @AfterEach
    void restaurar() {
        abertas.forEach(sessao -> {
            if (sessao.isConnected()) {
                sessao.disconnect();
            }
        });
        abertas.clear();
        stomp.stop();
        // pollDelay: o evento de desconexao chega ao registro e ao PresencaAutomatica na mesma publicacao; a pequena
        // folga garante que o nosso ja rodou antes de esquecermos o estado.
        await().pollDelay(Duration.ofMillis(300)).atMost(Duration.ofSeconds(5)).until(() -> registro.getUserCount() == 0);
        automatica.limparEstadoDe(ana);
        jdbc.update("DELETE FROM presenca_historico WHERE usuario_id = ?", ana);
        jdbc.update(
                "UPDATE usuario SET status_presenca = CAST(? AS status_presenca) WHERE id = ?", presencaDaAnaAntes, ana);
        if (flagDaAnaAntes == null) {
            jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id = ?", ana);
        } else {
            jdbc.update(
                    "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia) VALUES (?,?)"
                            + " ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = EXCLUDED.disponivel_para_ia",
                    ana,
                    flagDaAnaAntes);
        }
        jdbc.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", chaveAntes, CHAVE);
    }

    // --- cenario ------------------------------------------------------------------------------------------------

    protected void chave(boolean ligada) {
        jdbc.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", String.valueOf(ligada), CHAVE);
    }

    protected void presenca(String estado) {
        jdbc.update("UPDATE usuario SET status_presenca = CAST(? AS status_presenca) WHERE id = ?", estado, ana);
    }

    protected String presenca() {
        return jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id = ?", String.class, ana);
    }

    protected void ligarParaIa() {
        jdbc.update(
                "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia) VALUES (?, TRUE)"
                        + " ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = TRUE",
                ana);
    }

    protected List<Map<String, Object>> historico() {
        return jdbc.queryForList(
                "SELECT estado_anterior::text AS estado_anterior, estado_novo::text AS estado_novo, origem, motivo"
                        + " FROM presenca_historico WHERE usuario_id = ? ORDER BY criado_em, id",
                ana);
    }

    // --- sessoes ------------------------------------------------------------------------------------------------

    protected StompSession conectarComoAna() throws Exception {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        StompSession sessao = stomp.connectAsync(
                        "ws://localhost:" + porta + "/ws?access_token=" + token, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        abertas.add(sessao);
        return sessao;
    }

    protected void fechar(StompSession sessao) {
        sessao.disconnect();
        abertas.remove(sessao);
    }

    protected int sessoesDaAna() {
        var usuario = registro.getUser(ana.toString());
        return usuario == null ? 0 : usuario.getSessions().size();
    }

    protected void esperarSessoesDaAna(int quantas) {
        await().atMost(Duration.ofSeconds(5)).until(() -> sessoesDaAna() == quantas);
    }

    // --- HTTP -----------------------------------------------------------------------------------------------------

    protected ResponseEntity<String> patchPresencaDaAna(String estado) {
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/api/v1/usuarios/me/presenca",
                HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", estado), cabecalhos),
                String.class);
    }

    protected List<UUID> rodizio(String tokenInterno) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", tokenInterno);
        ResponseEntity<String> resposta = http.exchange(
                "/internal/v1/atendentes/disponiveis", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
        assertThat(resposta.getStatusCode().is2xxSuccessful()).as(resposta.getBody()).isTrue();
        List<UUID> ids = new ArrayList<>();
        try {
            JsonNode corpo = mapeador.readTree(resposta.getBody());
            corpo.forEach(n -> ids.add(UUID.fromString(n.path("usuarioId").asText())));
        } catch (Exception erro) {
            throw new AssertionError("corpo ilegivel: " + resposta.getBody(), erro);
        }
        return ids;
    }
}
