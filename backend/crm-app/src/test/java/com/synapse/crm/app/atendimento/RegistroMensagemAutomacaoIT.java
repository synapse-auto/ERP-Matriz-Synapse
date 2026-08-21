package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato interno da mensagem que a Automação já entregou ao provedor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=token-e30-mensagem")
class RegistroMensagemAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "token-e30-mensagem";
    private static final String PREFIXO = "E30-MENSAGEM-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CanalFake canal;

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM mensagem_automacao_idempotencia WHERE atendimento_id IN "
                        + "(SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN "
                        + "(SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))",
                PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM outbox_evento");
        canal.limpar();
    }

    @Test
    @DisplayName("registra saída da IA sem chamar o adaptador da Meta")
    void registraSemReenviar() {
        UUID atendimento = criarAtendimento("REGISTRO");

        ResponseEntity<String> resposta = chamar(TOKEN, atendimento, corpo("wamid.E30-1"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"idempotente\":false");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimento))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT remetente_tipo::text FROM mensagem WHERE atendimento_id = ?", String.class, atendimento))
                .isEqualTo("IA");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_evento", Long.class))
                .isZero();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test
    @DisplayName("wamid repetido devolve a mesma mensagem sem duplicar")
    void wamidRepetidoEIdempotente() {
        UUID atendimento = criarAtendimento("IDEMPOTENTE");
        String corpo = corpo("wamid.E30-repetido");

        ResponseEntity<String> primeira = chamar(TOKEN, atendimento, corpo);
        ResponseEntity<String> segunda = chamar(TOKEN, atendimento, corpo);

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segunda.getBody()).contains("\"idempotente\":true");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimento))
                .isOne();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM mensagem_automacao_idempotencia WHERE wamid = ?",
                        Long.class,
                        "wamid.E30-repetido"))
                .isOne();
    }

    @Test
    @DisplayName("sem token e token errado não registram mensagem")
    void autenticacaoRecusa() {
        UUID atendimento = criarAtendimento("AUTH");

        ResponseEntity<String> semToken = chamar(null, atendimento, corpo("wamid.E30-sem-token"));
        ResponseEntity<String> errado = chamar("forjado", atendimento, corpo("wamid.E30-errado"));

        assertThat(semToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errado.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(quantidadeDeMensagens(atendimento)).isZero();
    }

    @Test
    @DisplayName("JWT humano não abre o endpoint interno")
    void jwtHumanoRecusa() {
        UUID atendimento = criarAtendimento("JWT");
        String jwt = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(jwt);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resposta = http.exchange(
                url(atendimento), HttpMethod.POST, new HttpEntity<>(corpo("wamid.E30-jwt"), cabecalhos), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(quantidadeDeMensagens(atendimento)).isZero();
    }

    @Test
    @DisplayName("atendimento inexistente devolve 404")
    void atendimentoInexistente() {
        ResponseEntity<String> resposta = chamar(TOKEN, UUID.randomUUID(), corpo("wamid.E30-inexistente"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> chamar(String token, UUID atendimento, String corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        if (token != null) cabecalhos.set("X-Synapse-Token", token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(atendimento), HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static String url(UUID atendimento) {
        return "/internal/v1/atendimentos/" + atendimento + "/mensagens-enviadas";
    }

    private static String corpo(String wamid) {
        return "{\"wamid\":\"" + wamid + "\",\"tipo\":\"TEXTO\",\"conteudo\":\"Ola da IA\"}";
    }

    private UUID criarAtendimento(String marcador) {
        UUID lead = UUID.randomUUID();
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,telefone,status_basico) VALUES (?,?,?,?::status_basico_lead)",
                lead,
                PREFIXO + marcador,
                "556199999" + marcador.hashCode() % 10000,
                "IA");
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,NULL,'EM_IA')",
                atendimento,
                lead);
        return atendimento;
    }

    private long quantidadeDeMensagens(UUID atendimento) {
        return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, atendimento);
    }
}
