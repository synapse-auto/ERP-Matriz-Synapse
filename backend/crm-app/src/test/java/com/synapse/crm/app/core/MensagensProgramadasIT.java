package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.mensagemprogramada.AgendadorDeMensagensProgramadas;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@org.springframework.test.context.TestPropertySource(properties = {
        "synapse.canal.whatsapp.provedor=fake",
        "synapse.suporte.mensagens-programadas.intervalo-ms=3600000"
})
class MensagensProgramadasIT extends PostgresIT {
    @Autowired TestRestTemplate http; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired ApplicationContext contexto;
    @Autowired AgendadorDeMensagensProgramadas agendador;
    @Autowired PublicadorDaOutbox publicador;
    @Autowired CanalFake canal;
    UUID ana,bruno,leadAna,leadBruno,mensagemBruno; Instant futuro;
    @BeforeEach void preparar(){
        ana=usuario(EMAIL_ANA);bruno=usuario(EMAIL_BRUNO);futuro=Instant.now().plusSeconds(86_400);
        leadAna=lead("E13 programada Ana "+UUID.randomUUID(),ana);leadBruno=lead("E13 programada Bruno "+UUID.randomUUID(),bruno);
        mensagemBruno=UUID.randomUUID();jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,?,?)",mensagemBruno,leadBruno,bruno,"Privada do Bruno",Timestamp.from(futuro));
    }
    @Test @DisplayName("cria, edita enquanto AGENDADA e cancela sem excluir") void fluxoCompleto()throws Exception{
        var criada=chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.POST,"/api/v1/mensagens-programadas",Map.of("leadId",leadAna,"conteudo","Enviar proposta","dataEnvio",futuro.toString()));
        assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);String id=json.readTree(criada.getBody()).path("id").asText();
        var editada=chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.PUT,"/api/v1/mensagens-programadas/"+id,Map.of("conteudo","Enviar proposta revisada","dataEnvio",futuro.plusSeconds(3600).toString()));
        assertThat(editada.getBody()).contains("proposta revisada").contains("AGENDADA");
        var cancelada=chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.POST,"/api/v1/mensagens-programadas/"+id+"/cancelar",null);
        assertThat(cancelada.getBody()).contains("CANCELADA");
        assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.PUT,"/api/v1/mensagens-programadas/"+id,Map.of("conteudo","Nao pode","dataEnvio",futuro.plusSeconds(7200).toString())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem_programada WHERE id=?",Integer.class,UUID.fromString(id))).isOne();
    }
    @Test @DisplayName("RN-CRM-04: atendente NAO lista, edita nem cancela programada do colega") void negativo(){
        assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.GET,"/api/v1/mensagens-programadas",null).getBody()).doesNotContain(mensagemBruno.toString());
        assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.PUT,"/api/v1/mensagens-programadas/"+mensagemBruno,Map.of("conteudo","Tentativa","dataEnvio",futuro.plusSeconds(3600).toString())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.POST,"/api/v1/mensagens-programadas/"+mensagemBruno+"/cancelar",null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.POST,"/api/v1/mensagens-programadas",Map.of("leadId",leadBruno,"conteudo","Invadir","dataEnvio",futuro.toString())).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?",String.class,mensagemBruno)).isEqualTo("AGENDADA");
    }
    @Test @DisplayName("gestor ve mensagens de todos com coluna do atendente") void gestor(){String corpo=chamar(EMAIL_GESTOR,SENHA_GESTOR,HttpMethod.GET,"/api/v1/mensagens-programadas",null).getBody();assertThat(corpo).contains(mensagemBruno.toString()).contains("Bruno Atendente");}
    @Test @DisplayName("programada vencida entra uma vez na outbox e chega ao canal") void vencidaProcessadaUmaVez(){
        canal.limpar();
        UUID lead = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lead(id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES(?,?,?,?,'EM_ATENDIMENTO',now(),now())", lead, "E60 agendada", "5561999888777", ana);
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,?,?)", id, lead, ana, "Lembrete vencido", Timestamp.from(Instant.now().minusSeconds(30)));

        agendador.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?", String.class, id)).isEqualTo("ENVIADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload->>'mensagemProgramadaId'=?", Integer.class, id.toString())).isOne();

        publicador.publicarPendentes();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(canal.enviados()).hasSize(1));
        agendador.processarPendentes();
        assertThat(canal.enviados()).hasSize(1);
    }
    @Test @DisplayName("scheduler automatico fica desligado na integracao, mas o ponto manual continua disponivel") void schedulerNaoERegistradoNoContextoDeIntegracao(){
        assertThat(contexto.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }
    @Test @DisplayName("job de fundo nao processa linha criada por outro cenario") void jobDeFundoNaoProcessaLinhaDoCenario(){
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,?,?)", id, leadAna, ana, "Nao processar em segundo plano", Timestamp.from(Instant.now().minusSeconds(30)));

        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?", String.class, id)).isEqualTo("AGENDADA"));
    }
    @Test @DisplayName("programada futura e cancelada nunca entram no pipeline") void futuraOuCanceladaNaoProcessa(){
        UUID futura = UUID.randomUUID();
        UUID cancelada = UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,?,?)", futura, leadAna, ana, "Ainda nao", Timestamp.from(Instant.now().plusSeconds(3600)));
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio,status) VALUES(?,?,?,?,?,'CANCELADA')", cancelada, leadAna, ana, "Cancelada", Timestamp.from(Instant.now().minusSeconds(30)));

        canal.limpar();
        agendador.processarPendentes();

        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?", String.class, futura)).isEqualTo("AGENDADA");
        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?", String.class, cancelada)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload->>'mensagemProgramadaId' IN (?,?)", Integer.class, futura.toString(), cancelada.toString())).isZero();
        assertThat(canal.enviados()).isEmpty();
    }

    @Test @DisplayName("falha durante a materializacao faz rollback e mantem AGENDADA") void falhaMantemAgendada(){
        canal.fecharJanela();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,?,?)", id, leadAna, ana, "Falha", Timestamp.from(Instant.now().minusSeconds(30)));

        agendador.processarPendentes();

        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?", String.class, id)).isEqualTo("AGENDADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload->>'mensagemProgramadaId'=?", Integer.class, id.toString())).isZero();
        canal.abrirJanela();
    }


    @Test @DisplayName("duas execucoes concorrentes reservam a mesma programada uma unica vez") void concorrenciaNaoDuplica(){
        canal.limpar();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,?,?)", id, leadAna, ana, "Uma vez", Timestamp.from(Instant.now().minusSeconds(30)));

        CompletableFuture<Void> primeira = CompletableFuture.runAsync(agendador::processarPendentes);
        CompletableFuture<Void> segunda = CompletableFuture.runAsync(agendador::processarPendentes);
        CompletableFuture.allOf(primeira, segunda).join();

        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?", String.class, id)).isEqualTo("ENVIADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_evento WHERE payload->>'mensagemProgramadaId'=?", Integer.class, id.toString())).isOne();
    }
    ResponseEntity<String> chamar(String email,String senha,HttpMethod metodo,String url,Object corpo){String token=ApoioAutenticacao.login(http,email,senha).accessToken();HttpHeaders h=new HttpHeaders();h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);return http.exchange(url,metodo,new HttpEntity<>(corpo,h),String.class);}
    UUID usuario(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,email);}
    UUID lead(String nome,UUID dono){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES(?,?,?,'EM_ATENDIMENTO')",id,nome,dono);return id;}
}
