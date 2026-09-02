package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import com.synapse.crm.app.avatar.ArmazenamentoDeFotoDeLeadFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato HTTP, storage reprocessado, precedencia e isolamento da foto de perfil do lead. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=token-foto-lead-e97")
class FotoDoLeadIT extends PostgresIT {

    private static final String TOKEN = "token-foto-lead-e97";
    private static final String FOTO_LEGADA = "https://cdn.example/foto-legada.webp";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ArmazenamentoDeFotoDeLeadFake armazenamento;

    private final List<UUID> leads = new ArrayList<>();
    private final List<UUID> atendimentos = new ArrayList<>();
    private UUID leadDaAna;
    private UUID leadDoBruno;

    @BeforeEach
    void preparar() {
        armazenamento.limpar();
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = '5' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");
        leadDaAna = criarLead("Foto E97 Ana", usuario(EMAIL_ANA));
        leadDoBruno = criarLead("Foto E97 Bruno", usuario(EMAIL_BRUNO));
        criarAtendimento(leadDaAna, usuario(EMAIL_ANA));
    }

    @AfterEach
    void limpar() {
        atendimentos.forEach(id -> jdbc.update("DELETE FROM atendimento WHERE id = ?", id));
        leads.forEach(id -> jdbc.update("DELETE FROM lead WHERE id = ?", id));
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = '5' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");
        armazenamento.limpar();
    }

