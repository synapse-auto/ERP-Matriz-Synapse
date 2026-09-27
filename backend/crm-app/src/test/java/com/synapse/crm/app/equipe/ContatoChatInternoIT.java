package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao.Tokens;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class ContatoChatInternoIT extends PostgresIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @ParameterizedTest @ValueSource(strings={"direta","grupo"})
    void contatoExternoComMultiplosNumerosPersisteEReplayNaoDuplica(String tipo){
        var ana=login(http,EMAIL_ANA,SENHA_ATENDENTE);UUID conversa=conversa(ana,tipo);
        UUID chave=UUID.randomUUID();long antes=quantidade(conversa);
        var corpo=Map.of("nome","Contato informado","telefones",List.of("+55 (61) 99999-1234","6133334444"));
        var resposta=enviar(ana,conversa,chave,corpo);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("tipo","CONTATO").containsEntry("midiaUrl",null);
        String id=resposta.getBody().get("id").toString();
        assertThat(enviar(ana,conversa,chave,corpo).getBody()).containsEntry("id",id);
        assertThat(quantidade(conversa)).isEqualTo(antes+1);
        var historico=chamadaAutenticada(http,"/api/v1/chat-interno/conversas/"+conversa+"/mensagens",HttpMethod.GET,ana,null,String.class);
        assertThat(historico.getBody()).contains("Contato informado","6133334444","EXTERNO");
        assertThat(enviar(ana,conversa,chave,Map.of("nome","Outro contato")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var gestor=login(http,EMAIL_GESTOR,SENHA_GESTOR);
        assertThat(enviar(gestor,conversa,UUID.randomUUID(),corpo).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamadaAutenticada(http,"/api/v1/chat-interno/conversas/"+conversa+"/mensagens",HttpMethod.GET,gestor,null,String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test void identidadeInternaUsaUuidENomeCanonicoSemPesquisaPorTelefone(){
        var ana=login(http,EMAIL_ANA,SENHA_ATENDENTE);UUID conversa=conversa(ana,"direta");
        UUID bruno=db.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,EMAIL_BRUNO);
        var resposta=enviar(ana,conversa,UUID.randomUUID(),Map.of("usuarioId",bruno.toString(),"nome","Nome arbitrario"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody().get("midiaMetadados").toString()).contains(bruno.toString(),"Bruno","INTERNO").doesNotContain("Nome arbitrario");
        assertThat(enviar(ana,conversa,UUID.randomUUID(),Map.of("usuarioId",UUID.randomUUID())).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(enviar(ana,conversa,UUID.randomUUID(),Map.of("usuarioId",bruno,"telefones",List.of("6133334444"))).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void payloadInvalidoNaoPersiste(){
        var ana=login(http,EMAIL_ANA,SENHA_ATENDENTE);UUID conversa=conversa(ana,"direta");long antes=quantidade(conversa);
        for(var corpo:List.of(Map.of("nome",""),Map.of("nome","Contato","telefones",List.of("javascript:alert(1)")),Map.of("nome","Contato","telefones",List.of("12"))))
            assertThat(enviar(ana,conversa,UUID.randomUUID(),corpo).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(quantidade(conversa)).isEqualTo(antes);
        assertThat(http.postForEntity("/api/v1/chat-interno/conversas/"+conversa+"/mensagens/contato",Map.of("nome","Contato"),String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void duasRequisicoesConcorrentesGravamUmaMensagem(){
        var ana=login(http,EMAIL_ANA,SENHA_ATENDENTE);UUID conversa=conversa(ana,"direta");long antes=quantidade(conversa);UUID chave=UUID.randomUUID();
        var um=CompletableFuture.supplyAsync(()->enviar(ana,conversa,chave,Map.of("nome","Contato concorrente")));
        var dois=CompletableFuture.supplyAsync(()->enviar(ana,conversa,chave,Map.of("nome","Contato concorrente")));
        assertThat(um.join().getBody().get("id")).isEqualTo(dois.join().getBody().get("id"));
        assertThat(quantidade(conversa)).isEqualTo(antes+1);
    }

    private <T> ResponseEntity<T> chamadaAutenticada(TestRestTemplate cliente,String caminho,HttpMethod metodo,Tokens token,Object corpo,Class<T> classe){
        var headers=new HttpHeaders();headers.setBearerAuth(token.accessToken());headers.setContentType(MediaType.APPLICATION_JSON);
        return cliente.exchange(caminho,metodo,new HttpEntity<>(corpo,headers),classe);
    }
    private long quantidade(UUID conversa){return db.queryForObject("SELECT count(*) FROM chat_interno_mensagem WHERE conversa_id=?",Long.class,conversa);}
    private UUID conversa(Tokens ana,String tipo){
        UUID bruno=db.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,EMAIL_BRUNO);
        var corpo=tipo.equals("direta")?Map.of("usuarioId",bruno):Map.of("nome","Contato interno","participantes",List.of(bruno));
        var r=chamadaAutenticada(http,"/api/v1/chat-interno/conversas/"+tipo,HttpMethod.POST,ana,corpo,Map.class);
        return UUID.fromString(r.getBody().get("id").toString());
    }
    private ResponseEntity<Map> enviar(Tokens ana,UUID conversa,UUID chave,Object corpo){
        var headers=new HttpHeaders();headers.setBearerAuth(ana.accessToken());headers.setContentType(MediaType.APPLICATION_JSON);headers.set("Idempotency-Key",chave.toString());
        return http.exchange("/api/v1/chat-interno/conversas/"+conversa+"/mensagens/contato",HttpMethod.POST,new HttpEntity<>(corpo,headers),Map.class);
    }
}
