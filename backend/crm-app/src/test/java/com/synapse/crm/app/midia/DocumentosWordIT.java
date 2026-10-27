package com.synapse.crm.app.midia;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.atendimento.infrastructure.outbox.PublicadorDaOutbox;
import com.synapse.crm.atendimento.infrastructure.webhook.ProcessadorDeWebhookEntrada;
import com.synapse.crm.sharedkernel.midia.DocumentoWord;

/** HTTP, autorização, PostgreSQL e MinIO reais. O gateway externo é o fake já existente. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@Import(DocumentosWordIT.StorageReal.class)
@TestPropertySource(properties={"synapse.canal.whatsapp.provedor=fake"})
class DocumentosWordIT extends PostgresIT {
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "cgr.dev/chainguard/minio@sha256:bd014394a80898e68c149f2311fdf8d5a2c2f3bb2c33b9327ae6d02b4b065ae1"))
            .withEnv("MINIO_ROOT_USER", "word-test-access").withEnv("MINIO_ROOT_PASSWORD", "word-test-secret-storage")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    static { MINIO.start(); }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registro) {
        registro.add("synapse.midia.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registro.add("synapse.midia.url-publica", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registro.add("synapse.midia.bucket", () -> "word-test");
        registro.add("synapse.midia.access-key", () -> "word-test-access");
        registro.add("synapse.midia.secret-key", () -> "word-test-secret-storage");
    }
    @TestConfiguration(proxyBeanMethods=false) static class StorageReal {
        @Bean static BeanFactoryPostProcessor selecionarStorage() {
            return fabrica -> {
                fabrica.getBeanDefinition("armazenamentoDeMidiaFake").setPrimary(false);
                fabrica.getBeanDefinition("minioArmazenamentoDeMidia").setPrimary(true);
            };
        }
    }
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired CanalFake canal;
    @Autowired PublicadorDaOutbox publicador;
    @Autowired ProcessadorDeWebhookEntrada processador;
    private String ana, bruno, gestor;
    private UUID lead, atendimento;
    private String telefone;
    private final List<UUID> conversas = new ArrayList<>();
    private final List<UUID> mensagensInternas = new ArrayList<>();

    @BeforeEach void preparar() {
        canal.limpar();
        ana = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        bruno = login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken();
        gestor = login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        lead = UUID.randomUUID();
        telefone = "5561" + String.format("%09d", Math.floorMod(lead.getLeastSignificantBits(), 1_000_000_000L));
        UUID usuario = db.queryForObject("SELECT id FROM usuario WHERE email=?", UUID.class, EMAIL_ANA);
        db.update("INSERT INTO lead(id,nome,telefone,status_basico,atendente_responsavel_id,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,? ,?,'EM_ATENDIMENTO',?,now(),now())",
                lead, "WORD-"+lead, telefone, usuario);
        var abertura = http.postForEntity("/api/v1/atendimentos/mensagens", entidade(ana, Map.of("leadId",lead,"conteudo","Abertura sintética")), String.class);
        assertThat(abertura.getStatusCode()).isEqualTo(HttpStatus.OK);
        atendimento = db.queryForObject("SELECT id FROM atendimento WHERE lead_id=?", UUID.class, lead);
        publicador.publicarPendentes(); canal.limpar();
    }

    @AfterEach void limpar() {
        if (atendimento != null) {
            db.update("DELETE FROM chat_interno_encaminhamento_cliente WHERE atendimento_id=?", atendimento);
            db.update("DELETE FROM audit_log WHERE lead_id=?", lead);
            db.update("DELETE FROM outbox_evento WHERE payload::text LIKE ?", "%"+atendimento+"%");
            db.update("DELETE FROM webhook_entrada WHERE payload::text LIKE ?", "%"+telefone+"%");
            db.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id=?", atendimento);
            db.update("DELETE FROM mensagem WHERE atendimento_id=?", atendimento);
            db.update("DELETE FROM atendimento_participante WHERE atendimento_id=?", atendimento);
            db.update("DELETE FROM atendimento WHERE id=?", atendimento);
        }
        if (lead != null) db.update("DELETE FROM lead WHERE id=?",lead);
        for (UUID mensagem : mensagensInternas) {
            db.update("DELETE FROM chat_interno_midia_idempotencia WHERE mensagem_id=?", mensagem);
            db.update("DELETE FROM chat_interno_mensagem WHERE id=?", mensagem);
        }
        for (UUID conversa : conversas) {
            db.update("DELETE FROM chat_interno_mensagem WHERE conversa_id=?", conversa);
            db.update("DELETE FROM chat_interno_participante WHERE conversa_id=?", conversa);
            db.update("DELETE FROM chat_interno_conversa WHERE id=?", conversa);
        }
    }

    @ParameterizedTest @CsvSource({"doc,direta","docx,direta","doc,grupo","docx,grupo"})
    void participantesRecebemRecarregamBaixamEEncaminhamWord(String extensao, String tipo) throws Exception {
        UUID conversa = conversa(tipo);
        byte[] bytes = arquivo(extensao);
        String nome = "Relatório final."+extensao.toUpperCase();
        String chave = UUID.randomUUID().toString();
        var enviado = upload(ana, "/api/v1/chat-interno/conversas/"+conversa+"/mensagens/midia", bytes, nome, chave);
        assertThat(enviado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var dados = json.readTree(enviado.getBody());
        String mensagem = dados.path("id").asText();
        mensagensInternas.add(UUID.fromString(mensagem));
        assertThat(dados.path("tipo").asText()).isEqualTo("DOCUMENTO");
        assertThat(json.readTree(dados.path("midiaMetadados").asText()).path("nome_original").asText()).isEqualTo(nome);
        assertThat(json.readTree(upload(ana,"/api/v1/chat-interno/conversas/"+conversa+"/mensagens/midia", bytes,nome,chave).getBody()).path("id").asText()).isEqualTo(mensagem);
        String historico = http.exchange("/api/v1/chat-interno/conversas/"+conversa+"/mensagens",HttpMethod.GET,entidade(bruno,null),String.class).getBody();
        assertThat(historico).contains(mensagem,"DOCUMENTO");
        String rota = "/api/v1/chat-interno/conversas/"+conversa+"/midias/"+mensagem+"/arquivo";
        conferirDownload(rota,bruno,bytes,nome,mime(extensao));
        assertThat(http.exchange(rota,HttpMethod.GET,entidade(gestor,null),byte[].class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(upload(gestor,"/api/v1/chat-interno/conversas/"+conversa+"/mensagens/midia",bytes,nome,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Encaminhamento interno reutiliza o mesmo arquivo, mas autoriza a conversa de destino.
        UUID destino = conversa("grupo");
        var encaminhado = http.postForEntity("/api/v1/chat-interno/conversas/"+conversa+"/mensagens/"+mensagem+"/encaminhar",
                entidade(ana,Map.of("conversaDestinoId",destino)),String.class);
        assertThat(encaminhado.getStatusCode().is2xxSuccessful()).isTrue();
        String idEncaminhado = json.readTree(encaminhado.getBody()).path("id").asText();
        conferirDownload("/api/v1/chat-interno/conversas/"+destino+"/midias/"+idEncaminhado+"/arquivo",bruno,bytes,nome,mime(extensao));
        var h = new HttpHeaders(); h.setBearerAuth(ana); h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Idempotency-Key", UUID.randomUUID().toString());
        var paraCliente = http.postForEntity("/api/v1/atendimentos/"+atendimento+"/encaminhamento-do-chat-interno",
                new HttpEntity<>(Map.of("conversaId",conversa,"mensagemId",mensagem),h),String.class);
        assertThat(paraCliente.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String externa = json.readTree(paraCliente.getBody()).path("mensagemExternaId").asText();
        var repetido = json.readTree(http.postForEntity("/api/v1/atendimentos/"+atendimento+"/encaminhamento-do-chat-interno",
                new HttpEntity<>(Map.of("conversaId",conversa,"mensagemId",mensagem),h),String.class).getBody());
        assertThat(repetido.path("mensagemExternaId").asText()).isEqualTo(externa);
        assertThat(repetido.path("reutilizado").asBoolean()).isTrue();
        publicador.publicarPendentes(); publicador.publicarPendentes();
        conferirDownload("/api/v1/leads/"+lead+"/midias/"+externa+"/download",ana,bytes,nome,mime(extensao));
        assertThat(canal.enviados().stream().filter(e -> e.mensagemId().toString().equals(externa)).count()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings={"doc","docx"})
    void uploadExternoEDownloadPreservamNomeConteudoEIdempotencia(String extensao) throws Exception {
        byte[] bytes = arquivo(extensao);
        String nome = "Proposta com revisão."+extensao.toUpperCase();
        String rota = "/api/v1/atendimentos/"+atendimento+"/mensagens/midia";
        String chave = UUID.randomUUID().toString();
        var enviado = upload(ana,rota,bytes,nome,chave);
        assertThat(enviado.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id = json.readTree(enviado.getBody()).path("mensagemId").asText();
        assertThat(json.readTree(upload(ana,rota,bytes,nome,chave).getBody()).path("mensagemId").asText()).isEqualTo(id);
        publicador.publicarPendentes(); publicador.publicarPendentes();
        assertThat(canal.enviados().stream().filter(e -> e.mensagemId().toString().equals(id)).count()).isEqualTo(1);
        String download = "/api/v1/leads/"+lead+"/midias/"+id+"/download";
        conferirDownload(download,ana,bytes,nome,mime(extensao));
        assertThat(http.exchange(download,HttpMethod.GET,entidade(bruno,null),byte[].class).getStatusCode()).isIn(HttpStatus.NOT_FOUND,HttpStatus.FORBIDDEN);
        assertThat(http.exchange("/api/v1/atendimentos/"+atendimento+"/mensagens",HttpMethod.GET,entidade(ana,null),String.class).getBody()).contains(id,"DOCUMENTO");
    }

    @ParameterizedTest @ValueSource(strings={"doc","docx"})
    void webhookAssincronoComMimeGenericoPersisteWordPelosBytes(String extensao) throws Exception {
        byte[] bytes = arquivo(extensao);
        String nome = "Recebido com acentuação."+extensao;
        canal.programarMidiaRecebida(bytes,"application/octet-stream");
        String id = UUID.randomUUID().toString();
        var h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); h.set("X-Hub-Signature-256",CanalFake.ASSINATURA_VALIDA);
        String payload = json.writeValueAsString(Map.of("id",id,"de",telefone,"tipo","DOCUMENTO","midiaId","word-sintetico","mimetype","application/octet-stream","nomeArquivo",nome));
        for (int i=0;i<2;i++) assertThat(http.postForEntity("/webhook/canal",new HttpEntity<>(payload,h),String.class).getStatusCode().is2xxSuccessful()).isTrue();
        processador.processarPendentes(); processador.processarPendentes();
        var ids = db.queryForList("SELECT id FROM mensagem WHERE atendimento_id=? AND tipo='DOCUMENTO'",UUID.class,atendimento);
        assertThat(ids).hasSize(1);
        conferirDownload("/api/v1/leads/"+lead+"/midias/"+ids.getFirst()+"/download",ana,bytes,nome,mime(extensao));
    }

    @ParameterizedTest @ValueSource(strings={"doc","docx"})
    void arquivoFalsoOuVazioNaoEntraNosChats(String extensao) throws Exception {
        UUID conversa = conversa("grupo");
        for (byte[] bytes : new byte[][]{new byte[0], "texto nao e Word".getBytes(StandardCharsets.UTF_8)}) {
            assertThat(upload(ana,"/api/v1/chat-interno/conversas/"+conversa+"/mensagens/midia",bytes,"forjado."+extensao,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(upload(ana,"/api/v1/atendimentos/"+atendimento+"/mensagens/midia",bytes,"forjado."+extensao,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @ParameterizedTest @ValueSource(strings={"doc","docx"})
    void documentoValidoAcimaDoLimiteConfiguradoERecusadoNosDoisChats(String extensao) throws Exception {
        String configuracao="anexo.tamanho_maximo_documento_mb";
        var anterior=db.queryForList("SELECT valor FROM configuracao_automacao WHERE chave=?",String.class,configuracao);
        db.update("INSERT INTO configuracao_automacao(chave,valor,tipo) VALUES (?,'1','INT') ON CONFLICT(chave) DO UPDATE SET valor='1'",configuracao);
        try {
            byte[] grande;
            if(extensao.equals("doc")) grande=java.util.Arrays.copyOf(arquivo(extensao),arquivo(extensao).length+2*1024*1024);
            else {
                var bytes=new ByteArrayOutputStream();
                try(var zip=new ZipOutputStream(bytes)) {
                    zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION);
                    try(var original=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(arquivo(extensao)))) {
                        java.util.zip.ZipEntry parte;
                        while((parte=original.getNextEntry())!=null) {
                            String texto=new String(original.readAllBytes(),StandardCharsets.UTF_8);
                            if(parte.getName().equals("word/document.xml")) texto=texto.replace("Documento sintético CRM","a".repeat(2*1024*1024));
                            entrada(zip,parte.getName(),texto);
                        }
                    }
                }
                grande=bytes.toByteArray();
            }
            UUID conversa=conversa("grupo");
            assertThat(upload(ana,"/api/v1/chat-interno/conversas/"+conversa+"/mensagens/midia",grande,"grande."+extensao,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
            assertThat(upload(ana,"/api/v1/atendimentos/"+atendimento+"/mensagens/midia",grande,"grande."+extensao,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        } finally {
            if(anterior.isEmpty()) db.update("DELETE FROM configuracao_automacao WHERE chave=?",configuracao);
            else db.update("UPDATE configuracao_automacao SET valor=? WHERE chave=?",anterior.getFirst(),configuracao);
        }
    }

    private UUID conversa(String tipo) {
        UUID idBruno = db.queryForObject("SELECT id FROM usuario WHERE email=?",UUID.class,EMAIL_BRUNO);
        Object corpo = tipo.equals("direta") ? Map.of("usuarioId",idBruno) : Map.of("nome","Word sintético "+UUID.randomUUID(),"participantes",List.of(idBruno));
        var resposta = http.postForEntity("/api/v1/chat-interno/conversas/"+tipo,entidade(ana,corpo),Map.class);
        assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
        UUID id = UUID.fromString(resposta.getBody().get("id").toString());
        // Direta pode já existir por outro teste: só retirar mensagens criadas aqui ao limpar.
        if (tipo.equals("grupo")) conversas.add(id);
        return id;
    }
    private void conferirDownload(String rota,String token,byte[] bytes,String nome,String mime) {
        var download = http.exchange(rota,HttpMethod.GET,entidade(token,null),byte[].class);
        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(download.getBody()).containsExactly(bytes);
        assertThat(download.getHeaders().getContentType()).isEqualTo(MediaType.parseMediaType(mime));
        assertThat(download.getHeaders().getContentDisposition().isAttachment()).isTrue();
        assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo(nome);
        assertThat(download.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }
    private ResponseEntity<String> upload(String token,String rota,byte[] bytes,String nome,String chave) {
        var h = new HttpHeaders(); h.setBearerAuth(token); h.setContentType(MediaType.MULTIPART_FORM_DATA); h.set("Idempotency-Key",chave);
        var corpo = new LinkedMultiValueMap<String,Object>();
        var parte = new HttpHeaders(); parte.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        corpo.add("arquivo",new HttpEntity<>(new ByteArrayResource(bytes){@Override public String getFilename(){return nome;}},parte));
        return http.postForEntity(rota,new HttpEntity<>(corpo,h),String.class);
    }
    private static HttpEntity<Object> entidade(String token,Object corpo) {
        var h = new HttpHeaders(); h.setBearerAuth(token); h.setContentType(MediaType.APPLICATION_JSON); return new HttpEntity<>(corpo,h);
    }
    static String mime(String extensao) { return extensao.equals("doc") ? DocumentoWord.DOC : DocumentoWord.DOCX; }
    static byte[] arquivo(String extensao) throws Exception {
        if (extensao.equals("doc")) try (var entrada = DocumentosWordIT.class.getResourceAsStream("/word/vazio.doc")) { return entrada.readAllBytes(); }
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            entrada(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Override PartName=\"/word/document.xml\" ContentType=\""+DocumentoWord.DOCX+".main+xml\"/></Types>");
            entrada(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            entrada(zip,"word/document.xml","<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Documento sintético CRM</w:t></w:r></w:p></w:body></w:document>");
        }
        return bytes.toByteArray();
    }
    private static void entrada(ZipOutputStream zip,String nome,String conteudo) throws Exception {
        zip.putNextEntry(new ZipEntry(nome)); zip.write(conteudo.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
}
