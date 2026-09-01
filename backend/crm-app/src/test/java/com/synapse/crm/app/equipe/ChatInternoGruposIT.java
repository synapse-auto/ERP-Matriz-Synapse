package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class ChatInternoGruposIT extends PostgresIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate db;

    @Test
    @DisplayName("grupo: tres enxergam, quarto nao; participante comum gerencia; historico completo; DIRETA intacta")
    void fluxoCompletoDeGrupo() {
        Tokens ana = ApoioAutenticacao.login(rest, EMAIL_ANA, SENHA_ATENDENTE);
        Tokens bruno = ApoioAutenticacao.login(rest, EMAIL_BRUNO, SENHA_ATENDENTE);
        Tokens gestor = ApoioAutenticacao.login(rest, EMAIL_GESTOR, SENHA_GESTOR);
        Tokens admin = ApoioAutenticacao.login(rest, EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR);

        UUID idAna = idDo(EMAIL_ANA);
        UUID idBruno = idDo(EMAIL_BRUNO);
        UUID idGestor = idDo(EMAIL_GESTOR);
        UUID idAdmin = idDo(EMAIL_ADMINISTRADOR);

        String criar = """
                {"nome":"Ops Vidro","participantes":["%s","%s","%s"]}
                """.formatted(idAna, idBruno, idGestor);
        ResponseEntity<Map> criado = chamar(ana, HttpMethod.POST, "/api/v1/chat-interno/conversas/grupo",
                criar, Map.class);
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String grupoId = criado.getBody().get("id").toString();

        assertThat(listaContem(ana, grupoId)).isTrue();
        assertThat(listaContem(bruno, grupoId)).isTrue();
        assertThat(listaContem(gestor, grupoId)).isTrue();
        assertThat(listaContem(admin, grupoId)).as("quarto usuario nao participa").isFalse();

        ResponseEntity<Map> mensagensAdmin = chamar(admin, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + grupoId + "/mensagens", null, Map.class);
        assertThat(mensagensAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        List<Map<String, Object>> msgs = mensagens(ana, grupoId);
        assertThat(msgs).anySatisfy(m -> {
            assertThat(m.get("tipo")).isEqualTo("SISTEMA");
            assertThat(m.get("conteudo").toString()).contains("GRUPO_CRIADO");
        });

        chamar(ana, HttpMethod.POST, "/api/v1/chat-interno/conversas/" + grupoId + "/mensagens",
                "{\"conteudo\":\"ola grupo\"}", Map.class);

        ResponseEntity<Void> add = chamar(bruno, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + grupoId + "/participantes",
                "{\"usuarioId\":\"" + idAdmin + "\"}", Void.class);
        assertThat(add.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<Map<String, Object>> msgsAdmin = mensagens(admin, grupoId);
        assertThat(msgsAdmin).anySatisfy(m -> assertThat(m.get("conteudo")).isEqualTo("ola grupo"));
        assertThat(msgsAdmin).anySatisfy(m ->
                assertThat(m.get("conteudo").toString()).contains("PARTICIPANTE_ADICIONADO"));

        ResponseEntity<Void> rename = chamar(bruno, HttpMethod.PUT,
                "/api/v1/chat-interno/conversas/" + grupoId + "/nome",
                "{\"nome\":\"Ops Vidro 2\"}", Void.class);
        assertThat(rename.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listaNome(bruno, grupoId)).isEqualTo("Ops Vidro 2");
        assertThat(mensagens(bruno, grupoId)).anySatisfy(m ->
                assertThat(m.get("conteudo").toString()).contains("NOME_ALTERADO"));

        ResponseEntity<Void> removeCriador = chamar(bruno, HttpMethod.DELETE,
                "/api/v1/chat-interno/conversas/" + grupoId + "/participantes/" + idAna,
                null, Void.class);
        assertThat(removeCriador.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listaContem(ana, grupoId)).isFalse();

        chamar(bruno, HttpMethod.POST, "/api/v1/chat-interno/conversas/" + grupoId + "/mensagens",
                "{\"conteudo\":\"depois da remocao\"}", Map.class);
        ResponseEntity<Map> anaLe = chamar(ana, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + grupoId + "/mensagens", null, Map.class);
        assertThat(anaLe.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Void> sair = chamar(gestor, HttpMethod.DELETE,
                "/api/v1/chat-interno/conversas/" + grupoId + "/participantes/" + idGestor,
                null, Void.class);
        assertThat(sair.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(listaContem(gestor, grupoId)).isFalse();
        assertThat(mensagens(bruno, grupoId)).anySatisfy(m ->
                assertThat(m.get("conteudo").toString()).contains("PARTICIPANTE_SAIU"));

        ResponseEntity<Void> anaTentaAdd = chamar(ana, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + grupoId + "/participantes",
                "{\"usuarioId\":\"" + idAna + "\"}", Void.class);
        assertThat(anaTentaAdd.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> direta = chamar(ana, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/direta",
                "{\"usuarioId\":\"" + idBruno + "\"}", Map.class);
        String diretaId = direta.getBody().get("id").toString();
        ResponseEntity<Void> terceiro = chamar(ana, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/" + diretaId + "/participantes",
                "{\"usuarioId\":\"" + idGestor + "\"}", Void.class);
        assertThat(terceiro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> direta2 = chamar(bruno, HttpMethod.POST,
                "/api/v1/chat-interno/conversas/direta",
                "{\"usuarioId\":\"" + idAna + "\"}", Map.class);
        assertThat(direta2.getBody().get("id").toString()).isEqualTo(diretaId);
        assertThat(listaNome(ana, diretaId)).contains("Bruno");
    }

    private UUID idDo(String email) {
        return db.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    @SuppressWarnings("unchecked")
    private boolean listaContem(Tokens quem, String conversaId) {
        ResponseEntity<List> lista = chamar(quem, HttpMethod.GET, "/api/v1/chat-interno/conversas",
                null, List.class);
        assertThat(lista.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> body = lista.getBody();
        return body.stream()
                .map(o -> o.get("id").toString())
                .anyMatch(conversaId::equals);
    }

    @SuppressWarnings("unchecked")
    private String listaNome(Tokens quem, String conversaId) {
        ResponseEntity<List> lista = chamar(quem, HttpMethod.GET, "/api/v1/chat-interno/conversas",
                null, List.class);
        List<Map<String, Object>> body = lista.getBody();
        return body.stream()
                .filter(m -> conversaId.equals(m.get("id").toString()))
                .map(m -> m.get("participantes").toString())
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mensagens(Tokens quem, String conversaId) {
        ResponseEntity<Map> resp = chamar(quem, HttpMethod.GET,
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens", null, Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) resp.getBody().get("mensagens");
    }

    private <T> ResponseEntity<T> chamar(Tokens tokens, HttpMethod metodo, String url, String corpo,
            Class<T> tipo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(tokens.accessToken());
        if (corpo != null) {
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), tipo);
    }
}
