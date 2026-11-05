package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;

/** Entradas HTTP reais, banco real e Darwin OFF: o cadastro local nao depende do fornecedor. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {"synapse.seguranca.token-interno=teste-dados-cliente", "synapse.integracoes.darwin.habilitado=false"})
class DadosClienteFichaIT extends PostgresIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    UUID lead;
    UUID atendimento;
    String ana;
    String bruno;

    @BeforeEach void preparar() {
        ana = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        bruno = login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken();
        lead = UUID.randomUUID(); atendimento = UUID.randomUUID();
        jdbc.update("INSERT INTO campo_customizado(chave,rotulo,tipo,obrigatorio,filtravel,ordem) VALUES ('data_nascimento','Data de nascimento','DATA',false,false,99) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO lead(id,nome,telefone,status_basico,atendente_responsavel_id) VALUES (?,?,?,'EM_ATENDIMENTO',(SELECT id FROM usuario WHERE email=?))",
                lead, "Paciente sintetico", "5561" + String.format("%09d", Math.floorMod(lead.hashCode(), 1000000000)), EMAIL_ANA);
        jdbc.update("INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status,iniciado_em) VALUES (?,?,'ca000000-0000-4000-8000-000000000001',(SELECT id FROM usuario WHERE email=?),'EM_ATENDIMENTO',now())", atendimento, lead, EMAIL_ANA);
    }
    @AfterEach void limpar() {
        jdbc.update("DELETE FROM outbox_evento WHERE payload::jsonb->>'leadId'=?", lead.toString());
        jdbc.update("DELETE FROM comando_automacao_lead_idempotencia WHERE lead_id=?", lead);
        jdbc.update("DELETE FROM comando_automacao_idempotencia WHERE atendimento_id=?", atendimento);
        jdbc.update("DELETE FROM audit_log WHERE entidade_id=?", lead);
        jdbc.update("DELETE FROM atendimento WHERE id=?", atendimento);
        jdbc.update("DELETE FROM lead WHERE id=?", lead);
    }
    ResponseEntity<String> automacao(String rota, Object corpo) {
        return automacao(rota, corpo, UUID.randomUUID().toString());
    }
    ResponseEntity<String> automacao(String rota, Object corpo, String chave) {
        var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Synapse-Token", "teste-dados-cliente"); h.set("Idempotency-Key", chave);
        return http.exchange(rota, HttpMethod.POST, new HttpEntity<>(corpo,h), String.class);
    }
    ResponseEntity<String> ficha(String token) {
        var h = new HttpHeaders(); h.setBearerAuth(token);
        return http.exchange("/api/v1/leads/"+lead,HttpMethod.GET,new HttpEntity<>(h),String.class);
    }
    @Test void automacaoPersisteCamposLocaisEOutboxSemDadosPessoais() {
        assertThat(automacao("/internal/v1/ev05/leads/"+lead+"/preenchimento", Map.of("cpf","52998224725")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(automacao("/internal/v1/leads/"+lead+"/data-nascimento", Map.of("dataNascimento","1990-05-21")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ficha(ana).getBody()).contains("Paciente sintetico", "52998224725", "1990-05-21", "data_nascimento");
        assertThat(ficha(ana).getBody()).contains("52998224725"); // nova leitura preserva o banco.
        var sinais = jdbc.queryForList("SELECT payload::text FROM outbox_evento WHERE payload::jsonb->>'leadId'=? AND payload::jsonb->>'eventoTipo'='DADOS_CLIENTE_ATUALIZADOS'",String.class,lead.toString());
        assertThat(sinais).hasSize(2).allSatisfy(s -> assertThat(s).doesNotContain("Paciente sintetico","52998224725","1990-05-21"));
        assertThat(ficha(bruno).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entidade_id=? AND acao='PREENCHER_DADOS_CLIENTE_PELA_AUTOMACAO' AND ator_tipo='AUTOMACAO' AND dados_antes IS NULL AND dados_depois IS NULL", Long.class, lead)).isEqualTo(1L);
    }
    @Test void valoresManuaisNaoSaoSubstituidosEDataInvalidaNaoGrava() {
        jdbc.update("UPDATE lead SET cpf='11144477735',dados_customizados='{\"data_nascimento\":\"1985-02-03\"}'::jsonb WHERE id=?",lead);
        assertThat(automacao("/internal/v1/ev05/leads/"+lead+"/preenchimento",Map.of("cpf","52998224725")).getBody()).contains("IGNORADO_JA_PREENCHIDO");
        assertThat(automacao("/internal/v1/leads/"+lead+"/data-nascimento",Map.of("dataNascimento","1990-05-21")).getBody()).contains("IGNORADO_JA_PREENCHIDO");
        assertThat(automacao("/internal/v1/leads/"+lead+"/data-nascimento",Map.of("dataNascimento","data-invalida")).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ficha(ana).getBody()).contains("11144477735","1985-02-03").doesNotContain("52998224725");
    }
    @Test void leadParcialEAutenticacaoNegativaNaoInventamDados() {
        assertThat(ficha(ana).getBody()).contains("\"cpf\":null", "\"dadosCustomizados\":{}");
        var r = http.postForEntity("/internal/v1/leads/"+lead+"/data-nascimento",Map.of("dataNascimento","1990-05-21"),String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ficha(ana).getBody()).doesNotContain("1990-05-21");
    }
    @Test void nomeColetadoCompletaApenasFallbackERejeitaDivergenciaDeIdentidade() {
        jdbc.update("UPDATE lead SET nome=telefone WHERE id=?",lead);
        String chave = UUID.randomUUID().toString();
        var payload = Map.of("nome","Nome coletado sintetico","cpf","52998224725");
        var resposta = automacao("/internal/v1/ev05/leads/"+lead+"/preenchimento",payload,chave);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"nome\":\"APLICADO\"");
        long sinais = jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload::jsonb->>'leadId'=?",Long.class,lead.toString());
        assertThat(automacao("/internal/v1/ev05/leads/"+lead+"/preenchimento",payload,chave).getBody()).isEqualTo(resposta.getBody());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload::jsonb->>'leadId'=?",Long.class,lead.toString())).isEqualTo(sinais);
        assertThat(ficha(ana).getBody()).contains("Nome coletado sintetico");
        assertThat(automacao("/internal/v1/ev05/leads/"+lead+"/preenchimento",Map.of("nome","Outro nome")).getBody()).contains("\"nome\":\"IGNORADO_JA_PREENCHIDO\"");
        jdbc.update("UPDATE lead SET nome=telefone WHERE id=?",lead);
        assertThat(automacao("/internal/v1/ev05/leads/"+lead+"/preenchimento",Map.of("nome","Identidade divergente","cpf","11144477735")).getBody()).contains("\"nome\":\"IGNORADO_JA_PREENCHIDO\"");
        assertThat(ficha(ana).getBody()).doesNotContain("Identidade divergente");
    }
    @Test void edicaoConcorrenteDeNascimentoVenceAutomacaoSemPerdaDeOutrosCampos() throws Exception {
        var ds = jdbc.getDataSource();
        java.util.concurrent.CompletableFuture<ResponseEntity<String>> pendente;
        try (var conexao = ds.getConnection()) {
            conexao.setAutoCommit(false);
            try (var escrita = conexao.prepareStatement("UPDATE lead SET dados_customizados='{\"data_nascimento\":\"1985-02-03\",\"origem\":\"manual\"}'::jsonb WHERE id=?")) {
                escrita.setObject(1, lead); escrita.executeUpdate();
            }
            pendente = java.util.concurrent.CompletableFuture.supplyAsync(() -> automacao(
                    "/internal/v1/leads/"+lead+"/data-nascimento",Map.of("dataNascimento","1990-05-21")));
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() -> {
                // Reutiliza a conexao que segura o lock; nao disputa uma terceira vaga do bulkhead.
                try (var consulta = conexao.createStatement()) {
                    consulta.execute("SELECT pg_stat_clear_snapshot()");
                    try (var linhas = consulta.executeQuery("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%lead%'")) {
                        linhas.next(); return linhas.getInt(1) > 0;
                    }
                }
            });
            conexao.commit();
        }
        assertThat(pendente.get(10,java.util.concurrent.TimeUnit.SECONDS).getBody()).contains("IGNORADO_JA_PREENCHIDO");
        assertThat(ficha(ana).getBody()).contains("1985-02-03","manual").doesNotContain("1990-05-21");
    }
}
