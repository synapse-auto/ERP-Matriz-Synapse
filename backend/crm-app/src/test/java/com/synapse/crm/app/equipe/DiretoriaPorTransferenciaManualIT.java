package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Decisao D-12 (docs/71): a Diretoria "recebe por transferencia manual; nao entra no rodizio da triagem". O setor com
 * "recebe da triagem = nao" tira o integrante do rodizio da IA, mas nao o impede de ser destino de uma transferencia
 * feita por uma pessoa. Nenhum enum de papel muda: o integrante continua SUBGESTOR.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=e230-diretoria-token")
class DiretoriaPorTransferenciaManualIT extends PostgresIT {

    private static final String TOKEN = "e230-diretoria-token";
    private static final String PREFIXO = "E230-DIRETORIA-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    private UUID diretor;
    private UUID ana;

    @BeforeEach
    void preparar() {
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
        jdbc.update("UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'setores'");
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        UUID setor = jdbc.queryForObject(
                "INSERT INTO setor (nome, recebe_da_triagem) VALUES (?, FALSE) RETURNING id", UUID.class, PREFIXO + "Diretoria");
        diretor = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'SUBGESTOR','ONLINE',TRUE)",
                diretor, PREFIXO + "Diretor", diretor + "@e230.invalid", senha);
        jdbc.update("INSERT INTO disponibilidade_atendente_ia (atendente_id,disponivel_para_ia) VALUES (?,TRUE)", diretor);
        jdbc.update("INSERT INTO usuario_setor (usuario_id, setor_id) VALUES (?, ?)", diretor, setor);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario_setor WHERE setor_id IN (SELECT id FROM setor WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM setor WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id = ?", diretor);
        jdbc.update("DELETE FROM usuario WHERE id = ?", diretor);
        jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'setores'");
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
    }

    @Test
    @DisplayName("D-12: o integrante da Diretoria sai do rodizio da triagem, mas uma pessoa pode transferir para ele")
    void diretoriaFicaForaDoRodizioEContinuaDestinoManual() {
        // Fora do rodizio da IA (setores ligado, setor que nao recebe da triagem).
        HttpHeaders servico = new HttpHeaders();
        servico.set("X-Synapse-Token", TOKEN);
        ResponseEntity<String> rodizio = http.exchange(
                "/internal/v1/atendentes/disponiveis", HttpMethod.GET, new HttpEntity<>(servico), String.class);
        assertThat(rodizio.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rodizio.getBody()).doesNotContain(diretor.toString());

        // Mas e destino de transferencia feita por uma pessoa: aparece na lista e recebe o atendimento.
        String tokenAna = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        ResponseEntity<String> destinos = ApoioAutenticacao.comToken(
                http, tokenAna, HttpMethod.GET, "/api/v1/atendimentos/destinos-de-transferencia", String.class);
        assertThat(destinos.getBody()).contains(diretor.toString());

        UUID atendimento = atendimentoDaAna();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(tokenAna);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> transferencia = http.exchange(
                "/api/v1/atendimentos/" + atendimento + "/transferir",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("paraAtendenteId", diretor), cabecalhos),
                String.class);

        assertThat(transferencia.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento))
                .isEqualTo(diretor);
        assertThat(jdbc.queryForObject("SELECT papel::text FROM usuario WHERE id = ?", String.class, diretor))
                .as("nenhum enum mudou")
                .isEqualTo("SUBGESTOR");
    }

    private UUID atendimentoDaAna() {
        UUID lead = UUID.randomUUID();
        Timestamp agora = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,?,?,'EM_ATENDIMENTO'::status_basico_lead,?,?)",
                lead, PREFIXO + "lead", "5561900230001", ana, agora, agora);
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,?,'EM_ATENDIMENTO'::status_atendimento,?)",
                atendimento, lead, ana, agora);
        return atendimento;
    }
}
