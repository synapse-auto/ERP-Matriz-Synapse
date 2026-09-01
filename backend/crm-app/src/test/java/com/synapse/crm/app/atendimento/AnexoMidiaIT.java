package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.awaitility.core.ConditionFactory;
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
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.midia.ArmazenamentoDeMidiaFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;

/**
 * Upload e download de anexo (E11b) ponta a ponta, sobre {@code provedor=fake} e um storage em
 * memoria — mesmo espirito de {@link com.synapse.crm.app.canal.CanalWhatsAppIT}. A expiracao de URL
 * assinada e propositalmente curta ({@code MIDIA_S3_EXPIRACAO_LEITURA}) so nesta suite, para o teste
 * de expiracao nao depender de esperar minutos.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.canal.webhook.intervalo-ms=3600000",
            "synapse.midia.expiracao-leitura=500ms"
        })
class AnexoMidiaIT extends PostgresIT {

    private static final String PREFIXO = "E11b-";
    private static final String TELEFONE = "5561977776666";

    // 8 bytes de assinatura PNG + preenchimento — suficiente para o Tika detectar image/png.
    private static final byte[] PNG_VALIDO = concatenar(
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, new byte[64]);

    // Cabecalho MZ de executavel — o Tika detecta como binario, nunca como imagem.
    private static final byte[] EXECUTAVEL_DISFARCADO =
            new byte[] {0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};

    /** Contêiner M4A com faixa AAC (ftyp M4A + moov + mdat), sem Content-Type forjado. */
    private static final byte[] M4A_AAC_VALIDO = concatenar(
            new byte[] {
                0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x4D, 0x34, 0x41, 0x20,
                0, 0, 0, 0, 0x4D, 0x34, 0x41, 0x20, 0x69, 0x73, 0x6F, 0x6D,
                0, 0, 0, 8, 0x6D, 0x6F, 0x6F, 0x76,
                0, 0, 0, 20, 0x6D, 0x64, 0x61, 0x74,
                (byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80, 0x02, 0x3F, (byte) 0xFC,
                0x21, 0x10, 0x04, 0x60, (byte) 0x8C, 0x1C, 0, 0, 0, 0
            },
            new byte[64]);

    /** MP4 com ftyp isom: o Tika o classifica como vídeo/quicktime, que continua proibido. */
    private static final byte[] VIDEO_MP4_REAL = new byte[] {
        0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D,
        0, 0, 2, 0, 0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32,
        0, 0, 0, 8, 0x6D, 0x6F, 0x6F, 0x76,
        0, 0, 0, 12, 0x6D, 0x64, 0x61, 0x74, 0, 0, 0, 0
    };

    /** QuickTime ISO-BMFF com uma trilha soun e sem trilha vide, semelhante ao MediaRecorder afetado. */
    private static final byte[] AUDIO_ONLY_QUICKTIME = concatenar(
            box("ftyp", concatenar("qt  ".getBytes(StandardCharsets.US_ASCII), "qt  ".getBytes(StandardCharsets.US_ASCII))),
            box("moov", box("trak", box("mdia", box("hdlr", concatenar(new byte[8], "soun".getBytes(StandardCharsets.US_ASCII)))))));

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PublicadorDaOutbox publicador;

    @Autowired
    private ProcessadorDeWebhookEntrada processador;

    @Autowired
    private CanalFake canal;

    @Autowired
    private ArmazenamentoDeMidiaFake armazenamento;

    private UUID idAna;
    private UUID leadDaAna;

    @BeforeEach
    void preparar() {
        canal.limpar();
        armazenamento.limpar();
        limpar();
        idAna = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        leadDaAna = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico,
                                  ultima_interacao_em, ultima_mensagem_do_lead_em)
                VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', now(), now())
                """,
                leadDaAna,
                PREFIXO + "Cliente da Ana",
                TELEFONE,
                idAna);
    }

    @AfterEach
    void restaurarConfiguracao() {
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = '5' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");
    }

    @Test
    @DisplayName("upload de imagem valida cria mensagem PENDENTE que vira ENVIADA pela outbox")
    void upload_imagemValida_pontaAPonta() {
        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", "vao 1");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"statusEntrega\":\"PENDENTE\"");
        assertThat(armazenamento.contagemDeObjetos()).isEqualTo(1);

        publicador.publicarPendentes();

        esperar().untilAsserted(() -> {
            assertThat(canal.enviados()).hasSize(1);
            assertThat(canal.enviados().get(0).conteudo())
                    .isInstanceOfSatisfying(
                            ConteudoDeEnvio.MensagemMidia.class,
                            midia -> assertThat(midia.legenda()).isEqualTo("*Ana Atendente:*\n\nvao 1"));
        });
    }

    @Test
    @DisplayName("M4A/AAC real e detectado como audio/mp4 e aceito")
    void upload_m4aAacReal_eAceito() {
        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, M4A_AAC_VALIDO, "gravacao.m4a", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"statusEntrega\":\"PENDENTE\"");
        assertThat(armazenamento.contagemDeObjetos()).isEqualTo(1);
        assertThat(armazenamento.ultimoMimetype()).isEqualTo("audio/mp4");
    }

    @Test
    @DisplayName("QuickTime audio-only e normalizado para audio/mp4 sem aceitar video")
    void upload_quicktimeAudioOnly_eAceito() {
        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, AUDIO_ONLY_QUICKTIME, "gravacao.m4a", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(armazenamento.ultimoMimetype()).isEqualTo("audio/mp4");
        publicador.publicarPendentes();
        esperar().untilAsserted(() -> {
            assertThat(canal.enviados()).hasSize(1);
            assertThat(canal.enviados().get(0).conteudo())
                    .isInstanceOfSatisfying(
                            ConteudoDeEnvio.MensagemMidia.class,
                            midia -> assertThat(midia.tipo()).isEqualTo(TipoMensagem.AUDIO));
        });
    }

    @Test
    @DisplayName("vídeo MP4 real continua recusado pela allowlist")
    void upload_videoReal_continuaRecusado() {
        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, VIDEO_MP4_REAL, "video.mp4", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(armazenamento.contagemDeObjetos()).isZero();
    }

    @Test
    @DisplayName("vídeo ISO-BMFF disfarçado de M4A continua recusado")
    void upload_videoDisfarcado_continuaRecusado() {
        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, VIDEO_MP4_REAL, "gravacao.m4a", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(armazenamento.contagemDeObjetos()).isZero();
    }

    @Test
    @DisplayName("extensao mentindo (.jpg que e executavel) e rejeitada pelo conteudo real")
    void upload_extensaoMinta_rejeitaPeloConteudoReal() {
        atendimentoDoLeadOuAbrir(leadDaAna); // abre a conversa; a mensagem "abrindo conversa" nao conta.
        int mensagensAntes = mensagensDoLead();

        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, EXECUTAVEL_DISFARCADO, "foto.jpg", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(armazenamento.contagemDeObjetos()).isZero();
        assertThat(mensagensDoLead()).isEqualTo(mensagensAntes);
    }

    @Test
    @DisplayName("arquivo acima do limite configurado e rejeitado antes de gravar no storage")
    void upload_acimaDoLimite_rejeitaAntesDoStorage() {
        jdbc.update(
                "UPDATE configuracao_automacao SET valor = '1' WHERE chave = 'anexo.tamanho_maximo_imagem_mb'");
        byte[] imagemGrande = concatenar(PNG_VALIDO, new byte[2 * 1024 * 1024]);

        ResponseEntity<String> resposta = enviarAnexo(leadDaAna, imagemGrande, "foto-grande.png", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(armazenamento.contagemDeObjetos()).isZero();
    }

    @Test
    @DisplayName("midia recebida via webhook e baixada e persistida no storage proprio")
    void webhookMidia_recebida_baixaEPersisteNoStorageProprio() {
        byte[] conteudoDoCliente = concatenar(PNG_VALIDO, new byte[16]);
        canal.programarMidiaRecebida(conteudoDoCliente, "image/png");

        String payload = payloadDeImagem("ext-midia-1", "meta-media-id-123");
        http.postForEntity(
                "/webhook/canal",
                new HttpEntity<>(payload, cabecalhosWebhook(CanalFake.ASSINATURA_VALIDA)),
                String.class);
        processador.processarPendentes();

        esperar().untilAsserted(() -> assertThat(mensagensDoLead()).isEqualTo(1));

        String corpo = mensagensComo(EMAIL_ANA, atendimentoDoLead()).getBody();
        assertThat(corpo).contains("\"tipo\":\"IMAGEM\"");
        // Prova que o historico nao depende da URL da Meta: a referencia e do storage proprio.
        assertThat(corpo).contains("fake-storage.local");
        assertThat(corpo).doesNotContain("graph.facebook.com");

        String midiaUrl = extrairMidiaUrl(corpo);
        assertThat(armazenamento.baixarPelaUrlAssinada(midiaUrl)).contains(conteudoDoCliente);
    }

    @Test
    @DisplayName("URL assinada expira e para de entregar o conteudo")
    void urlAssinada_expira_paraDeFuncionar() {
        enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", null);
        String corpo = mensagensComo(EMAIL_ANA, atendimentoDoLead()).getBody();
        String midiaUrl = extrairMidiaUrl(corpo);

        assertThat(armazenamento.baixarPelaUrlAssinada(midiaUrl)).isPresent();

        esperar().untilAsserted(() -> assertThat(armazenamento.baixarPelaUrlAssinada(midiaUrl)).isEmpty());
    }

    /**
     * O RLS cobre a mensagem; este teste prova que a URL assinada tambem so nasce para quem ja
     * alcanca a mensagem — um colega sem acesso nem recebe a URL, nao so recebe uma URL invalida.
     */
    @Test
    @DisplayName("atendente sem acesso ao atendimento nao recebe a URL do anexo do colega")
    void mensagens_deColega_naoExpoeAnexo() {
        enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", null);
        UUID atendimentoId = atendimentoDoLead();

        ResponseEntity<String> comoAna = mensagensComo(EMAIL_ANA, atendimentoId);
        ResponseEntity<String> comoBruno = mensagensComo(EMAIL_BRUNO, atendimentoId);

        assertThat(comoAna.getBody()).contains("fake-storage.local");
        // AtendimentoRepositorio.porId ja e RLS-scoped: "nao existe ou nao e seu" respondem
        // igual (404), antes mesmo de tocar em mensagem.midia_url — nenhuma URL chega a ser
        // assinada para quem nao alcanca o atendimento, muito menos devolvida.
        assertThat(comoBruno.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(comoBruno.getBody()).doesNotContain("fake-storage.local");
    }

    @Test
    @DisplayName("listagem de midias nao devolve caminho /api protegido")
    void listarMidias_naoExpoeUrlDeDownloadProtegida() {
        enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", null);

        ResponseEntity<String> resposta =
                autenticado(EMAIL_ANA, HttpMethod.GET, "/api/v1/leads/" + leadDaAna + "/midias");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).doesNotContain("/download");
        assertThat(resposta.getBody()).doesNotContain("urlDownload");
        assertThat(resposta.getBody()).doesNotContain("/api/v1/leads/" + leadDaAna + "/midias/");
    }

    @Test
    @DisplayName("URL emitida sob demanda resolve o arquivo certo")
    void emitirUrl_resolveArquivoCerto() {
        enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", null);
        UUID mensagemId = mensagemDeMidiaDoLead(leadDaAna);

        ResponseEntity<String> resposta = autenticado(
                EMAIL_ANA, HttpMethod.GET, "/api/v1/leads/" + leadDaAna + "/midias/" + mensagemId + "/url");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String url = extrairUrlEmitida(resposta.getBody());
        assertThat(url).contains("fake-storage.local");
        assertThat(url).contains("token=");
        assertThat(armazenamento.baixarPelaUrlAssinada(url)).contains(PNG_VALIDO);
    }

    @Test
    @DisplayName("quem nao enxerga o lead nao emite URL — 404, nao 403")
    void emitirUrl_colegaoNaoEnxerga_retorna404() {
        enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", null);
        UUID mensagemId = mensagemDeMidiaDoLead(leadDaAna);

        ResponseEntity<String> resposta = autenticado(
                EMAIL_BRUNO, HttpMethod.GET, "/api/v1/leads/" + leadDaAna + "/midias/" + mensagemId + "/url");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).doesNotContain("fake-storage.local");
        assertThat(resposta.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("mensagemId de outro lead falha mesmo com leadId correto no caminho")
    void emitirUrl_mensagemDeOutroLead_retorna404() {
        enviarAnexo(leadDaAna, PNG_VALIDO, "foto.png", null);
        UUID mensagemDaAna = mensagemDeMidiaDoLead(leadDaAna);

        UUID leadDois = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead (id, nome, telefone, atendente_responsavel_id, status_basico,
                                  ultima_interacao_em, ultima_mensagem_do_lead_em)
                VALUES (?, ?, ?, ?, 'EM_ATENDIMENTO', now(), now())
                """,
                leadDois,
                PREFIXO + "Outro cliente da Ana",
                "5561977770000",
                idAna);
        UUID atendimentoDois = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO atendimento (id, lead_id, canal_id, atendente_id, status, iniciado_em)
                SELECT ?, ?, a.canal_id, ?, 'EM_ATENDIMENTO', now()
                  FROM atendimento a WHERE a.lead_id = ? LIMIT 1
                """,
                atendimentoDois,
                leadDois,
                idAna,
                leadDaAna);
        UUID mensagemAlheia = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, midia_url, enviado_em, status_entrega)
                VALUES (?, ?, 'ATENDENTE', 'IMAGEM', 'fake/outro.png', now(), 'ENVIADO')
                """,
                mensagemAlheia,
                atendimentoDois);

        ResponseEntity<String> resposta = autenticado(
                EMAIL_ANA, HttpMethod.GET, "/api/v1/leads/" + leadDaAna + "/midias/" + mensagemAlheia + "/url");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).doesNotContain("fake-storage.local");
        assertThat(mensagemDaAna).isNotEqualTo(mensagemAlheia);
    }

    // --- apoio ------------------------------------------------------------

    private static ConditionFactory esperar() {
        return await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100));
    }

    private ResponseEntity<String> enviarAnexo(UUID leadId, byte[] conteudo, String nomeArquivo, String legenda) {
        UUID atendimentoId = atendimentoDoLeadOuAbrir(leadId);
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();

        MultiValueMap<String, Object> corpo = new LinkedMultiValueMap<>();
        corpo.add("arquivo", new ByteArrayResource(conteudo) {
            @Override
            public String getFilename() {
                return nomeArquivo;
            }
        });
        if (legenda != null) {
            corpo.add("legenda", legenda);
        }

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.MULTIPART_FORM_DATA);
        return http.postForEntity(
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens/midia",
                new HttpEntity<>(corpo, cabecalhos),
                String.class);
    }

    /**
     * O primeiro upload de um lead abre o atendimento; os seguintes so precisam do id do lead. A
     * mensagem de texto que abre a conversa e drenada da outbox e do {@link CanalFake} aqui mesmo —
     * senao ela aparece nas asserções de quem so quer ver o efeito do anexo.
     */
    private UUID atendimentoDoLeadOuAbrir(UUID leadId) {
        UUID existente = atendimentoDoLead();
        if (existente != null) {
            return existente;
        }
        // Ainda nao existe atendimento: abre um via o endpoint de texto, que o E11 ja cobre.
        String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        http.postForEntity(
                "/api/v1/atendimentos/mensagens",
                new HttpEntity<>(
                        java.util.Map.of("leadId", leadId.toString(), "conteudo", "abrindo conversa"),
                        cabecalhos),
                String.class);
        publicador.publicarPendentes();
        canal.limpar();
        return atendimentoDoLead();
    }

    private UUID atendimentoDoLead() {
        return jdbc.query(
                "SELECT id FROM atendimento WHERE lead_id = ?",
                (rs, i) -> (UUID) rs.getObject("id"),
                leadDaAna).stream().findFirst().orElse(null);
    }

    private ResponseEntity<String> autenticado(String email, HttpMethod metodo, String url) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return http.exchange(url, metodo, new HttpEntity<>(cabecalhos), String.class);
    }

    private UUID mensagemDeMidiaDoLead(UUID leadId) {
        return jdbc.queryForObject(
                """
                SELECT m.id FROM mensagem m
                  JOIN atendimento a ON a.id = m.atendimento_id
                 WHERE a.lead_id = ? AND m.midia_url IS NOT NULL
                 ORDER BY m.enviado_em DESC, m.id DESC
                 LIMIT 1
                """,
                UUID.class,
                leadId);
    }

    private static String extrairUrlEmitida(String json) {
        return json.replaceAll(".*\"url\":\"([^\"]+)\".*", "$1").replace("\\u0026", "&");
    }

    private ResponseEntity<String> mensagensComo(String email, UUID atendimentoId) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        return http.exchange(
                "/api/v1/atendimentos/" + atendimentoId + "/mensagens",
                HttpMethod.GET,
                new HttpEntity<>(cabecalhos),
                String.class);
    }

    private static String extrairMidiaUrl(String json) {
        return json.replaceAll(".*\"midiaUrl\":\"([^\"]+)\".*", "$1").replace("\\u0026", "&");
    }

    private static HttpHeaders cabecalhosWebhook(String assinatura) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (assinatura != null) {
            cabecalhos.set("X-Hub-Signature-256", assinatura);
        }
        return cabecalhos;
    }

    /**
     * Formato flat do {@code TradutorFake} (nao o da Meta — o provedor desta suite e "fake", como em
     * {@code CanalWhatsAppIT}). {@code midiaId} e o que {@link CanalFake#baixarMidiaRecebida}
     * recebe; o conteudo de verdade vem de {@link CanalFake#programarMidiaRecebida}.
     */
    private String payloadDeImagem(String idExterno, String midiaIdExterno) {
        return "{\"id\":\"" + idExterno + "\",\"de\":\"" + TELEFONE + "\",\"nome\":\"Cliente\","
                + "\"tipo\":\"IMAGEM\",\"midiaId\":\"" + midiaIdExterno + "\",\"mimetype\":\"image/png\"}";
    }

    private int mensagensDoLead() {
        Integer total = jdbc.queryForObject(
                """
                SELECT count(*) FROM mensagem m
                  JOIN atendimento a ON a.id = m.atendimento_id
                 WHERE a.lead_id = ?
                """,
                Integer.class,
                leadDaAna);
        return total == null ? 0 : total;
    }

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

    private void limpar() {
        jdbc.update("DELETE FROM outbox_evento");
        jdbc.update("DELETE FROM webhook_entrada");
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ? OR l.telefone = ?)
                """,
                PREFIXO + "%",
                TELEFONE);
        jdbc.update(
                """
                DELETE FROM atendimento WHERE lead_id IN (
                    SELECT id FROM lead WHERE nome LIKE ? OR telefone = ?)
                """,
                PREFIXO + "%",
                TELEFONE);
        jdbc.update("DELETE FROM lead WHERE nome LIKE ? OR telefone = ?", PREFIXO + "%", TELEFONE);
    }
}