    @Test
    @DisplayName("POST reprocessa, hash evita escrita e DELETE e idempotente")
    void cicloCompleto_reprocessaEDevolveEstadosContratados() throws Exception {
        byte[] original = imagemPng(320, 180);

        ResponseEntity<String> atualizada = enviar(leadDaAna, original, cabecalhosInternos());

        assertThat(atualizada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(atualizada.getBody()).path("status").asText()).isEqualTo("ATUALIZADA");
        String referencia = jdbc.queryForObject(
                "SELECT foto_referencia FROM lead WHERE id = ?", String.class, leadDaAna);
        String hash = jdbc.queryForObject("SELECT foto_hash FROM lead WHERE id = ?", String.class, leadDaAna);
        Instant atualizadaEm = jdbc.queryForObject(
                "SELECT foto_atualizada_em FROM lead WHERE id = ?", Instant.class, leadDaAna);
        assertThat(referencia).startsWith("lead/").endsWith(".png");
        assertThat(hash).hasSize(64);
        assertThat(atualizadaEm).isNotNull();
        assertThat(armazenamento.salvamentos()).isEqualTo(1);

        var armazenada = armazenamento.unicoArquivo();
        assertThat(armazenada.mimetype()).isEqualTo("image/png");
        assertThat(armazenada.conteudo()).isNotEqualTo(original);
        BufferedImage pronta = ImageIO.read(new ByteArrayInputStream(armazenada.conteudo()));
        assertThat(pronta.getWidth()).isEqualTo(256);
        assertThat(pronta.getHeight()).isEqualTo(256);

        ResponseEntity<String> inalterada = enviar(leadDaAna, original, cabecalhosInternos());
        assertThat(json.readTree(inalterada.getBody()).path("status").asText()).isEqualTo("INALTERADA");
        assertThat(armazenamento.salvamentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT foto_atualizada_em FROM lead WHERE id = ?", Instant.class, leadDaAna))
                .isEqualTo(atualizadaEm);

        String caminho = "/api/v1/leads/" + leadDaAna + "/foto";
        assertThat(fichaComoAna().path("fotoUrl").asText()).isEqualTo(caminho);
        assertThat(fotoNaInboxComoAna()).isEqualTo(caminho);
        ResponseEntity<byte[]> download = chamarComJwt(
                EMAIL_ANA, HttpMethod.GET, caminho, null, byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(download.getHeaders().getCacheControl()).contains("no-cache");
        assertThat(download.getBody()).isEqualTo(armazenada.conteudo());

        ResponseEntity<String> removida = remover(leadDaAna, cabecalhosInternos());
        ResponseEntity<String> repetida = remover(leadDaAna, cabecalhosInternos());
        assertThat(json.readTree(removida.getBody()).path("status").asText()).isEqualTo("REMOVIDA");
        assertThat(json.readTree(repetida.getBody()).path("status").asText()).isEqualTo("REMOVIDA");
        assertThat(armazenamento.remocoes()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM lead WHERE id = ? AND foto_referencia IS NULL AND foto_hash IS NULL AND foto_atualizada_em IS NULL",
                        Long.class,
                        leadDaAna))
                .isEqualTo(1L);
        assertThat(fichaComoAna().path("fotoUrl").asText()).isEqualTo(FOTO_LEGADA);
    }

    @Test
    @DisplayName("endpoint interno exige X-Synapse-Token e nao aceita JWT humano")
    void autenticacaoInterna_recusaAusenteEJwtHumano() throws IOException {
        byte[] original = imagemPng(32, 32);

        ResponseEntity<String> ausente = enviar(leadDaAna, original, new HttpHeaders());
        HttpHeaders jwt = new HttpHeaders();
        jwt.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken());
        ResponseEntity<String> humano = enviar(leadDaAna, original, jwt);

        assertThat(ausente.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(humano.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(armazenamento.salvamentos()).isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT foto_referencia FROM lead WHERE id = ?", String.class, leadDaAna))
                .isNull();
    }

    @Test
    @DisplayName("lead inexistente da 404 e conteudo invalido da 422")
    void validacao_retornaErrosDoContrato() throws IOException {
        ResponseEntity<String> inexistente = enviar(
                UUID.randomUUID(), imagemPng(32, 32), cabecalhosInternos());
        ResponseEntity<String> invalida = enviar(
                leadDaAna, "nao e imagem".getBytes(), cabecalhosInternos());

        assertThat(inexistente.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(invalida.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(invalida.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(armazenamento.salvamentos()).isZero();
    }

    @Test
    @DisplayName("arquivo acima do limite configurado da 413 antes de tocar o storage")
    void limiteConfigurado_retorna413() {
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = '1' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");

        ResponseEntity<String> resposta = enviar(
                leadDaAna, new byte[1024 * 1024 + 1], cabecalhosInternos());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(armazenamento.salvamentos()).isZero();
    }

    @Test
    @DisplayName("download exige JWT e respeita visibilidade do lead")
    void download_respeitaAutenticacaoERls() throws IOException {
        enviar(leadDaAna, imagemPng(32, 32), cabecalhosInternos());
        String caminho = "/api/v1/leads/" + leadDaAna + "/foto";

        ResponseEntity<byte[]> semJwt = http.exchange(
                caminho, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
        ResponseEntity<byte[]> colega = chamarComJwt(
                EMAIL_BRUNO, HttpMethod.GET, caminho, null, byte[].class);
        ResponseEntity<byte[]> semFoto = chamarComJwt(
                EMAIL_BRUNO,
                HttpMethod.GET,
                "/api/v1/leads/" + leadDoBruno + "/foto",
                null,
                byte[].class);

        assertThat(semJwt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(colega.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(semFoto.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<String> enviar(UUID leadId, byte[] conteudo, HttpHeaders cabecalhos) {
        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return "foto-original.png";
            }
        });
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.exchange(
                "/internal/v1/leads/" + leadId + "/foto",
                HttpMethod.POST,
                new HttpEntity<>(corpo, cabecalhos),
                String.class);
    }

    private ResponseEntity<String> remover(UUID leadId, HttpHeaders cabecalhos) {
        return http.exchange(
                "/internal/v1/leads/" + leadId + "/foto",
                HttpMethod.DELETE,
                new HttpEntity<>(cabecalhos),
                String.class);
    }

    private HttpHeaders cabecalhosInternos() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", TOKEN);
        return cabecalhos;
    }

    private JsonNode fichaComoAna() throws Exception {
        return json.readTree(chamarComJwt(
                        EMAIL_ANA,
                        HttpMethod.GET,
                        "/api/v1/leads/" + leadDaAna,
                        null,
                        String.class)
                .getBody());
    }

    private String fotoNaInboxComoAna() throws Exception {
        JsonNode resposta = json.readTree(chamarComJwt(
                        EMAIL_GESTOR,
                        HttpMethod.GET,
                        "/api/v1/atendimentos/inbox?visao=TODOS&limite=50",
                        null,
                        String.class,
                        SENHA_GESTOR)
                .getBody());
        return java.util.stream.StreamSupport.stream(resposta.path("itens").spliterator(), false)
                .filter(item -> leadDaAna.toString().equals(item.path("leadId").asText()))
                .findFirst()
                .orElseThrow()
                .path("leadFotoUrl")
                .asText();
    }

    private <T> ResponseEntity<T> chamarComJwt(
            String email, HttpMethod metodo, String caminho, Object corpo, Class<T> tipo) {
        return chamarComJwt(email, metodo, caminho, corpo, tipo, SENHA_ATENDENTE);
    }

    private <T> ResponseEntity<T> chamarComJwt(
            String email, HttpMethod metodo, String caminho, Object corpo, Class<T> tipo, String senha) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, email, senha).accessToken());
        if (corpo != null) {
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        }
        return http.exchange(caminho, metodo, new HttpEntity<>(corpo, cabecalhos), tipo);
    }

    private UUID criarLead(String nome, UUID dono) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,foto_url,atendente_responsavel_id,status_basico) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                id,
                nome,
                FOTO_LEGADA,
                dono);
        leads.add(id);
        return id;
    }

    private void criarAtendimento(UUID leadId, UUID dono) {
        UUID id = UUID.randomUUID();
        UUID canal = jdbc.queryForObject("SELECT id FROM canal ORDER BY id LIMIT 1", UUID.class);
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status) VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO')",
                id,
                leadId,
                canal,
                dono);
        atendimentos.add(id);
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private static byte[] imagemPng(int largura, int altura) throws IOException {
        BufferedImage imagem = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        imagem.setRGB(0, 0, Color.MAGENTA.getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", bytes);
        return bytes.toByteArray();
    }
}
