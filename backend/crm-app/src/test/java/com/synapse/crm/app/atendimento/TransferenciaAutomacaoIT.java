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
    @DisplayName("automacao resolve nome sem diferenciar maiusculas e por substring")
    void buscaAtendentePorNome() {
        UUID daiane = criarAtendenteDisponivel("DAIANE-BUSCA");
        UUID diane = criarAtendenteDisponivel("DIANE-BUSCA");

        ResponseEntity<String> respostaExata = buscarPorNome(TOKEN, "daiane-busca");

        assertThat(respostaExata.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaExata.getBody()).contains(daiane.toString()).contains("DAIANE-BUSCA");

        ResponseEntity<String> respostaParcial = buscarPorNome(TOKEN, "iane-bus");

        assertThat(respostaParcial.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respostaParcial.getBody()).contains(daiane.toString()).contains(diane.toString());
    }

    @Test
    @DisplayName("busca por nome sem correspondencia devolve lista vazia")
    void buscaAtendentePorNomeSemCorrespondencia() {
        ResponseEntity<String> resposta = buscarPorNome(TOKEN, "NAO-EXISTE-BUSCA");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo("[]");
    }

    @Test
    @DisplayName("busca respeita ativo e papel, mas nao disponibilidade para IA")
    void buscaAtendentePorNomeRespeitaElegibilidade() {
        UUID inativo = criarAtendenteDisponivel("INATIVO-BUSCA");
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", inativo);
        UUID gestor = criarUsuarioDisponivel("GESTOR-BUSCA", "GESTOR");
        UUID administrador = criarUsuarioDisponivel("ADMIN-BUSCA", "ADMINISTRADOR");
        UUID foraDoRodizio = criarAtendenteDisponivel("FORA-RODIZIO-BUSCA");
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE WHERE atendente_id = ?", foraDoRodizio);

        ResponseEntity<String> resposta = buscarPorNome(TOKEN, "BUSCA");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody())
                .contains(foraDoRodizio.toString())
                .doesNotContain(inativo.toString())
                .doesNotContain(gestor.toString())
                .doesNotContain(administrador.toString());
    }

    @Test
    @DisplayName("nome ausente ou em branco retorna 400")
    void buscaAtendentePorNomeExigeNome() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", TOKEN);
        ResponseEntity<String> ausente = http.exchange(
                "/internal/v1/atendimentos/atendentes", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
        ResponseEntity<String> emBranco = buscarPorNome(TOKEN, "");

        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(emBranco.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("busca exige o token interno")
    void buscaAtendentePorNomeExigeToken() {
        HttpHeaders semToken = new HttpHeaders();

        ResponseEntity<String> ausente = http.exchange(
                "/internal/v1/atendimentos/atendentes?nome=daiane", HttpMethod.GET, new HttpEntity<>(semToken), String.class);
        ResponseEntity<String> invalido = buscarPorNome("token-forjado", "daiane");

        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("transferencia explicita reatribui atendimento humano e atualiza o lead")
    void transferenciaExplicitaReatribuiAtendimentoHumano() {
        UUID michele = criarAtendenteDisponivel("MICHELE");
        UUID daiane = criarAtendenteDisponivel("DAIANE");
        UUID atendimento = criarAtendimentoHumano("PEDIDO-CLIENTE", michele);

        ResponseEntity<String> resposta = chamarComToken(
                TOKEN, atendimento, Map.of("atendenteId", daiane.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isEqualTo(daiane);
        assertThat(statusDoLead(atendimento)).isEqualTo("EM_ATENDIMENTO");
        assertThat(origemDoEvento(atendimento)).isEqualTo("AUTOMACAO");
        assertThat(atorDoEvento(atendimento)).isNull();
    }

    @Test
    @DisplayName("transferencia explicita nao reatribui atendimento finalizado")
    void transferenciaExplicitaBloqueiaAtendimentoFinalizado() {
        UUID michele = criarAtendenteDisponivel("MICHELE-FINALIZADO");
        UUID daiane = criarAtendenteDisponivel("DAIANE-FINALIZADO");
        UUID atendimento = criarAtendimentoHumano("FINALIZADO", michele);
        UUID lead = jdbc.queryForObject(
                "SELECT lead_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
        jdbc.update(
                "UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = CURRENT_TIMESTAMP WHERE id = ?",
                atendimento);
        jdbc.update("UPDATE lead SET status_basico = 'FINALIZADO' WHERE id = ?", lead);

        ResponseEntity<String> resposta = chamarComToken(
                TOKEN, atendimento, Map.of("atendenteId", daiane.toString()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dono(atendimento)).isEqualTo(michele);
        assertThat(quantidadeDeTransferencias(atendimento)).isZero();
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

    @Test
    @DisplayName("rodizio continua recusando atendimento que ja tem humano")
    void rodizioNaoReatribuiAtendimentoHumano() {
        UUID michele = criarAtendenteDisponivel("MICHELE-RODIZIO");
        criarAtendenteDisponivel("DAIANE-RODIZIO");
        UUID atendimento = criarAtendimentoHumano("RODIZIO-HUMANO", michele);

        ResponseEntity<String> resposta = http.exchange(
                url(atendimento).replace("/transferir", "/transferir-proximo-humano"),
                HttpMethod.POST,
                entidadeComToken(TOKEN, PREFIXO + "rodizio-humano", null),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dono(atendimento)).isEqualTo(michele);
        assertThat(quantidadeDeTransferencias(atendimento)).isZero();
    }

    private ResponseEntity<String> chamarComToken(String token, UUID atendimento, Object corpo) {
        return http.exchange(url(atendimento), HttpMethod.POST, entidadeComToken(token, PREFIXO + atendimento, corpo), String.class);
    }

    private ResponseEntity<String> buscarPorNome(String token, String nome) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", token);
        return http.exchange(
                "/internal/v1/atendimentos/atendentes?nome=" + nome,
                HttpMethod.GET,
                new HttpEntity<>(cabecalhos),
                String.class);
    }

    private HttpEntity<Object> entidadeComToken(String token, String chave, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", token);
        cabecalhos.set("Idempotency-Key", chave);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(corpo, cabecalhos);
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

    private UUID criarAtendimentoHumano(String marcador, UUID atendente) {
        UUID lead = criarLead(marcador, atendente, "EM_ATENDIMENTO");
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,atendente_id,status) VALUES (?,?,?,'EM_ATENDIMENTO')",
                atendimento,
                lead,
                atendente);
        return atendimento;
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

    private String statusDoLead(UUID atendimento) {
        return jdbc.queryForObject(
                "SELECT status_basico::text FROM lead WHERE id = (SELECT lead_id FROM atendimento WHERE id = ?)",
                String.class,
                atendimento);
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
