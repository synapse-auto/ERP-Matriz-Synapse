package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.synapse.crm.app.seguranca.ApoioAutenticacao.Tokens;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class AcoesDeMensagemChatInternoIT extends PostgresIT {

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.messaging.simp.user.SimpUserRegistry usuariosStomp;
    @org.springframework.beans.factory.annotation.Value("${local.server.port}") int porta;

    @Test
    void duasSessoesDoRemetenteRecebemMensagemPersistidaSemEventoNoReplay() throws Exception {
        Tokens ana = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);
        String origem = abrir(ana, idDo(EMAIL_BRUNO));
        String destino = criarGrupo(ana, idDo(EMAIL_BRUNO));
        String mensagem = enviar(ana, origem, "origem para duas abas");
        var stomp = new org.springframework.web.socket.messaging.WebSocketStompClient(
                new org.springframework.web.socket.client.standard.StandardWebSocketClient());
        var sessoes = new java.util.ArrayList<org.springframework.messaging.simp.stomp.StompSession>();
        var filas = new java.util.ArrayList<java.util.concurrent.BlockingQueue<String>>();
        try {
            for (int i = 0; i < 2; i++) {
                var sessao = stomp.connectAsync("ws://localhost:" + porta + "/ws?access_token=" + ana.accessToken(),
                        new org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter() {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
                sessoes.add(sessao);
                var fila = new java.util.concurrent.LinkedBlockingQueue<String>();
                filas.add(fila);
                int antes = usuariosStomp.findSubscriptions(s -> s.getDestination().equals("/user/queue/notificacoes")).size();
                sessao.subscribe("/user/queue/notificacoes", new org.springframework.messaging.simp.stomp.StompFrameHandler() {
                    public java.lang.reflect.Type getPayloadType(org.springframework.messaging.simp.stomp.StompHeaders headers) { return byte[].class; }
                    public void handleFrame(org.springframework.messaging.simp.stomp.StompHeaders headers, Object payload) {
                        fila.add(new String((byte[]) payload, java.nio.charset.StandardCharsets.UTF_8));
                    }
                });
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() ->
                        usuariosStomp.findSubscriptions(s -> s.getDestination().equals("/user/queue/notificacoes")).size() > antes);
            }
            UUID chave = UUID.randomUUID();
            var resposta = encaminhar(ana, origem, mensagem, destino, chave);
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            String id = json.readTree(resposta.getBody()).path("id").asText();
            for (var fila : filas) {
                String evento = fila.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(evento).contains("CHAT_INTERNO_MENSAGEM", id);
                // Um observador externo já consegue ler o registro quando o evento chega.
                assertThat(db.queryForObject("SELECT count(*) FROM chat_interno_mensagem WHERE id = ?", Long.class, UUID.fromString(id))).isEqualTo(1);
            }
            assertThat(json.readTree(encaminhar(ana, origem, mensagem, destino, chave).getBody()).path("id").asText()).isEqualTo(id);
            for (var fila : filas) assertThat(fila.poll(1, java.util.concurrent.TimeUnit.SECONDS)).isNull();
        } finally {
            sessoes.forEach(org.springframework.messaging.simp.stomp.StompSession::disconnect);
            stomp.stop();
        }
    }

    @Test
    void encaminhamentoIdempotentePreservaParticipacaoEConflitaComOutraOrigem() throws Exception {
        Tokens ana = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);
        Tokens gestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR);
        String origem = abrir(ana, idDo(EMAIL_BRUNO));
        String destino = criarGrupo(ana, idDo(EMAIL_GESTOR));
        String mensagem = enviar(ana, origem, "encaminhamento idempotente");
        UUID chave = UUID.randomUUID();
        long antes = quantidade(destino);
        var primeira = encaminhar(ana, origem, mensagem, destino, chave);
        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = json.readTree(primeira.getBody()).path("id").asText();
        assertThat(json.readTree(encaminhar(ana, origem, mensagem, destino, chave).getBody()).path("id").asText()).isEqualTo(id);
        assertThat(quantidade(destino)).isEqualTo(antes + 1);
        String outra = enviar(ana, origem, "outra origem");
        assertThat(encaminhar(ana, origem, outra, destino, chave).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Participar do destino e ter papel amplo não libera a conversa de origem, nem no replay.
        assertThat(encaminhar(gestor, origem, mensagem, destino, chave).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(quantidade(destino)).isEqualTo(antes + 1);
    }

    @Test
    void encaminhamentosConcorrentesComMesmaChavePersistemUmaCopia() throws Exception {
        Tokens ana = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);
        String origem = abrir(ana, idDo(EMAIL_BRUNO));
        String destino = criarGrupo(ana, idDo(EMAIL_BRUNO), idDo(EMAIL_GESTOR));
        String mensagem = enviar(ana, origem, "origem concorrente");
        UUID chave = UUID.randomUUID();
        long antes = quantidade(destino);
        var um = java.util.concurrent.CompletableFuture.supplyAsync(() -> encaminhar(ana, origem, mensagem, destino, chave));
        var dois = java.util.concurrent.CompletableFuture.supplyAsync(() -> encaminhar(ana, origem, mensagem, destino, chave));
        assertThat(um.join().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(dois.join().getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(um.join().getBody()).path("id")).isEqualTo(json.readTree(dois.join().getBody()).path("id"));
        assertThat(quantidade(destino)).isEqualTo(antes + 1);
    }

    private long quantidade(String conversa) {
        return db.queryForObject("SELECT count(*) FROM chat_interno_mensagem WHERE conversa_id = ?", Long.class, UUID.fromString(conversa));
    }

    private ResponseEntity<String> encaminhar(Tokens quem, String origem, String mensagem, String destino, UUID chave) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(quem.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", chave.toString());
        return http.exchange("/api/v1/chat-interno/conversas/" + origem + "/mensagens/" + mensagem + "/encaminhar",
                HttpMethod.POST, new HttpEntity<>(Map.of("conversaDestinoId", destino), headers), String.class);
    }

    @Test
    @DisplayName("responder, excluir e encaminhar preservam citação e isolamento em conversa interna")
    void acoesPreservamAutorizacaoETombstone() throws Exception {
        Tokens ana = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);
        Tokens bruno = ApoioAutenticacao.login(http, EMAIL_BRUNO, SENHA_ATENDENTE);
        Tokens gestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR);
        UUID brunoId = idDo(EMAIL_BRUNO);
        UUID gestorId = idDo(EMAIL_GESTOR);

        String direta = abrir(ana, brunoId);
        String origem = enviar(ana, direta, "mensagem para citar");
        JsonNode resposta = json.readTree(chamar(bruno, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens/" + origem + "/responder",
                "{\"conteudo\":\"resposta interna\"}", String.class).getBody());
        assertThat(resposta.path("citacao").path("tipoReferencia").asText()).isEqualTo("RESPOSTA");
        assertThat(resposta.path("citacao").path("autor").asText()).isEqualTo("Ana Atendente");
        assertThat(resposta.path("citacao").path("previa").asText()).isEqualTo("mensagem para citar");

        ResponseEntity<String> fora = chamar(gestor, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens/" + origem + "/responder",
                "{\"conteudo\":\"nao autorizado\"}", String.class);
        assertThat(fora.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> removida = chamar(ana, HttpMethod.DELETE,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens/" + origem, null, String.class);
        assertThat(removida.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tombstone = json.readTree(removida.getBody());
        assertThat(tombstone.path("removida").asBoolean()).isTrue();
        assertThat(tombstone.path("conteudo").isNull()).isTrue();

        JsonNode historico = json.readTree(chamar(bruno, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens", null, String.class).getBody());
        JsonNode respostaHistorico = encontrar(historico.path("mensagens"), resposta.path("id").asText());
        assertThat(respostaHistorico.path("citacao").path("origemRemovida").asBoolean()).isTrue();
        assertThat(respostaHistorico.path("citacao").path("previa").asText()).isEmpty();
        JsonNode origemHistorico = encontrar(historico.path("mensagens"), origem);
        assertThat(origemHistorico.path("removida").asBoolean()).isTrue();
        assertThat(origemHistorico.path("conteudo").isNull()).isTrue();

        String destino = abrir(ana, gestorId);
        String encaminhada = enviar(ana, direta, "encaminhar esta");
        JsonNode copia = json.readTree(chamar(ana, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens/" + encaminhada + "/encaminhar",
                "{\"conversaDestinoId\":\"" + destino + "\"}", String.class).getBody());
        assertThat(copia.path("citacao").path("tipoReferencia").asText()).isEqualTo("ENCAMINHAMENTO");
        assertThat(copia.path("citacao").path("previa").asText()).isEqualTo("encaminhar esta");

        String grupo = criarGrupo(ana, brunoId, gestorId);
        String grupoOrigem = enviar(ana, grupo, "mensagem do grupo");
        JsonNode grupoResposta = json.readTree(chamar(gestor, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + grupo + "/mensagens/" + grupoOrigem + "/responder",
                "{\"conteudo\":\"resposta no grupo\"}", String.class).getBody());
        assertThat(grupoResposta.path("citacao").path("previa").asText()).isEqualTo("mensagem do grupo");
    }

    @Test
    @DisplayName("autor edita texto interno, preservando id e bloqueando outro participante")
    void editarMensagemPreservaIdentidadeEAutorizacao() throws Exception {
        Tokens ana = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);
        Tokens bruno = ApoioAutenticacao.login(http, EMAIL_BRUNO, SENHA_ATENDENTE);
        String direta = abrir(ana, idDo(EMAIL_BRUNO));
        String origem = enviar(ana, direta, "versao inicial");

        ResponseEntity<String> editada = chamar(ana, HttpMethod.PATCH,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens/" + origem,
                "{\"conteudo\":\"versao atualizada\"}", String.class);
        assertThat(editada.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = json.readTree(editada.getBody());
        assertThat(corpo.path("id").asText()).isEqualTo(origem);
        assertThat(corpo.path("conteudo").asText()).isEqualTo("versao atualizada");
        assertThat(corpo.path("editadoEm").isMissingNode()).isFalse();

        ResponseEntity<String> bloqueada = chamar(bruno, HttpMethod.PATCH,
                "/api/v1/chat-interno/conversas/" + direta + "/mensagens/" + origem,
                "{\"conteudo\":\"nao autorizado\"}", String.class);
        assertThat(bloqueada.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID idDo(String email) {
        return db.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private String abrir(Tokens quem, UUID destino) {
        ResponseEntity<Map> resposta = chamar(quem, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/direta", "{\"usuarioId\":\"" + destino + "\"}", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody().get("id").toString();
    }

    private String criarGrupo(Tokens quem, UUID... participantes) {
        String ids = java.util.Arrays.stream(participantes).map(UUID::toString)
                .map(id -> "\"" + id + "\"").collect(java.util.stream.Collectors.joining(","));
        ResponseEntity<Map> resposta = chamar(quem, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/grupo", "{\"nome\":\"Paridade\",\"participantes\":[" + ids + "]}", Map.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return resposta.getBody().get("id").toString();
    }

    private String enviar(Tokens quem, String conversaId, String texto) throws Exception {
        ResponseEntity<String> respostaHttp = chamar(quem, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens",
                "{\"conteudo\":\"" + texto + "\"}", String.class);
        assertThat(respostaHttp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode resposta = json.readTree(respostaHttp.getBody());
        return resposta.path("id").asText();
    }

    private JsonNode encontrar(JsonNode itens, String id) {
        for (JsonNode item : itens) {
            if (id.equals(item.path("id").asText())) return item;
        }
        throw new AssertionError("mensagem nao encontrada: " + id);
    }

    private <T> ResponseEntity<T> chamar(Tokens tokens, HttpMethod metodo, String url, String corpo, Class<T> tipo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(tokens.accessToken());
        if (corpo != null) cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), tipo);
    }
}
