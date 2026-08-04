package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class MensagensProgramadasIT extends PostgresIT {
    @Autowired TestRestTemplate http; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
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
    ResponseEntity<String> chamar(String email,String senha,HttpMethod metodo,String url,Object corpo){String token=ApoioAutenticacao.login(http,email,senha).accessToken();HttpHeaders h=new HttpHeaders();h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);return http.exchange(url,metodo,new HttpEntity<>(corpo,h),String.class);}
    UUID usuario(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,email);}
    UUID lead(String nome,UUID dono){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO lead(id,nome,atendente_responsavel_id,status_basico) VALUES(?,?,?,'EM_ATENDIMENTO')",id,nome,dono);return id;}
}
