package com.synapse.crm.app.core;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT) @ActiveProfiles("dev") class MensagensRapidasIT extends PostgresIT{
 @Autowired TestRestTemplate http;@Autowired JdbcTemplate jdbc;@Autowired ObjectMapper json;UUID ana,bruno,rapidaBruno;
 @BeforeEach void preparar(){ana=usuario(EMAIL_ANA);bruno=usuario(EMAIL_BRUNO);rapidaBruno=UUID.randomUUID();jdbc.update("INSERT INTO mensagem_rapida(id,atendente_id,palavra_chave,conteudo) VALUES(?,?,?,?)",rapidaBruno,bruno,"privada"+rapidaBruno.toString().substring(0,6),"Conteudo privado do Bruno");}
 @Test @DisplayName("CRUD pessoal persiste e palavra-chave e unica sem diferenciar caixa") void crud()throws Exception{String chave="proposta"+UUID.randomUUID().toString().substring(0,6);var criada=chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.POST,"/api/v1/mensagens-rapidas",Map.of("palavraChave",chave,"conteudo","Segue a proposta"));assertThat(criada.getStatusCode()).isEqualTo(HttpStatus.CREATED);String id=json.readTree(criada.getBody()).path("id").asText();assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.POST,"/api/v1/mensagens-rapidas",Map.of("palavraChave",chave.toUpperCase(),"conteudo","Duplicada")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.PUT,"/api/v1/mensagens-rapidas/"+id,Map.of("palavraChave",chave+"2","conteudo","Proposta revisada")).getBody()).contains("Proposta revisada");assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.DELETE,"/api/v1/mensagens-rapidas/"+id,null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);}
 @Test @DisplayName("RN-CRM-04: atendente NAO lista, edita nem remove mensagem rapida do colega") void negativo(){assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.GET,"/api/v1/mensagens-rapidas",null).getBody()).doesNotContain(rapidaBruno.toString());var corpo=Map.of("palavraChave","tentativa","conteudo","Tentativa");assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.PUT,"/api/v1/mensagens-rapidas/"+rapidaBruno,corpo).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(chamar(EMAIL_ANA,SENHA_ATENDENTE,HttpMethod.DELETE,"/api/v1/mensagens-rapidas/"+rapidaBruno,null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);assertThat(jdbc.queryForObject("SELECT conteudo FROM mensagem_rapida WHERE id=?",String.class,rapidaBruno)).isEqualTo("Conteudo privado do Bruno");}
 @Test @DisplayName("gestor ve todas, mas endpoint minhas limita autocomplete pessoal") void gestor(){assertThat(chamar(EMAIL_GESTOR,SENHA_GESTOR,HttpMethod.GET,"/api/v1/mensagens-rapidas",null).getBody()).contains(rapidaBruno.toString()).contains("Bruno Atendente");assertThat(chamar(EMAIL_GESTOR,SENHA_GESTOR,HttpMethod.GET,"/api/v1/mensagens-rapidas?minhas=true",null).getBody()).doesNotContain(rapidaBruno.toString());}
 ResponseEntity<String> chamar(String e,String s,HttpMethod m,String u,Object c){String token=ApoioAutenticacao.login(http,e,s).accessToken();HttpHeaders h=new HttpHeaders();h.setBearerAuth(token);h.setContentType(MediaType.APPLICATION_JSON);return http.exchange(u,m,new HttpEntity<>(c,h),String.class);}UUID usuario(String e){return jdbc.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,e);}}
