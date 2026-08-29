package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao.Tokens;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "synapse.midia.s3.bucket=test-bucket",
        "synapse.midia.limites.imagem=100", // limite pequeno para teste de 100 bytes
        "synapse.midia.limites.audio=1000",
        "synapse.midia.limites.video=1000",
        "synapse.midia.limites.documento=1000"
})
class ChatInternoMidiaIT extends PostgresIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate db;

    @Autowired
    ObjectMapper mapper;

    @Test
    @DisplayName("Deve permitir upload de midia valida no chat interno e gerar a url assinada")
    void devePermitirUploadMidia() {
        var authAna = login(rest, EMAIL_ANA, SENHA_ATENDENTE);
        var authBruno = login(rest, EMAIL_BRUNO, SENHA_ATENDENTE);

        // 1. Ana lista contatos e acha o Bruno
        ResponseEntity<List> reqContatos = chamadaAutenticada(rest, "/api/v1/chat-interno/contatos", HttpMethod.GET, authAna, null, List.class);
        assertThat(reqContatos.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> contatos = reqContatos.getBody();
        Map<String, Object> bruno = contatos.stream().filter(c -> c.get("nome").toString().contains("Bruno")).findFirst().orElseThrow();
        assertThat(bruno).containsKey("presenca");
        String brunoId = bruno.get("id").toString();

        // 2. Ana abre conversa com Bruno
        String payload = "{\"usuarioId\": \"" + brunoId + "\"}";
        ResponseEntity<Map> resConversa = chamadaAutenticada(rest, "/api/v1/chat-interno/conversas/direta", HttpMethod.POST, authAna, payload, Map.class);
        String conversaId = resConversa.getBody().get("id").toString();
        Long mensagensAntes = contarMensagens(conversaId);

        // 3. Ana envia imagem fake (bytes de magic png limitados a menos de 100 bytes)
        byte[] imagemValida = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // magic bytes
                0, 0, 0, 13, // IHDR length
                0x49, 0x48, 0x44, 0x52 // IHDR chunk type
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authAna.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("arquivo", new ByteArrayResource(imagemValida) {
            @Override
            public String getFilename() {
                return "teste.png";
            }
        });
        body.add("legenda", "Minha foto");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens/midia",
                HttpMethod.POST,
                requestEntity,
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> mensagem = response.getBody();
        assertThat(mensagem.get("tipo")).isEqualTo("IMAGEM");
        assertThat(mensagem.get("conteudo")).isEqualTo("Minha foto"); // legenda mapeada pra conteudo
        assertThat(mensagem.get("midiaUrl")).asString().contains("token="); // deve ser assinada no fake storage

        // 4. Gestor tenta enviar midia pra essa conversa, mas nao participa -> 403
        var authGestor = login(rest, EMAIL_GESTOR, SENHA_GESTOR);
        HttpHeaders headersGestor = new HttpHeaders();
        headersGestor.setBearerAuth(authGestor.accessToken());
        headersGestor.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> reqGestor = new HttpEntity<>(body, headersGestor);
        ResponseEntity<Map> respGestor = rest.exchange("/api/v1/chat-interno/conversas/" + conversaId + "/mensagens/midia", HttpMethod.POST, reqGestor, Map.class);
        assertThat(respGestor.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 5. Ana tenta enviar um arquivo de video ou executavel nao permitido
        byte[] arquivoFalso = new byte[] {0x00, 0x00, 0x01, (byte) 0xBA, 0x21, 0x00, 0x01, 0x00}; // mpeg video header fake
        MultiValueMap<String, Object> bodyFalso = new LinkedMultiValueMap<>();
        bodyFalso.add("arquivo", new ByteArrayResource(arquivoFalso) {
            @Override
            public String getFilename() {
                return "virus.png";
            }
        });
        HttpEntity<MultiValueMap<String, Object>> reqFalso = new HttpEntity<>(bodyFalso, headers);
        ResponseEntity<Map> respFalso = rest.exchange("/api/v1/chat-interno/conversas/" + conversaId + "/mensagens/midia", HttpMethod.POST, reqFalso, Map.class);
        assertThat(respFalso.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(contarMensagens(conversaId)).isEqualTo(mensagensAntes + 1);
    }

    @Test
    @DisplayName("gravação ISO-BMFF audio-only rotulada como QuickTime e aceita no chat interno")
    void upload_quicktimeAudioOnly_eAceito() {
        var authAna = login(rest, EMAIL_ANA, SENHA_ATENDENTE);

        ResponseEntity<List> reqContatos = chamadaAutenticada(rest, "/api/v1/chat-interno/contatos", HttpMethod.GET, authAna, null, List.class);
        List<Map<String, Object>> contatos = reqContatos.getBody();
        String brunoId = contatos.stream().filter(c -> c.get("nome").toString().contains("Bruno")).findFirst().orElseThrow().get("id").toString();

        String payload = "{\"usuarioId\": \"" + brunoId + "\"}";
        ResponseEntity<Map> resConversa = chamadaAutenticada(rest, "/api/v1/chat-interno/conversas/direta", HttpMethod.POST, authAna, payload, Map.class);
        String conversaId = resConversa.getBody().get("id").toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authAna.accessToken());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("arquivo", new ByteArrayResource(AUDIO_ONLY_QUICKTIME) {
            @Override
            public String getFilename() {
                return "gravacao.m4a";
            }
        });
        ResponseEntity<Map> response = rest.exchange(
                "/api/v1/chat-interno/conversas/" + conversaId + "/mensagens/midia",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("tipo")).isEqualTo("AUDIO");
    }

    private static final byte[] AUDIO_ONLY_QUICKTIME = concatenar(
            box("ftyp", concatenar("qt  ".getBytes(StandardCharsets.US_ASCII), "qt  ".getBytes(StandardCharsets.US_ASCII))),
            box("moov", box("trak", box("mdia", box("hdlr", concatenar(new byte[8], "soun".getBytes(StandardCharsets.US_ASCII)))))));

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] resultado = new byte[a.length + b.length];
        System.arraycopy(a, 0, resultado, 0, a.length);
        System.arraycopy(b, 0, resultado, a.length, b.length);
        return resultado;
    }

    private static byte[] box(String tipo, byte[] payload) {
        byte[] resultado = new byte[8 + payload.length];
        int tamanho = resultado.length;
        resultado[0] = (byte) (tamanho >>> 24);
        resultado[1] = (byte) (tamanho >>> 16);
        resultado[2] = (byte) (tamanho >>> 8);
        resultado[3] = (byte) tamanho;
        byte[] nome = tipo.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(nome, 0, resultado, 4, 4);
        System.arraycopy(payload, 0, resultado, 8, payload.length);
        return resultado;
    }

    private Long contarMensagens(String conversaId) {
        return db.queryForObject(
                "select count(*) from chat_interno_mensagem where conversa_id = ?",
                Long.class,
                UUID.fromString(conversaId));
    }

    private <T> ResponseEntity<T> chamadaAutenticada(
            TestRestTemplate http,
            String url,
            HttpMethod metodo,
            Tokens tokens,
            Object body,
            Class<T> tipo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.accessToken());

        if (body instanceof MultiValueMap) {
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        } else if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        return http.exchange(url, metodo, new HttpEntity<>(body, headers), tipo);
    }
}
