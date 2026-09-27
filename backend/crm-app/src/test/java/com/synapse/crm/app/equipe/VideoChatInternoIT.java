package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao.Tokens;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class VideoChatInternoIT extends PostgresIT {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;

    @ParameterizedTest
    @ValueSource(strings = {"direta", "grupo"})
    void videoRealComLegendaPersisteBaixaERepeticaoNaoDuplica(String tipo) throws Exception {
        var ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, tipo);
        byte[] video = video();
        String chave = UUID.randomUUID().toString();
        long antes = quantidade(conversa);
        var resposta = enviar(ana, conversa, video, "video.mp4", chave);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resposta.getBody()).containsEntry("tipo", "VIDEO").containsEntry("conteudo", "Legenda\ncom acento 💬");
        String mensagem = resposta.getBody().get("id").toString();
        assertThat(enviar(ana, conversa, video, "video.mp4", chave).getBody()).containsEntry("id", mensagem);
        assertThat(quantidade(conversa)).isEqualTo(antes + 1);
        var headers = new HttpHeaders(); headers.setBearerAuth(ana.accessToken());
        var arquivo = http.exchange("/api/v1/chat-interno/conversas/" + conversa + "/midias/" + mensagem + "/arquivo", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        assertThat(arquivo.getBody()).containsExactly(video);
        assertThat(arquivo.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType("video/mp4"));
        assertThat(arquivo.getHeaders().getContentDisposition().getFilename()).isEqualTo("video.mp4");
        UUID destino = conversa(ana, "grupo");
        var encaminharHeaders = new HttpHeaders();
        encaminharHeaders.setBearerAuth(ana.accessToken());
        encaminharHeaders.setContentType(MediaType.APPLICATION_JSON);
        encaminharHeaders.set("Idempotency-Key", UUID.randomUUID().toString());
        String rota = "/api/v1/chat-interno/conversas/" + conversa + "/mensagens/" + mensagem + "/encaminhar";
        var pedido = new HttpEntity<>(Map.of("conversaDestinoId", destino), encaminharHeaders);
        var copia = http.exchange(rota, HttpMethod.POST, pedido, Map.class);
        assertThat(copia.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String copiaId = copia.getBody().get("id").toString();
        assertThat(http.exchange(rota, HttpMethod.POST, pedido, Map.class).getBody()).containsEntry("id", copiaId);
        assertThat(copia.getBody()).containsEntry("tipo", "VIDEO").containsEntry("conteudo", "Legenda\ncom acento 💬");
        assertThat(db.queryForObject("SELECT midia_url FROM chat_interno_mensagem WHERE id = ?", String.class, UUID.fromString(copiaId)))
                .isEqualTo(db.queryForObject("SELECT midia_url FROM chat_interno_mensagem WHERE id = ?", String.class, UUID.fromString(mensagem)));
        assertThat(http.exchange("/api/v1/chat-interno/conversas/" + destino + "/midias/" + copiaId + "/arquivo", HttpMethod.GET, new HttpEntity<>(headers), byte[].class).getBody()).containsExactly(video);
        var historico = http.exchange("/api/v1/chat-interno/conversas/" + conversa + "/mensagens", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(historico.getBody()).contains(mensagem, "VIDEO", "Legenda");
        var gestor = login(http, EMAIL_GESTOR, SENHA_GESTOR);
        assertThat(enviar(gestor, conversa, video, "video.mp4", UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"M4A ", "qt  ", "xxxx"})
    void marcaNaoPermitidaMesmoComTrilhaDeVideoERecusada(String marca) throws Exception {
        var ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, "direta");
        long antes = quantidade(conversa);
        byte[] bytes = video();
        System.arraycopy(marca.getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
        assertThat(enviar(ana, conversa, bytes, "gravacao.m4a", UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(quantidade(conversa)).isEqualTo(antes);
    }

    @Test
    void bytesDisfarcadosDeVideoNaoCriamMensagem() {
        var ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
        UUID conversa = conversa(ana, "direta");
        long antes = quantidade(conversa);
        assertThat(enviar(ana, conversa, new byte[]{0, 1, 2, 3}, "video.mp4", UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(quantidade(conversa)).isEqualTo(antes);
    }

    @Test
    void tamanhoConfiguradoRecusaAntesDePersistir() throws Exception {
        String chave = "anexo.tamanho_maximo_video_mb";
        var anterior = db.queryForList("SELECT valor FROM configuracao_automacao WHERE chave = ?", String.class, chave);
        db.update("INSERT INTO configuracao_automacao(chave,valor,tipo) VALUES (?,'1','INT') ON CONFLICT(chave) DO UPDATE SET valor = '1'", chave);
        try {
            var ana = login(http, EMAIL_ANA, SENHA_ATENDENTE);
            UUID conversa = conversa(ana, "direta");
            long antes = quantidade(conversa);
            byte[] original = video();
            var bytes = ByteBuffer.allocate(original.length + 2 * 1024 * 1024);
            bytes.put(original).putInt(2 * 1024 * 1024).put("free".getBytes(StandardCharsets.US_ASCII));
            assertThat(enviar(ana, conversa, bytes.array(), "video.mp4", UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(quantidade(conversa)).isEqualTo(antes);
        } finally {
            if (anterior.isEmpty()) db.update("DELETE FROM configuracao_automacao WHERE chave = ?", chave);
            else db.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", anterior.getFirst(), chave);
        }
    }

    private byte[] video() throws Exception {
        try (var arquivo = getClass().getResourceAsStream("/midia/chat-interno/video-validacao.mp4")) {
            return java.util.Objects.requireNonNull(arquivo).readAllBytes();
        }
    }
    private long quantidade(UUID conversa) {
        return db.queryForObject("SELECT count(*) FROM chat_interno_mensagem WHERE conversa_id = ?", Long.class, conversa);
    }
    private UUID conversa(Tokens ana, String tipo) {
        UUID bruno = db.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_BRUNO);
        var headers = new HttpHeaders(); headers.setBearerAuth(ana.accessToken()); headers.setContentType(MediaType.APPLICATION_JSON);
        String corpo = tipo.equals("direta") ? "{\"usuarioId\":\"" + bruno + "\"}" : "{\"nome\":\"Vídeo interno\",\"participantes\":[\"" + bruno + "\"]}";
        return UUID.fromString(http.exchange("/api/v1/chat-interno/conversas/" + tipo, HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class).getBody().get("id").toString());
    }
    private ResponseEntity<Map> enviar(Tokens ana, UUID conversa, byte[] bytes, String nome, String chave) {
        var headers = new HttpHeaders(); headers.setBearerAuth(ana.accessToken()); headers.setContentType(MediaType.MULTIPART_FORM_DATA); headers.set("Idempotency-Key", chave);
        var corpo = new LinkedMultiValueMap<String, Object>();
        corpo.add("arquivo", new ByteArrayResource(bytes) { @Override public String getFilename() { return nome; } });
        corpo.add("legenda", "Legenda\ncom acento 💬");
        return http.exchange("/api/v1/chat-interno/conversas/" + conversa + "/mensagens/midia", HttpMethod.POST, new HttpEntity<>(corpo, headers), Map.class);
    }
}
