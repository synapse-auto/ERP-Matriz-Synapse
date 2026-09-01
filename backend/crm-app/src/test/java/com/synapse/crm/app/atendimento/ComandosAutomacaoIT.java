package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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

/** Ponta a ponta dos quatro comandos internos, incluindo autorização, rollback e retry. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.seguranca.token-interno=e33-token",
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.canal.webhook.intervalo-ms=3600000"
        })
class ComandosAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "e33-token";
    private static final String PREFIXO = "E33-COMANDO-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM comando_automacao_idempotencia WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM outbox_evento");
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN (SELECT id FROM usuario WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE numero LIKE '55619033%'");
        jdbc.update("DELETE FROM canal WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    void responderComoIa_gravaMensagemOutboxSemTransferir() {
        UUID atendimento = criarAtendimento("RESPONDER", "EM_IA", null, true);

        ResponseEntity<String> resposta = chamar(
                HttpMethod.POST,
                url(atendimento, "responder"),
                TOKEN,
                "chave-responder",
                Map.of("conteudo", "Mensagem da automacao"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"statusEntrega\":\"PENDENTE\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ? AND remetente_tipo = 'IA'", Integer.class, atendimento)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE tipo = 'canal.mensagem.enviar'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE atendimento_id = ? AND tipo LIKE '%TRANSFERIDO%'", Integer.class, atendimento)).isZero();
        await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE atendimento_id = ? AND tipo = 'MENSAGEM_ENVIADA' AND origem = 'AUTOMACAO'", Integer.class, atendimento)).isEqualTo(1));
    }

    @Test
    void responderRepetidoComMesmaChave_naoDuplica() {
        UUID atendimento = criarAtendimento("RESPONDER-RETRY", "EM_IA", null, true);
        ResponseEntity<String> primeira = chamar(HttpMethod.POST, url(atendimento, "responder"), TOKEN, "retry-1", Map.of("conteudo", "ola"));
        ResponseEntity<String> segunda = chamar(HttpMethod.POST, url(atendimento, "responder"), TOKEN, "retry-1", Map.of("conteudo", "ola"));

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segunda.getBody()).isEqualTo(primeira.getBody());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimento)).isEqualTo(1);
    }

    @Test
    void chaveReutilizadaEmOutraOperacao_retorna409() {
        UUID atendimento = criarAtendimento("CHAVE-REUSO", "EM_IA", null, true);
        UUID atendente = criarAtendente("DESTINO");
        chamar(HttpMethod.POST, url(atendimento, "responder"), TOKEN, "mesma-chave", Map.of("conteudo", "ola"));

        ResponseEntity<String> resposta = chamar(HttpMethod.POST, url(atendimento, "transferir"), TOKEN, "mesma-chave", Map.of("atendenteId", atendente.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento)).isNull();
    }

    @Test
    void chaveAusente_retorna400SemEscrita() {
        UUID atendimento = criarAtendimento("SEM-CHAVE", "EM_IA", null, true);
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, url(atendimento, "responder"), TOKEN, null, Map.of("conteudo", "ola"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimento)).isZero();
    }

    @Test
    void transferirValidaDestinoAtivoEAtendente() {
        UUID atendimento = criarAtendimento("TRANSFERIR", "EM_IA", null, false);
        UUID destino = criarAtendente("ATIVO");
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, url(atendimento, "transferir"), TOKEN, "transferir-1", Map.of("atendenteId", destino.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isEqualTo(destino);
        assertThat(origemDoEvento(atendimento)).isEqualTo("AUTOMACAO");
        assertThat(atorDoEvento(atendimento)).isNull();
    }

    @Test
    void destinoInvalido_falhaSemAlterar() {
        UUID atendimento = criarAtendimento("DESTINO-INVALIDO", "EM_IA", null, false);
        ResponseEntity<String> resposta = chamar(HttpMethod.POST, url(atendimento, "transferir"), TOKEN, "transferir-invalido", Map.of("atendenteId", UUID.randomUUID().toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(dono(atendimento)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE atendimento_id = ?", Integer.class, atendimento)).isZero();
    }

    @Test
    void modoIa_removeResponsavelEIdentificaAutomacao() {
        UUID humano = criarAtendente("HUMANO");
        UUID atendimento = criarAtendimento("MODO-IA", "EM_ATENDIMENTO", humano, false);
        ResponseEntity<String> resposta = chamar(HttpMethod.PATCH, url(atendimento, "modo-ia"), TOKEN, "modo-ia-1", Map.of());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isNull();
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, atendimento)).isEqualTo("EM_IA");
        assertThat(origemDoEvento(atendimento)).isEqualTo("AUTOMACAO");
        assertThat(atorDoEvento(atendimento)).isNull();
    }

    @Test
    void proximoHumanoEscolheDisponivelE409QuandoNaoHa() {
        UUID primeiro = criarAtendente("A-PRIMEIRO");
        UUID segundo = criarAtendente("B-SEGUNDO");
        UUID atendimento = criarAtendimento("PROXIMO", "EM_IA", null, false);

        ResponseEntity<String> sucesso = chamar(HttpMethod.POST, url(atendimento, "transferir-proximo-humano"), TOKEN, "proximo-1", null);
        assertThat(sucesso.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isIn(primeiro, segundo);

        UUID semDestino = criarAtendimento("SEM-DESTINO", "EM_IA", null, false);
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE WHERE atendente_id IN (SELECT id FROM usuario WHERE nome LIKE ?)", PREFIXO + "%");
        ResponseEntity<String> conflito = chamar(HttpMethod.POST, url(semDestino, "transferir-proximo-humano"), TOKEN, "proximo-2", null);
        assertThat(conflito.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dono(semDestino)).isNull();
    }

    @Test
    void semTokenETokenInvalido_rejeitamComando() {
        UUID atendimento = criarAtendimento("AUTH", "EM_IA", null, false);
        ResponseEntity<String> ausente = chamar(HttpMethod.POST, url(atendimento, "transferir-proximo-humano"), null, "auth-1", null);
        ResponseEntity<String> invalido = chamar(HttpMethod.POST, url(atendimento, "transferir-proximo-humano"), "errado", "auth-2", null);

        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(dono(atendimento)).isNull();
    }

    private ResponseEntity<String> chamar(
            HttpMethod metodo, String url, String token, String chave, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.set("X-Synapse-Token", token);
        }
        if (chave != null) {
            headers.set("Idempotency-Key", chave);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
    }

    private static String url(UUID atendimento, String acao) {
        return "/internal/v1/atendimentos/" + atendimento + "/" + acao;
    }

    private UUID criarAtendimento(String marcador, String status, UUID dono, boolean comCanal) {
        UUID lead = UUID.randomUUID();
        String telefone = "55619033" + String.format("%05d", Math.abs(marcador.hashCode()) % 100000);
        jdbc.update("INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,?,?::uuid,?::status_basico_lead,now(),now())", lead, PREFIXO + marcador, telefone, dono, dono == null ? "IA" : "EM_ATENDIMENTO");
        UUID credencial = comCanal ? criarCredencial(marcador, telefone) : null;
        UUID atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO atendimento (id,lead_id,canal_credencial_id,atendente_id,status,iniciado_em) VALUES (?,?,?,?::uuid,?::status_atendimento,now())", atendimento, lead, credencial, dono, status);
        return atendimento;
    }

    private UUID criarCredencial(String marcador, String telefone) {
        UUID canal = UUID.randomUUID();
        UUID credencial = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id,nome,tipo) VALUES (?,?,'WHATSAPP')", canal, PREFIXO + marcador);
        jdbc.update("INSERT INTO canal_credencial (id,canal_id,numero,identificador_externo,token_ref,ativo) VALUES (?,?,?,?, 'secret://e33',TRUE)", credencial, canal, telefone, telefone);
        return credencial;
    }

    private UUID criarAtendente(String marcador) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update("INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE)", id, PREFIXO + marcador, id + "@e33.invalid", senha);
        jdbc.update("INSERT INTO disponibilidade_atendente_ia (atendente_id,disponivel_para_ia) VALUES (?,TRUE)", id);
        return id;
    }

    private UUID dono(UUID atendimento) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    private String origemDoEvento(UUID atendimento) {
        return jdbc.queryForObject("SELECT origem::text FROM evento_timeline WHERE atendimento_id = ? ORDER BY criado_em DESC LIMIT 1", String.class, atendimento);
    }

    private UUID atorDoEvento(UUID atendimento) {
        return jdbc.queryForObject("SELECT ator_id FROM evento_timeline WHERE atendimento_id = ? ORDER BY criado_em DESC LIMIT 1", UUID.class, atendimento);
    }
}
