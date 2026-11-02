package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.core.application.darwin.DarwinGateway;

/** HTTP real do CRM, PostgreSQL real e contrato Darwin simulado. Nenhuma conta externa. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DarwinLeadIT extends PostgresIT {
    private static final HttpServer DARWIN = servidor();
    private static final AtomicInteger CHAMADAS = new AtomicInteger();
    private static volatile String telefone;
    private static volatile int status = 200;
    private static volatile String campos;
    private static volatile String sessoes;
    private static final String CAMPOS = "[{\"fieldId\":\"11\",\"value\":\"Pessoa Sintética Darwin\"},{\"fieldId\":\"22\",\"value\":\"529.982.247-25\"}]";

    private static HttpServer servidor() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", troca -> {
                CHAMADAS.incrementAndGet();
                String caminho = troca.getRequestURI().getPath();
                String corpo = caminho.equals("/v1/custom-fields")
                        ? "[{\"id\":11,\"type\":\"string\"},{\"id\":22,\"type\":\"string\"}]"
                        : caminho.equals("/v1/sessions") ? sessoes
                        : "{\"id\":123,\"phoneNumber\":\"+" + telefone + "\",\"customFields\":" + campos + "}";
                if (status != 200) corpo = "erro sintético";
                byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().set("Content-Type", "application/json");
                troca.sendResponseHeaders(status, bytes.length);
                troca.getResponseBody().write(bytes);
                troca.close();
            });
            servidor.start();
            return servidor;
        } catch (java.io.IOException erro) { throw new ExceptionInInitializerError(erro); }
    }

    @DynamicPropertySource
    static void configurar(DynamicPropertyRegistry r) {
        r.add("synapse.integracoes.darwin.habilitado", () -> "true");
        r.add("synapse.integracoes.darwin.url", () -> "http://127.0.0.1:" + DARWIN.getAddress().getPort());
        r.add("synapse.integracoes.darwin.api-key", () -> "chave-sintetica-nao-produtiva");
        r.add("synapse.integracoes.darwin.campo-nome", () -> "11");
        r.add("synapse.integracoes.darwin.campo-cpf", () -> "22");
        r.add("synapse.integracoes.darwin.canal-id", () -> "7");
        // Isola o teste de falhas do histórico do circuito compartilhado entre métodos.
        r.add("synapse.integracoes.darwin.circuito-minimo", () -> "100");
    }

    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @SpyBean DarwinGateway gateway;
    private UUID lead;
    private UUID outro;
    private String ana;
    private String bruno;

    @BeforeEach void preparar() {
        CHAMADAS.set(0); status = 200; campos = CAMPOS;
        sessoes = "[{\"sessionId\":123,\"customFields\":" + campos + "}]";
        telefone = "55619" + String.format("%08d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000L));
        lead = criar(EMAIL_ANA, telefone);
        outro = criar(EMAIL_BRUNO, null);
        ana = login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        bruno = login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken();
        doAnswer(invocacao -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return invocacao.callRealMethod();
        }).when(gateway).consultar(anyString());
    }
    @AfterEach void limpar() {
        jdbc.update("DELETE FROM audit_log WHERE entidade_id IN (?,?)", lead, outro);
        jdbc.update("DELETE FROM lead WHERE id IN (?,?)", lead, outro);
    }
    private UUID criar(String email, String numero) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,notas,resumo_ia) VALUES (?,?,?,(SELECT id FROM usuario WHERE email=?),'EM_ATENDIMENTO',?,?)",
                id, "Identificação atual", numero, email, "Nota preservada", "Resumo preservado");
        return id;
    }
    private ResponseEntity<String> pedido(String token, HttpMethod metodo, String url, Object corpo) {
        var headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, headers), String.class);
    }
    private String rota(UUID id, String acao) { return "/api/v1/leads/" + id + "/darwin/" + acao; }
    private JsonNode previa() throws Exception {
        var resposta = pedido(ana, HttpMethod.POST, rota(lead,"consultas"), null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }
    private ResponseEntity<String> aplicar(String token, UUID id, JsonNode previa, boolean nome, boolean cpf) {
        return pedido(token,HttpMethod.POST,rota(id,"aplicacoes"),Map.of("confirmacao",previa.path("confirmacao").asText(),"nome",nome,"cpf",cpf));
    }

    @Test void consultaNaoGravaEConfirmacaoPersistePelaFichaComAuditoriaSanitizada() throws Exception {
        assertThat(pedido(ana,HttpMethod.GET,"/api/v1/leads/integracoes/darwin",null).getBody()).contains("\"habilitada\":true");
        assertThat(CHAMADAS).hasValue(0);
        var p = previa();
        assertThat(p.path("estado").asText()).isEqualTo("ENCONTRADO");
        assertThat(p.path("cpfAtual").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,lead)).isEqualTo("Identificação atual");
        assertThat(aplicar(ana,lead,p,true,true).getStatusCode()).isEqualTo(HttpStatus.OK);
        var ficha = pedido(ana,HttpMethod.GET,"/api/v1/leads/"+lead,null);
        assertThat(ficha.getBody()).contains("Pessoa Sintética Darwin","52998224725","Nota preservada","Resumo preservado",telefone);
        assertThat(aplicar(ana,lead,p,true,true).getBody()).contains("\"camposAlterados\":[]");
        assertThat(CHAMADAS).hasValue(3); // aplicação/reload não consultam o provedor.
        var auditoria = jdbc.queryForList("SELECT dados_depois::text FROM audit_log WHERE acao='APLICAR_DADOS_DARWIN' AND entidade_id=?",String.class,lead);
        assertThat(auditoria).hasSize(2).allSatisfy(a -> assertThat(a).contains("DARWIN","camposAlterados").doesNotContain("52998224725","Pessoa Sintética Darwin",telefone,"confirmacao","chave-sintetica"));
    }
    @Test void colegaInexistenteESemLoginNaoConsultamNemAplicam() throws Exception {
        for (UUID id : java.util.List.of(outro,UUID.randomUUID())) {
            assertThat(pedido(ana,HttpMethod.POST,rota(id,"consultas"),null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(pedido(ana,HttpMethod.POST,rota(id,"aplicacoes"),Map.of("confirmacao","invalida","nome",true)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(http.postForEntity(rota(lead,"consultas"),null,String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(CHAMADAS).hasValue(0);
        var p = previa();
        assertThat(aplicar(bruno,lead,p,true,true).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // Mesmo lead visível ao gestor, recibo continua vinculado ao usuário consultante.
        String gestor = login(http,EMAIL_GESTOR,SENHA_GESTOR).accessToken();
        assertThat(aplicar(gestor,lead,p,true,true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    @Test void camposNaoSelecionadosEEdicaoManualConcorrenteSaoPreservados() throws Exception {
        var p = previa();
        pedido(ana,HttpMethod.PUT,"/api/v1/leads/"+lead,Map.of("nome","Correção manual"));
        assertThat(aplicar(ana,lead,p,true,true).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT cpf FROM lead WHERE id=?",String.class,lead)).isNull();
        assertThat(aplicar(ana,lead,p,false,true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,lead)).isEqualTo("Correção manual");
    }
    @Test void telefoneAlteradoEReciboAdulteradoNaoAplicam() throws Exception {
        var p = previa();
        assertThat(pedido(ana,HttpMethod.POST,rota(lead,"aplicacoes"),Map.of("confirmacao","x"+p.path("confirmacao").asText(),"nome",true)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        jdbc.update("UPDATE lead SET telefone=NULL WHERE id=?",lead);
        assertThat(aplicar(ana,lead,p,true,true).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(previa().path("estado").asText()).isEqualTo("SEM_IDENTIDADE");
        assertThat(CHAMADAS).hasValue(3);
    }
    @Test void ambiguidadeNaoMisturaSessoesNemForneceConfirmacao() throws Exception {
        sessoes = "[{\"sessionId\":123,\"customFields\":[{\"fieldId\":\"11\",\"value\":\"Nome A\"}]},{\"sessionId\":122,\"customFields\":[{\"fieldId\":\"22\",\"value\":\"52998224725\"}]}]";
        var p = previa();
        assertThat(p.path("estado").asText()).isEqualTo("AMBIGUO");
        assertThat(p.path("confirmacao").isNull()).isTrue();
        assertThat(p.path("candidatos")).hasSize(2);
        assertThat(CHAMADAS).hasValue(2);
    }
    @Test void ausentesInvalidosESessaoVaziaNaoViraramDadosAplicaveis() throws Exception {
        sessoes = "[]";
        assertThat(previa().path("estado").asText()).isEqualTo("SEM_RESULTADO");
        campos = "[{\"fieldId\":\"22\",\"value\":\"11111111111\"}]";
        sessoes = "[{\"sessionId\":123,\"customFields\":"+campos+"}]";
        var p = previa();
        assertThat(p.path("candidatos").get(0).path("cpfInvalido").asBoolean()).isTrue();
        assertThat(aplicar(ana,lead,p,false,true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        campos = "[]"; sessoes = "[{\"sessionId\":123,\"customFields\":[]}]";
        assertThat(previa().path("estado").asText()).isEqualTo("SEM_RESULTADO");
    }
    @Test void autorizacaoDoProvedorNaoDeslogaCrmEFichaContinuaDisponivel() {
        status = 403;
        var resposta = pedido(ana,HttpMethod.POST,rota(lead,"consultas"),null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(resposta.getBody()).contains("DARWIN_AUTENTICACAO").doesNotContain(telefone,"chave-sintetica");
        assertThat(CHAMADAS).hasValue(1);
        assertThat(pedido(ana,HttpMethod.GET,"/api/v1/leads/"+lead,null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void permissaoDeEditarEExigidaAntesDeConsultarOuAplicar() throws Exception {
        UUID usuario = UUID.randomUUID(); String email = usuario+"@dev.local";
        jdbc.update("INSERT INTO usuario (id,nome,email,senha_hash,papel,ativo,senha_alterada_em) SELECT ?, 'Somente leitura', ?, senha_hash, papel, TRUE, now() FROM usuario WHERE email=?",usuario,email,EMAIL_ANA);
        try {
            jdbc.update("INSERT INTO permissao_usuario (usuario_id) VALUES (?)",usuario);
            jdbc.update("INSERT INTO permissao_usuario_excecao (usuario_id,tipo,alvo,valor) VALUES (?,'ACAO','contatos.editar','NEGAR')",usuario);
            jdbc.update("UPDATE lead SET atendente_responsavel_id=? WHERE id=?",usuario,lead);
            String token = login(http,email,SENHA_ATENDENTE).accessToken();
            assertThat(pedido(token,HttpMethod.GET,"/api/v1/leads/"+lead,null).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(pedido(token,HttpMethod.POST,rota(lead,"consultas"),null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(pedido(token,HttpMethod.POST,rota(lead,"aplicacoes"),Map.of("confirmacao","invalida","nome",true)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(CHAMADAS).hasValue(0);
        } finally {
            jdbc.update("UPDATE lead SET atendente_responsavel_id=(SELECT id FROM usuario WHERE email=?) WHERE id=?",EMAIL_ANA,lead);
            jdbc.update("DELETE FROM usuario WHERE id=?",usuario);
        }
    }
}
