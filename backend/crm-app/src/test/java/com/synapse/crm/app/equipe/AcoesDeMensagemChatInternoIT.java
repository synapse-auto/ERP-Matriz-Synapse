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
