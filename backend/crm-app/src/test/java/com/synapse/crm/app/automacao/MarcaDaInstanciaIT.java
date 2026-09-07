package com.synapse.crm.app.automacao;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.midia.ArmazenamentoDeMidiaFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/** Contrato ponta a ponta da marca editavel, sem reiniciar a aplicacao. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class MarcaDaInstanciaIT extends PostgresIT {

    private static final byte[] PNG_VALIDO = concatenar(
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, new byte[32]);

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ArmazenamentoDeMidiaFake armazenamento;

    @BeforeEach
    void restaurarFallback() {
        armazenamento.limpar();
        jdbc.update(
                "UPDATE marca_da_instancia SET tema = NULL, logo_referencia_storage = NULL, "
                        + "nome_da_marca = NULL, subtitulo = NULL, atualizado_por_id = NULL, "
                        + "atualizado_em = NULL WHERE id = 1");
    }

    @Test
    @DisplayName("linha NULL/NULL preserva o tema do classpath")
    void temaSemCustomizacao_eIgualAoClasspath() throws Exception {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/config/tema", String.class);
        JsonNode esperado = json.readTree(new ClassPathResource("tema.json").getInputStream());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resposta.getBody())).isEqualTo(esperado);
    }

    @Test
    @DisplayName("linha NULL/NULL preserva os bytes da logo do classpath")
    void logoSemCustomizacao_eIgualAoClasspath() throws Exception {
        ResponseEntity<byte[]> resposta = http.getForEntity("/api/v1/config/logo", byte[].class);
        byte[] esperado = new ClassPathResource("logo.png").getInputStream().readAllBytes();

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(resposta.getBody()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("identidade NULL/NULL preserva o catalogo completo do classpath")
    void textosSemCustomizacao_eIgualAoClasspath() throws Exception {
        ResponseEntity<String> resposta = http.getForEntity("/api/v1/config/textos", String.class);
        JsonNode esperado = json.readTree(new ClassPathResource("textos.json").getInputStream());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody().getBytes(StandardCharsets.UTF_8))
                .isEqualTo(json.writeValueAsBytes(esperado));
    }

    @Test
    @DisplayName("PUT de tema aparece no GET seguinte sem reiniciar")
    void atualizarTema_refleteSemReiniciar() throws Exception {
        HttpHeaders cabecalhos = cabecalhosDoGestor(MediaType.APPLICATION_JSON);
        String corpo = "{\"corPrimaria\":\"#abcdef\",\"logoUrl\":\"/api/v1/config/logo\"}";

        ResponseEntity<String> atualizacao = http.exchange(
                "/api/v1/config/marca/tema",
                HttpMethod.PUT,
                new HttpEntity<>(corpo, cabecalhos),
                String.class);
        ResponseEntity<String> leitura = http.getForEntity("/api/v1/config/tema", String.class);

        assertThat(atualizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(leitura.getBody()).path("corPrimaria").asText()).isEqualTo("#abcdef");
    }

    @Test
    @DisplayName("upload de logo troca os bytes e remove a referencia antiga")
    void atualizarLogo_trocaStorageEServeNovoArquivo() {
        String antiga = armazenamento.salvar("logo-antiga".getBytes(StandardCharsets.UTF_8), "logo-antiga.png", "image/png");
        jdbc.update("UPDATE marca_da_instancia SET logo_referencia_storage = ? WHERE id = 1", antiga);

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new ByteArrayResource(PNG_VALIDO) {
            @Override
            public String getFilename() {
                return "marca.png";
            }
        });
        HttpHeaders cabecalhos = cabecalhosDoGestor(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Void> atualizacao = http.exchange(
                "/api/v1/config/marca/logo",
                HttpMethod.PUT,
                new HttpEntity<>(corpo, cabecalhos),
                Void.class);
        ResponseEntity<byte[]> leitura = http.getForEntity("/api/v1/config/logo", byte[].class);

        assertThat(atualizacao.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(leitura.getBody()).isEqualTo(PNG_VALIDO);
        assertThat(armazenamento.foiRemovida(antiga)).isTrue();
    }

    @Test
    @DisplayName("PUT de identidade sobrepoe dois campos sem reiniciar nem congelar o catalogo")
    void atualizarIdentidade_refleteSemReiniciarESemMutarClasspath() throws Exception {
        JsonNode classpath = json.readTree(new ClassPathResource("textos.json").getInputStream());
        HttpHeaders cabecalhos = cabecalhosDoGestor(MediaType.APPLICATION_JSON);
        String corpo = "{\"marca\":\"Clínica Femina\",\"subtitulo\":\"Atendimento especializado\"}";

        ResponseEntity<String> atualizacao = http.exchange(
                "/api/v1/config/marca/identidade",
                HttpMethod.PUT,
                new HttpEntity<>(corpo, cabecalhos),
                String.class);
        ResponseEntity<String> primeiraLeitura = http.getForEntity("/api/v1/config/textos", String.class);
        ResponseEntity<String> segundaLeitura = http.getForEntity("/api/v1/config/textos", String.class);

        assertThat(atualizacao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(primeiraLeitura.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segundaLeitura.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode esperado = classpath.deepCopy();
        ObjectNode appEsperado = (ObjectNode) esperado.path("app");
        appEsperado.put("marca", "Clínica Femina");
        appEsperado.put("subtitulo", "Atendimento especializado");
        assertThat(json.readTree(primeiraLeitura.getBody())).isEqualTo(esperado);
        assertThat(json.readTree(segundaLeitura.getBody())).isEqualTo(esperado);
        assertThat(json.readTree(primeiraLeitura.getBody()).path("app").path("nome").asText())
                .isEqualTo("Synapse CRM");
        assertThat(json.readTree(primeiraLeitura.getBody()).path("menu"))
                .isEqualTo(classpath.path("menu"));
    }

    @Test
    @DisplayName("escrita de marca sem autenticacao e rejeitada")
    void escritaSemAutenticacao_devolve401() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/config/marca/tema",
                HttpMethod.PUT,
                new HttpEntity<>("{}", new HttpHeaders()),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("atendente nao pode escrever marca, como nos PUTs da automacao")
    void atendente_naoPodeEscreverMarca() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken());
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/config/marca/tema",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("corPrimaria", "#abcdef"), cabecalhos),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("identidade sem autenticacao e rejeitada")
    void identidadeSemAutenticacao_devolve401() {
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/config/marca/identidade",
                HttpMethod.PUT,
                new HttpEntity<>("{\"marca\":\"Clinica\",\"subtitulo\":\"CRM\"}", new HttpHeaders()),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("atendente nao pode atualizar identidade")
    void identidade_atendenteRecebe403() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken());
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/config/marca/identidade",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("marca", "Clinica", "subtitulo", "CRM"), cabecalhos),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("identidade exige os dois campos nao vazios")
    void identidade_vaziaDevolve400() {
        HttpHeaders cabecalhos = cabecalhosDoGestor(MediaType.APPLICATION_JSON);

        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/config/marca/identidade",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("marca", " ", "subtitulo", "CRM"), cabecalhos),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private HttpHeaders cabecalhosDoGestor(MediaType tipo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken());
        cabecalhos.setContentType(tipo);
        return cabecalhos;
    }

    private static byte[] concatenar(byte[] a, byte[] b) {
        byte[] resultado = new byte[a.length + b.length];
        System.arraycopy(a, 0, resultado, 0, a.length);
        System.arraycopy(b, 0, resultado, a.length, b.length);
        return resultado;
    }
}
