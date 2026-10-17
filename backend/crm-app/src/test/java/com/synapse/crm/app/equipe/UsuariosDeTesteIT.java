package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;

/** Pontos de entrada reais: perfil nao identifica contas de teste por aproximacao. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@org.springframework.test.context.TestPropertySource(properties="synapse.seguranca.token-interno=token-usuarios-teste-it")
class UsuariosDeTesteIT extends PostgresIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry assinaturas;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.synapse.crm.equipe.application.usuario.ArmazenamentoDeAvatar avatares;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int porta;
    private final List<UUID> criados=new ArrayList<>();
    private final List<UUID> conversas=new ArrayList<>();

    @AfterEach void limpar() {
        for(UUID id:conversas) {
            jdbc.update("DELETE FROM chat_interno_participante WHERE conversa_id=?",id);
            jdbc.update("DELETE FROM chat_interno_conversa WHERE id=?",id);
        }
        for (UUID id:criados) {
            jdbc.update("DELETE FROM mensagem_programada WHERE atendente_id=?",id);
            jdbc.update("DELETE FROM atendimento WHERE atendente_id=?",id);
            jdbc.update("DELETE FROM lead WHERE atendente_responsavel_id=?",id);
            jdbc.update("DELETE FROM audit_log WHERE ator_id=? OR entidade_id=?",id,id);
            jdbc.update("DELETE FROM usuario WHERE id=?",id);
        }
    }

    @Test void adminCriaContaPersistidaSemInferenciaDeNomeEContaComumPermaneceComum() {
        UUID teste=criar(true); UUID comum=criar(null);
        assertThat(jdbc.queryForObject("SELECT usuario_teste FROM usuario WHERE id=?",Boolean.class,teste)).isTrue();
        assertThat(jdbc.queryForObject("SELECT usuario_teste FROM usuario WHERE id=?",Boolean.class,comum)).isFalse();
        JsonNode lista=chamar(admin(),HttpMethod.GET,"/api/v1/usuarios",null).getBody();
        assertThat(lista.toString()).contains(teste.toString(),comum.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entidade_id=? AND dados_depois->>'usuarioTeste'='true'",Long.class,teste)).isPositive();
    }

    @Test void gestorSubgestorEAtendenteNaoPodemDefinirPropriedadeNemPeloPayloadComFalse() {
        for (String token:List.of(gestor(),login(http,EMAIL_SUBGESTOR,SENHA_SUBGESTOR).accessToken(),ana())) {
            for (boolean teste:List.of(true,false)) {
                var resposta=chamar(token,HttpMethod.POST,"/api/v1/usuarios",Map.of("nome","Pessoa comum", "email",UUID.randomUUID()+"@example.invalid","senha","senha-teste-123","papel","ATENDENTE","usuarioTeste",teste));
                assertThat(resposta.getStatusCode().value()).isEqualTo(403);
            }
        }
    }

    @Test void listasDiretoPorIdAvataresEPainelNaoRevelamTesteAoNaoAdmin() {
        UUID teste=criar(true); UUID comum=criar(null);
        String referencia="avatar-sintetico-"+teste;
        byte[] imagem={ (byte)137,80,78,71,13,10,26,10 };
        jdbc.update("UPDATE usuario SET foto_referencia=? WHERE id=?",referencia,teste);
        org.mockito.Mockito.when(avatares.buscar(referencia)).thenReturn(java.util.Optional.of(
                new com.synapse.crm.equipe.application.usuario.ArmazenamentoDeAvatar.Arquivo(imagem,"image/png")));
        // A negativa precisa de um recurso existente: sem foto, 404 nao prova autorizacao.
        var headersFoto=new HttpHeaders(); headersFoto.setBearerAuth(admin());
        var fotoAdm=http.exchange("/api/v1/me/foto/"+teste,HttpMethod.GET,
                new HttpEntity<>(headersFoto),byte[].class);
        assertThat(fotoAdm.getStatusCode().value()).isEqualTo(200);
        assertThat(fotoAdm.getBody()).isEqualTo(imagem);
        org.mockito.Mockito.clearInvocations(avatares);
        for (String token:List.of(gestor(),login(http,EMAIL_SUBGESTOR,SENHA_SUBGESTOR).accessToken())) {
            for (String rota:List.of("/api/v1/usuarios","/api/v1/gestao/permissoes/usuarios","/api/v1/chat-interno/contatos","/api/v1/atendimentos/destinos-de-transferencia","/api/v1/atendimentos/painel-operacional")) {
                var resposta=chamar(token,HttpMethod.GET,rota,null);
                assertThat(resposta.getStatusCode().value()).as(rota).isEqualTo(200);
                assertThat(resposta.getBody().toString()).as(rota).doesNotContain(teste.toString());
            }
            assertThat(chamar(token,HttpMethod.GET,"/api/v1/gestao/permissoes/usuarios/"+teste,null).getStatusCode().value()).isEqualTo(404);
            assertThat(chamar(token,HttpMethod.GET,"/api/v1/me/foto/"+teste,null).getStatusCode().value()).isEqualTo(404);
            assertThat(chamar(token,HttpMethod.PATCH,"/api/v1/usuarios/"+teste+"/classificacao-teste",Map.of("usuarioTeste",false,"confirmado",true)).getStatusCode().value()).isEqualTo(403);
        }
        org.mockito.Mockito.verifyNoInteractions(avatares);
        assertThat(chamar(gestor(),HttpMethod.GET,"/api/v1/usuarios",null).getBody().toString()).contains(comum.toString());
    }

    @Test void loginTrocaDeSenhaELeituraContinuamMasMutationsSaoNegadasEAuditadas() {
        UUID teste=criar(true);
        String email=jdbc.queryForObject("SELECT email FROM usuario WHERE id=?",String.class,teste);
        String inicial=login(http,email,"senha-teste-123").accessToken();
        var senha=chamar(inicial,HttpMethod.POST,"/api/v1/auth/senha",Map.of("senhaAtual","senha-teste-123","novaSenha","senha-nova-teste-456"));
        assertThat(senha.getStatusCode().value()).isEqualTo(200);
        String token=senha.getBody().path("accessToken").asText();
        assertThat(chamar(token,HttpMethod.GET,"/api/v1/me",null).getStatusCode().value()).isEqualTo(200);
        assertThat(chamar(token,HttpMethod.GET,"/api/v1/atendimentos?visao=POTENCIAIS",null).getStatusCode().value()).isEqualTo(200);
        for (String rota:List.of("/api/v1/atendimentos/mensagens", "/api/v1/atendimentos/"+UUID.randomUUID()+"/transferir", "/api/v1/atendimentos/"+UUID.randomUUID()+"/pedir-entrada", "/api/v1/lembretes", "/api/v1/mensagens-programadas", "/api/v1/chat-interno/conversas/direta")) {
            var resposta=chamar(token,HttpMethod.POST,rota,Map.of());
            assertThat(resposta.getStatusCode().value()).as(rota).isEqualTo(403);
            assertThat(resposta.getBody().path("codigo").asText()).isEqualTo("CONTA_TESTE_SOMENTE_LEITURA");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE ator_id=? AND acao='BLOQUEIO_CONTA_TESTE'",Long.class,teste)).isEqualTo(6);
    }

    @Test void classificacaoComVinculosRecusaSemCancelarAgendamentoNemAlterarDono() {
        UUID comum=criar(null); UUID lead=UUID.randomUUID(); UUID programa=UUID.randomUUID();
        jdbc.update("INSERT INTO lead(id,nome,atendente_responsavel_id) VALUES(?,'Fixture classificacao',?)",lead,comum);
        jdbc.update("INSERT INTO mensagem_programada(id,lead_id,atendente_id,conteudo,data_envio) VALUES(?,?,?,'fixture',now()+interval '1 day')",programa,lead,comum);
        var resposta=chamar(admin(),HttpMethod.PATCH,"/api/v1/usuarios/"+comum+"/classificacao-teste",Map.of("usuarioTeste",true,"confirmado",true));
        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().path("codigo").asText()).isEqualTo("USUARIO_TESTE_COM_VINCULOS");
        assertThat(resposta.getBody().path("vinculos").toString()).contains(lead.toString(),programa.toString()).doesNotContain("Fixture classificacao","fixture");
        assertThat(jdbc.queryForObject("SELECT usuario_teste FROM usuario WHERE id=?",Boolean.class,comum)).isFalse();
        assertThat(jdbc.queryForObject("SELECT status::text FROM mensagem_programada WHERE id=?",String.class,programa)).isEqualTo("AGENDADA");
    }

    @Test void bancoRecusaNovosVinculosInclusiveComoServicoOuExecucaoEnfileirada() {
        UUID teste=criar(true);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO lead(nome,atendente_responsavel_id) VALUES('fixture',?)",teste))
                .hasStackTraceContaining("CONTA_TESTE_SOMENTE_LEITURA");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO chat_interno_participante(conversa_id,usuario_id) VALUES(?,?)",UUID.randomUUID(),teste))
                .hasStackTraceContaining("CONTA_TESTE_SOMENTE_LEITURA");
    }

    @Test void admTesteTambemFicaSomenteLeituraSemAlterarOsDemaisAdministradores() {
        UUID teste=criar(true);
        jdbc.update("UPDATE usuario SET papel='ADMINISTRADOR' WHERE id=?",teste);
        String token=tokenDefinitivo(teste);
        assertThat(chamar(token,HttpMethod.GET,"/api/v1/me",null).getBody().path("usuarioTeste").asBoolean()).isTrue();
        assertThat(chamar(token,HttpMethod.POST,"/api/v1/usuarios",Map.of()).getStatusCode().value()).isEqualTo(403);
        var efetivas=chamar(token,HttpMethod.GET,"/api/v1/gestao/permissoes/minhas",null);
        assertThat(efetivas.getStatusCode().value()).isEqualTo(200);
        JsonNode capacidades=efetivas.getBody().path("capacidades");
        assertThat(capacidades.has("atendimentos.responder")).isTrue();
        assertThat(capacidades.path("atendimentos.responder").path("permitido").asBoolean()).isFalse();
        assertThat(chamar(admin(),HttpMethod.GET,"/api/v1/usuarios",null).getStatusCode().value()).isEqualTo(200);
    }

    @Test void onlineEDisponivelNuncaTransformamTesteEmDestinoDaIaOuTransferencia() {
        UUID teste=criar(true); UUID comum=criar(null);
        for(UUID id:List.of(teste,comum)) {
            jdbc.update("UPDATE usuario SET status_presenca='ONLINE' WHERE id=?",id);
            jdbc.update("INSERT INTO disponibilidade_atendente_ia(atendente_id,disponivel_para_ia) VALUES(?,TRUE) ON CONFLICT(atendente_id) DO UPDATE SET disponivel_para_ia=TRUE",id);
        }
        var headers=new HttpHeaders(); headers.set("X-Synapse-Token","token-usuarios-teste-it");
        var resposta=http.exchange("/internal/v1/atendentes/disponiveis",HttpMethod.GET,new HttpEntity<>(headers),String.class);
        assertThat(resposta.getStatusCode().value()).isEqualTo(200);
        assertThat(resposta.getBody()).contains(comum.toString()).doesNotContain(teste.toString());
        assertThat(chamar(admin(),HttpMethod.GET,"/api/v1/atendimentos/destinos-de-transferencia",null).getBody().toString()).contains(comum.toString()).doesNotContain(teste.toString());
    }

    @Test void alteracaoExigeConfirmacaoEPerfilComumNaoPermitePayloadDeClassificacao() {
        UUID comum=criar(null);
        assertThat(chamar(admin(),HttpMethod.PATCH,"/api/v1/usuarios/"+comum+"/classificacao-teste",Map.of("usuarioTeste",true,"confirmado",false)).getStatusCode().value()).isEqualTo(400);
        assertThat(chamar(admin(),HttpMethod.PUT,"/api/v1/usuarios/"+comum,Map.of("nome","Comum","email","nao-usado@example.invalid","papel","ATENDENTE","usuarioTeste",true)).getStatusCode().value()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT usuario_teste FROM usuario WHERE id=?",Boolean.class,comum)).isFalse();
        assertThat(chamar(admin(),HttpMethod.PATCH,"/api/v1/usuarios/"+comum+"/classificacao-teste",Map.of("usuarioTeste",true,"confirmado",true)).getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT usuario_teste FROM usuario WHERE id=?",Boolean.class,comum)).isTrue();
        assertThat(chamar(admin(),HttpMethod.PATCH,"/api/v1/usuarios/"+comum+"/classificacao-teste",Map.of("usuarioTeste",false,"confirmado",true)).getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT usuario_teste FROM usuario WHERE id=?",Boolean.class,comum)).isFalse();
    }

    @Test void todaRelacaoOperacionalDoCatalogoPossuiTriggerDeBloqueio() {
        var semProtecao=jdbc.queryForList("""
            SELECT DISTINCT r.tabela FROM usuario_teste_relacao_operacional r
            WHERE NOT EXISTS (SELECT 1 FROM pg_trigger t
                WHERE t.tgrelid=(quote_ident(r.esquema)||'.'||quote_ident(r.tabela))::regclass
                  AND t.tgname='impedir_vinculo_conta_teste' AND t.tgenabled='O')
            """,String.class);
        assertThat(semProtecao).isEmpty();
    }

    @Test void guardPreservaCriacaoDeConversaComRoleRestritaMasRecusaDestinoTeste() {
        UUID comum=criar(null); UUID teste=criar(true);
        var normal=chamar(gestor(),HttpMethod.POST,"/api/v1/chat-interno/conversas/direta",Map.of("usuarioId",comum));
        assertThat(normal.getStatusCode().value()).isEqualTo(200);
        UUID conversa=UUID.fromString(normal.getBody().path("id").asText()); conversas.add(conversa);
        var recusado=chamar(admin(),HttpMethod.POST,"/api/v1/chat-interno/conversas/direta",Map.of("usuarioId",teste));
        assertThat(recusado.getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_interno_participante WHERE usuario_id=?",Long.class,teste)).isZero();
        // A role especializada nao ganhou UPDATE em usuario para conseguir tomar o lock.
        assertThat(jdbc.queryForObject("SELECT has_table_privilege('synapse_chat_rls','usuario','UPDATE')",Boolean.class)).isFalse();
    }

    @Test void classificacaoAvisaStompRealSemRevelarIdentidadeAoGestor() throws Exception {
        UUID comum=criar(null);
        var cliente=new org.springframework.web.socket.messaging.WebSocketStompClient(new org.springframework.web.socket.client.standard.StandardWebSocketClient());
        org.springframework.messaging.simp.stomp.StompSession sessao=null;
        var recebidos=new java.util.concurrent.CopyOnWriteArrayList<String>();
        String destino="/user/queue/notificacoes";
        try {
            sessao=cliente.connectAsync("ws://localhost:"+porta+"/ws?access_token="+gestor(),new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {}).get(10,java.util.concurrent.TimeUnit.SECONDS);
            int anteriores=assinaturas.findSubscriptions(a->destino.equals(a.getDestination())).size();
            sessao.subscribe(destino,new org.springframework.messaging.simp.stomp.StompFrameHandler(){
                public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders h){return byte[].class;}
                public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders h,Object corpo){recebidos.add(new String((byte[])corpo,java.nio.charset.StandardCharsets.UTF_8));}
            });
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->assinaturas.findSubscriptions(a->destino.equals(a.getDestination())).size()>anteriores);
            assertThat(chamar(admin(),HttpMethod.PATCH,"/api/v1/usuarios/"+comum+"/classificacao-teste",Map.of("usuarioTeste",true,"confirmado",true)).getStatusCode().value()).isEqualTo(200);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->recebidos.stream().anyMatch(s->s.contains("ACESSO_ALTERADO")));
            assertThat(recebidos.stream().filter(s->s.contains("ACESSO_ALTERADO"))).allSatisfy(s->assertThat(s).doesNotContain(comum.toString()));
            assertThat(chamar(gestor(),HttpMethod.GET,"/api/v1/usuarios",null).getBody().toString()).doesNotContain(comum.toString());
        } finally {
            if(sessao!=null&&sessao.isConnected())sessao.disconnect();
            cliente.stop();
        }
    }

    private String tokenDefinitivo(UUID usuario) {
        String email=jdbc.queryForObject("SELECT email FROM usuario WHERE id=?",String.class,usuario);
        String inicial=login(http,email,"senha-teste-123").accessToken();
        return chamar(inicial,HttpMethod.POST,"/api/v1/auth/senha",Map.of("senhaAtual","senha-teste-123","novaSenha","senha-nova-teste-456")).getBody().path("accessToken").asText();
    }

    private UUID criar(Boolean teste) {
        var dados=new java.util.HashMap<String,Object>(Map.of("nome","Pessoa comum "+UUID.randomUUID(),"email",UUID.randomUUID()+"@example.invalid","senha","senha-teste-123","papel","ATENDENTE"));
        if(teste!=null) dados.put("usuarioTeste",teste);
        var resposta=chamar(admin(),HttpMethod.POST,"/api/v1/usuarios",dados);
        assertThat(resposta.getStatusCode().value()).isEqualTo(201);
        UUID id=UUID.fromString(resposta.getBody().path("id").asText()); criados.add(id); return id;
    }
    private String admin(){return login(http,EMAIL_ADMINISTRADOR,SENHA_ADMINISTRADOR).accessToken();}
    private String gestor(){return login(http,EMAIL_GESTOR,SENHA_GESTOR).accessToken();}
    private String ana(){return login(http,EMAIL_ANA,SENHA_ATENDENTE).accessToken();}
    private ResponseEntity<JsonNode> chamar(String token,HttpMethod metodo,String rota,Object corpo){
        var headers=new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return http.exchange(rota,metodo,new HttpEntity<>(corpo,headers),JsonNode.class);
    }
}
