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
class ReacoesDeChatInternoIT extends PostgresIT {

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("participante reage; nao participante e gestor de fora recebem 403; outra conversa nao herda")
    void participacaoIsolaReacao() throws Exception {
        Tokens ana = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE);
        Tokens bruno = ApoioAutenticacao.login(http, EMAIL_BRUNO, SENHA_ATENDENTE);
        Tokens gestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR);

        UUID brunoId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_BRUNO);
        UUID gestorId = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_GESTOR);

        String conversaAnaBruno = abrir(ana, brunoId);
        String conversaAnaGestor = abrir(ana, gestorId);
        String mensagemId = enviar(ana, conversaAnaBruno, "texto da reacao");
        enviar(ana, conversaAnaGestor, "outra conversa");

        ResponseEntity<String> definida = putReacao(ana, conversaAnaBruno, mensagemId, "👍");
        assertThat(definida.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = json.readTree(definida.getBody());
        assertThat(corpo.path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(1);

        ResponseEntity<String> doBruno = putReacao(bruno, conversaAnaBruno, mensagemId, "👍");
        assertThat(doBruno.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(doBruno.getBody()).path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(2);

        assertThat(putReacao(gestor, conversaAnaBruno, mensagemId, "🎉").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(deleteReacao(gestor, conversaAnaBruno, mensagemId).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(listar(gestor, conversaAnaBruno).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        JsonNode outra = json.readTree(listar(ana, conversaAnaGestor).getBody());
        for (JsonNode item : outra.path("mensagens")) {
            assertThat(item.path("id").asText()).isNotEqualTo(mensagemId);
            assertThat(item.path("reacoes")).isEmpty();
        }

        JsonNode pagina = json.readTree(listar(ana, conversaAnaBruno).getBody());
        JsonNode alvo = null;
        for (JsonNode item : pagina.path("mensagens")) {
            if (mensagemId.equals(item.path("id").asText())) {
                alvo = item;
            }
        }
        assertThat(alvo).isNotNull();
        assertThat(alvo.path("reacoes").get(0).path("quantidade").asInt()).isEqualTo(2);
        assertThat(alvo.path("reacoes").get(0).path("reagi").asBoolean()).isTrue();
    }

    private String abrir(Tokens quem, UUID destino) {
        ResponseEntity<Map> resposta = chamar(quem, HttpMethod.POST, "/api/v1/chat-interno/conversas/direta",
                "{\"usuarioId\":\"" + destino + "\"}", Map.class);
        return resposta.getBody().get("id").toString();
    }

    private String enviar(Tokens quem, String conversaId, String texto) throws Exception {
        ResponseEntity<String> resposta = chamar(quem, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens",
                "{\"conteudo\":\"" + texto + "\"}", String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(resposta.getBody()).path("id").asText();
    }

    private ResponseEntity<String> putReacao(Tokens quem, String conversaId, String mensagemId, String emoji) {
        return chamar(quem, HttpMethod.PUT,
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens/" + mensagemId + "/reacao",
                "{\"emoji\":\"" + emoji + "\"}", String.class);
    }

    private ResponseEntity<String> deleteReacao(Tokens quem, String conversaId, String mensagemId) {
        return chamar(quem, HttpMethod.DELETE,
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens/" + mensagemId + "/reacao",
                null, String.class);
    }

    private ResponseEntity<String> listar(Tokens quem, String conversaId) {
        return chamar(quem, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens", null, String.class);
    }

    private <T> ResponseEntity<T> chamar(Tokens tokens, HttpMethod metodo, String url, String corpo, Class<T> tipo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(tokens.accessToken());
        if (corpo != null) {
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), tipo);
    }
}
