package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato e efeitos comerciais do POST tecnico que entrega uma conversa da IA. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=token-e21b-transferencia")
class TransferenciaAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "token-e21b-transferencia";
    private static final String PREFIXO = "E21B-TRANSFERENCIA-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update(
                "DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN (SELECT id FROM usuario WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
    }

    @Test
    @DisplayName("servidor valida destinatario ATENDENTE e registra AUTOMACAO")
    void distribuiSemAceitarDestinatarioERegistraAtorTecnico() {
        UUID menorCarga = criarAtendenteDisponivel("B-MENOR-CARGA");
        UUID maiorCarga = criarAtendenteDisponivel("A-MAIOR-CARGA");
        criarAtendimentoHumano("CARGA-EXISTENTE", maiorCarga);
        UUID atendimento = criarAtendimentoDaIa("ALVO");

        ResponseEntity<String> resposta = chamarComToken(
                TOKEN, atendimento, Map.of("atendenteId", menorCarga.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(menorCarga.toString()).doesNotContain(maiorCarga.toString());
        assertThat(dono(atendimento)).isEqualTo(menorCarga);
        assertThat(origemDoEvento(atendimento)).isEqualTo("AUTOMACAO");
        assertThat(atorDoEvento(atendimento)).isNull();
        assertThat(origemDaAuditoria(atendimento)).isEqualTo("AUTOMACAO");
        assertThat(atorDaAuditoria(atendimento)).isNull();
    }

    @Test
    @DisplayName("sem token retorna 401 e nao transfere")
    void semTokenNaoTransfere() {
        criarAtendenteDisponivel("SEM-TOKEN-DESTINO");
        UUID atendimento = criarAtendimentoDaIa("SEM-TOKEN-ALVO");

        ResponseEntity<String> resposta = http.exchange(
                url(atendimento), HttpMethod.POST, new HttpEntity<>(new HttpHeaders()), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(dono(atendimento)).isNull();
        assertThat(quantidadeDeTransferencias(atendimento)).isZero();
    }

    @Test
    @DisplayName("token errado retorna 401 e nao transfere")
    void tokenErradoNaoTransfere() {
        criarAtendenteDisponivel("TOKEN-ERRADO-DESTINO");
        UUID atendimento = criarAtendimentoDaIa("TOKEN-ERRADO-ALVO");

        ResponseEntity<String> resposta = chamarComToken("token-forjado", atendimento, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(dono(atendimento)).isNull();
        assertThat(quantidadeDeTransferencias(atendimento)).isZero();
    }

    @Test
    @DisplayName("JWT humano nao abre o endpoint interno e nao transfere")
    void jwtHumanoNaoTransfere() {
        criarAtendenteDisponivel("JWT-DESTINO");
        UUID atendimento = criarAtendimentoDaIa("JWT-ALVO");
        String jwt = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(jwt);

        ResponseEntity<String> resposta = http.exchange(
                url(atendimento), HttpMethod.POST, new HttpEntity<>(cabecalhos), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(dono(atendimento)).isNull();
        assertThat(quantidadeDeTransferencias(atendimento)).isZero();
    }

    @Test
    @DisplayName("subgestor ativo e aceito como destino da automacao")
    void subgestorAtivoEAceito() {
        UUID sub = criarUsuarioDisponivel("SUB-DESTINO", "SUBGESTOR");
        UUID atendimento = criarAtendimentoDaIa("SUB-ALVO");

        ResponseEntity<String> resposta = chamarComToken(
                TOKEN, atendimento, Map.of("atendenteId", sub.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isEqualTo(sub);
    }

    @Test
    @DisplayName("gestor continua recusado com 422")
    void gestorContinuaRecusado() {
        UUID gestor = criarUsuarioDisponivel("GESTOR-DESTINO", "GESTOR");
        UUID atendimento = criarAtendimentoDaIa("GESTOR-ALVO");

        ResponseEntity<String> resposta = chamarComToken(
                TOKEN, atendimento, Map.of("atendenteId", gestor.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(dono(atendimento)).isNull();
        assertThat(quantidadeDeTransferencias(atendimento)).isZero();
    }

    private ResponseEntity<String> chamarComToken(String token, UUID atendimento, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", token);
        cabecalhos.set("Idempotency-Key", PREFIXO + atendimento);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                url(atendimento), HttpMethod.POST, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private static String url(UUID atendimento) {
        return "/internal/v1/atendimentos/" + atendimento + "/transferir";
    }

    private UUID criarAtendenteDisponivel(String marcador) {
        return criarUsuarioDisponivel(marcador, "ATENDENTE");
    }

    private UUID criarUsuarioDisponivel(String marcador, String papel) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email = ?", String.class, EMAIL_GESTOR);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca) VALUES (?,?,?,?, CAST(? AS papel_usuario),'ONLINE')",
                id,
                PREFIXO + marcador,
                id + "@e21b.invalid",
                senha,
                papel);
        jdbc.update(
                "INSERT INTO disponibilidade_atendente_ia(atendente_id,disponivel_para_ia) VALUES (?,TRUE)",
                id);
        return id;
    }

    private UUID criarAtendimentoDaIa(String marcador) {
        UUID lead = criarLead(marcador, null, "IA");
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,NULL,'EM_IA')",
                atendimento,
                lead);
        return atendimento;
    }

    private void criarAtendimentoHumano(String marcador, UUID atendente) {
        UUID lead = criarLead(marcador, atendente, "EM_ATENDIMENTO");
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,?,'EM_ATENDIMENTO')",
                UUID.randomUUID(),
                lead,
                atendente);
    }

    private UUID criarLead(String marcador, UUID atendente, String status) {
        UUID lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES (?,?,?,?::status_basico_lead)",
                lead,
                PREFIXO + marcador,
                atendente,
                status);
        return lead;
    }

    private UUID dono(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    private String origemDoEvento(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT origem::text FROM evento_timeline WHERE atendimento_id = ? AND tipo = 'ATENDIMENTO_TRANSFERIDO'",
                String.class,
                atendimento);
    }

    private UUID atorDoEvento(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT ator_id FROM evento_timeline WHERE atendimento_id = ? AND tipo = 'ATENDIMENTO_TRANSFERIDO'",
                UUID.class,
                atendimento);
    }

    private String origemDaAuditoria(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT ator_tipo::text FROM audit_log WHERE entidade_id = ? AND acao = 'ATENDIMENTO_TRANSFERIDO'",
                String.class,
                atendimento);
    }

    private UUID atorDaAuditoria(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT ator_id FROM audit_log WHERE entidade_id = ? AND acao = 'ATENDIMENTO_TRANSFERIDO'",
                UUID.class,
                atendimento);
    }

    private long quantidadeDeTransferencias(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM evento_timeline WHERE atendimento_id = ? AND tipo = 'ATENDIMENTO_TRANSFERIDO'",
                Long.class,
                atendimento);
    }
}
