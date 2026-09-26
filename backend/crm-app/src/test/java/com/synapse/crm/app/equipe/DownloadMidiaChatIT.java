package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.midia.ArmazenamentoDeMidiaFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao.Tokens;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DownloadMidiaChatIT extends PostgresIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired ArmazenamentoDeMidiaFake storage;
    @Autowired CircuitBreakerRegistry breakers;

    @AfterEach
    void resetarBreakerDoFixture() {
        breakers.circuitBreaker("chat-interno-download").reset();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> arquivos() {
        return Stream.of("DIRETA", "GRUPO").flatMap(conversa -> Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(conversa, "IMAGEM", "image/png", "foto.png"),
                org.junit.jupiter.params.provider.Arguments.of(conversa, "AUDIO", "audio/ogg", "voz.ogg"),
                org.junit.jupiter.params.provider.Arguments.of(conversa, "VIDEO", "video/mp4", "filme.mp4"),
                org.junit.jupiter.params.provider.Arguments.of(conversa, "DOCUMENTO", "application/pdf", "orçamento.pdf")));
    }

    @ParameterizedTest
    @MethodSource("arquivos")
    void entregaBytesMimeNomeESemDependerDaUrlDoHistorico(String tipoConversa, String tipo, String mime, String nome) {
        Tokens ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, tipoConversa);
        byte[] bytes = ("arquivo-testado-" + tipo).getBytes(StandardCharsets.UTF_8);
        String referencia = storage.salvar(bytes, UUID.randomUUID().toString(), mime);
        UUID mensagem = mensagem(conversa, tipo, mime, nome, referencia);
        var expirada = storage.urlAssinada(referencia, java.time.Duration.ofSeconds(-1));
        assertThat(storage.baixarPelaUrlAssinada(expirada)).isEmpty();
        ResponseEntity<byte[]> resposta = chamar(ana, rota(conversa, mensagem), byte[].class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).containsExactly(bytes);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(mime));
        assertThat(resposta.getHeaders().getContentDisposition().getFilename()).isEqualTo(nome);
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");
        assertThat(chamar(ana, rota(conversa, mensagem), byte[].class).getBody()).containsExactly(bytes);
    }

    @Test
    void corsPermiteChaveDeUploadMasNaoOutraOrigem() {
        var headers = new HttpHeaders();
        headers.setOrigin("http://localhost:3000");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(java.util.List.of("Authorization", "Idempotency-Key"));
        String rota = "/api/v1/chat-interno/conversas/" + UUID.randomUUID() + "/mensagens/midia";
        var resposta = http.exchange(rota, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getAccessControlAllowHeaders()).contains("Idempotency-Key");
        assertThat(resposta.getHeaders().getAccessControlExposeHeaders()).contains("Content-Disposition");
        headers.setOrigin("https://outra-origem.invalid");
        assertThat(http.exchange(rota, HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void gestorEAdministradorForaDaConversaNaoLeemHistoricoNemArquivo() {
        Tokens ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, "GRUPO");
        UUID mensagem = mensagem(conversa, "AUDIO", "audio/ogg", "voz.ogg", storage.salvar(new byte[]{1, 2, 3}, "voz", "audio/ogg"));
        for (Tokens proibido : new Tokens[]{login(http, EMAIL_GESTOR, SENHA_GESTOR), login(http, EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR)}) {
            for (String rota : new String[]{rota(conversa, mensagem), "/api/v1/chat-interno/conversas/" + conversa + "/mensagens", "/api/v1/chat-interno/conversas/" + conversa + "/midias/" + mensagem + "/url"}) {
                var resposta = chamar(proibido, rota, String.class);
                assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(resposta.getBody()).doesNotContain("voz.ogg", "fake/", "token=");
            }
        }
    }

    @Test
    void origemDeOutraConversaRemovidaEArquivoAusenteNaoVazam() {
        Tokens ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, "GRUPO");
        UUID outra = conversa(ana, "GRUPO");
        String referencia = storage.salvar(new byte[]{4, 5}, "arquivo", "image/png");
        UUID mensagem = mensagem(conversa, "IMAGEM", "image/png", "foto.png", referencia);
        assertThat(chamar(ana, rota(outra, mensagem), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        storage.remover(referencia);
        var ausente = chamar(ana, rota(conversa, mensagem), String.class);
        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ausente.getBody()).doesNotContain(referencia, "fake/", "token=");
        db.update("UPDATE chat_interno_mensagem SET removida_em = now(), conteudo = NULL, midia_url = NULL, midia_metadados = NULL WHERE id = ?", mensagem);
        assertThat(chamar(ana, rota(conversa, mensagem), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void breakerAbertoDegradaComErroSeguro() {
        Tokens ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, "GRUPO");
        UUID mensagem = mensagem(conversa, "IMAGEM", "image/png", "foto.png", storage.salvar(new byte[]{1}, "foto", "image/png"));
        breakers.circuitBreaker("chat-interno-download").transitionToOpenState();
        assertThat(chamar(ana, rota(conversa, mensagem), String.class).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void nomeNaoPermiteCaminhoControlesOuExtensaoDisfarcada() {
        Tokens ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, "GRUPO");
        UUID mensagem = mensagem(conversa, "IMAGEM", "image/png", "C:\\pasta\\foto.exe", storage.salvar(new byte[]{1}, "foto", "image/png"));
        assertThat(chamar(ana, rota(conversa, mensagem), byte[].class).getHeaders().getContentDisposition().getFilename()).isEqualTo("foto.png");
    }

    private UUID conversa(Tokens tokens, String tipo) {
        UUID bruno = db.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_BRUNO);
        String body = tipo.equals("DIRETA") ? "{\"usuarioId\":\"" + bruno + "\"}" : "{\"nome\":\"Teste mídia\",\"participantes\":[\"" + bruno + "\"]}";
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var resposta = http.exchange("/api/v1/chat-interno/conversas/" + (tipo.equals("DIRETA") ? "direta" : "grupo"), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        return UUID.fromString(resposta.getBody().get("id").toString());
    }

    private UUID mensagem(UUID conversa, String tipo, String mime, String nome, String referencia) {
        UUID id = UUID.randomUUID();
        UUID ana = db.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        db.update("INSERT INTO chat_interno_mensagem(id, conversa_id, remetente_id, tipo, midia_url, midia_metadados) VALUES (?, ?, ?, ?::tipo_mensagem, ?, jsonb_build_object('nome_original', ?, 'mimetype', ?, 'tamanho_bytes', 3))", id, conversa, ana, tipo, referencia, nome, mime);
        return id;
    }

    private String rota(UUID conversa, UUID mensagem) {
        return "/api/v1/chat-interno/conversas/" + conversa + "/midias/" + mensagem + "/arquivo";
    }

    private <T> ResponseEntity<T> chamar(Tokens tokens, String rota, Class<T> tipo) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());
        return http.exchange(rota, HttpMethod.GET, new HttpEntity<>(headers), tipo);
    }
}
