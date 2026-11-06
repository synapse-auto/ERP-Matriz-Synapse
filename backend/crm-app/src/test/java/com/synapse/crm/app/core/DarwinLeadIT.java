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
    private static volatile String paciente;
    private static final String PACIENTE = "{\"cpf\":\"529.982.247-25\",\"name\":\"Pessoa Sintética Darwin\",\"email\":\"sintetica@example.invalid\",\"phoneNumber\":null,\"motherName\":\"Campo não utilizado\"}";

    private static HttpServer servidor() {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", troca -> {
                CHAMADAS.incrementAndGet();
                String corpo = paciente;
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
        r.add("synapse.integracoes.darwin.coleta.habilitada", () -> "false"); // Esta suite cobre o fluxo manual.
        r.add("synapse.integracoes.darwin.url", () -> "http://127.0.0.1:" + DARWIN.getAddress().getPort());
        r.add("synapse.integracoes.darwin.token", () -> "token-medico-sintetico");
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
        CHAMADAS.set(0); status = 200; paciente = PACIENTE;
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
        var resposta = pedido(ana, HttpMethod.POST, rota(lead,"consultas"), Map.of("cpf","529.982.247-25"));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return json.readTree(resposta.getBody());
    }
    private ResponseEntity<String> aplicar(String token, UUID id, JsonNode previa, boolean nome, boolean cpf) {
        return aplicar(token,id,previa,nome,cpf,false);
    }
    private ResponseEntity<String> aplicar(String token, UUID id, JsonNode previa, boolean nome, boolean cpf, boolean email) {
        return pedido(token,HttpMethod.POST,rota(id,"aplicacoes"),Map.of("confirmacao",previa.path("confirmacao").asText(),"nome",nome,"cpf",cpf,"email",email));
    }

    @Test void consultaNaoGravaEConfirmacaoPersistePelaFichaComAuditoriaSanitizada() throws Exception {
        assertThat(pedido(ana,HttpMethod.GET,"/api/v1/leads/integracoes/darwin",null).getBody())
                .contains("\"habilitada\":true", "\"estado\":\"DISPONIVEL\"");
        assertThat(CHAMADAS).hasValue(0);
        var p = previa();
        assertThat(p.path("estado").asText()).isEqualTo("ENCONTRADO");
        assertThat(p.path("cpfAtual").isNull()).isTrue();
        assertThat(p.path("emailAtual").isNull()).isTrue();
        assertThat(p.path("candidatos").get(0).path("sessaoId").isNull()).isTrue();
        assertThat(p.path("candidatos").get(0).path("email").asText()).isEqualTo("sintetica@example.invalid");
        assertThat(p.toString()).doesNotContain("motherName", "phoneNumber", "Campo não utilizado");
        assertThat(jdbc.queryForObject("SELECT cpf FROM lead WHERE id=?",String.class,lead)).isNull();
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,lead)).isEqualTo("Identificação atual");
        var aplicado = aplicar(ana,lead,p,true,true,true);
        assertThat(aplicado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aplicado.getBody()).contains("DARWIN_MEDICO");
        var ficha = pedido(ana,HttpMethod.GET,"/api/v1/leads/"+lead,null);
        assertThat(ficha.getBody()).contains("Pessoa Sintética Darwin","52998224725","sintetica@example.invalid","Nota preservada","Resumo preservado",telefone);
        assertThat(aplicar(ana,lead,p,true,true,true).getBody()).contains("\"camposAlterados\":[]");
        assertThat(CHAMADAS).hasValue(1); // aplicação/reload não consultam o provedor.
        var auditoria = jdbc.queryForList("SELECT dados_depois::text FROM audit_log WHERE acao='APLICAR_DADOS_DARWIN' AND entidade_id=?",String.class,lead);
        assertThat(auditoria).hasSize(2).allSatisfy(a -> assertThat(a).contains("DARWIN_MEDICO","camposAlterados").doesNotContain("52998224725","Pessoa Sintética Darwin","sintetica@example.invalid",telefone,"confirmacao","token-medico-sintetico"));
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
        assertThat(previa().path("estado").asText()).isEqualTo("ENCONTRADO"); // CPF conhecido dispensa telefone.
        assertThat(CHAMADAS).hasValue(2);
    }
    @Test void arrayOuCpfDivergenteNaoOferecemConfirmacaoNemGravam() {
        for(String corpo:java.util.List.of("[]","{\"cpf\":\"39053344705\",\"name\":\"Outro cadastro\"}")) {
            paciente=corpo;
            var resposta=pedido(ana,HttpMethod.POST,rota(lead,"consultas"),Map.of("cpf","52998224725"));
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(resposta.getBody()).doesNotContain("Outro cadastro");
            assertThat(jdbc.queryForObject("SELECT cpf FROM lead WHERE id=?",String.class,lead)).isNull();
        }
    }
    @Test void pacienteAusenteNoEscopoENomeNuloNaoInventamValores() throws Exception {
        status=404;
        assertThat(previa().path("estado").asText()).isEqualTo("SEM_RESULTADO");
        status=200;paciente="{\"cpf\":\"52998224725\",\"name\":null}";
        var p = previa();
        assertThat(p.path("candidatos").get(0).path("nome").isNull()).isTrue();
        assertThat(aplicar(ana,lead,p,true,false).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(aplicar(ana,lead,p,false,true).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,lead)).isEqualTo("Identificação atual");
    }
    @Test void autorizacaoDoProvedorNaoDeslogaCrmEFichaContinuaDisponivel() {
        status = 403;
        var resposta = pedido(ana,HttpMethod.POST,rota(lead,"consultas"),Map.of("cpf","52998224725"));
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

    @Test void telefoneSemCpfNaoDescobreDocumentoNemFazChamada() throws Exception {
        var resposta=pedido(ana,HttpMethod.POST,rota(lead,"consultas"),null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resposta.getBody()).path("estado").asText()).isEqualTo("SEM_IDENTIDADE");
        assertThat(CHAMADAS).hasValue(0);
    }

    @Test void cpfCadastradoDispensaCorpoENaoDependeDeTelefone() throws Exception {
        jdbc.update("UPDATE lead SET cpf='52998224725',telefone=NULL WHERE id=?",lead);
        var resposta=pedido(ana,HttpMethod.POST,rota(lead,"consultas"),null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        var p=json.readTree(resposta.getBody());
        assertThat(p.path("estado").asText()).isEqualTo("ENCONTRADO");
        assertThat(aplicar(ana,lead,p,true,false).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(CHAMADAS).hasValue(1);
    }

    @Test void documentoInvalidoNaoConsultaENaoEhPersistido() {
        for(String cpf:java.util.List.of("11111111111","5561999991234","52998224724")) {
            var resposta=pedido(ana,HttpMethod.POST,rota(lead,"consultas"),Map.of("cpf",cpf));
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("DARWIN_CPF_INVALIDO");
        }
        assertThat(CHAMADAS).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT cpf FROM lead WHERE id=?",String.class,lead)).isNull();
    }

    @Test void trocaConcorrenteDeCpfBloqueiaInclusiveAplicacaoSoDoNome() throws Exception {
        var p=previa();
        jdbc.update("UPDATE lead SET cpf='39053344705' WHERE id=?",lead);
        assertThat(aplicar(ana,lead,p,true,false).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT nome FROM lead WHERE id=?",String.class,lead)).isEqualTo("Identificação atual");
    }

    @Test void emailPodeSerConfirmadoEEdicaoConcorrenteNaoEhSobrescrita() throws Exception {
        var p=previa();
        var aplicada=aplicar(ana,lead,p,false,false,true);
        assertThat(aplicada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(aplicada.getBody()).contains("email");
        assertThat(jdbc.queryForObject("SELECT email FROM lead WHERE id=?",String.class,lead)).isEqualTo("sintetica@example.invalid");

        p=previa();
        pedido(ana,HttpMethod.PUT,"/api/v1/leads/"+lead,Map.of("email","manual@example.invalid"));
        assertThat(aplicar(ana,lead,p,false,false,true).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT email FROM lead WHERE id=?",String.class,lead)).isEqualTo("manual@example.invalid");
    }

    @Test void emailMalformadoDaDarwinFicaIndisponivelEEndpointRecusaSelecaoDireta() throws Exception {
        paciente="{\"cpf\":\"52998224725\",\"name\":\"Pessoa Sintética Darwin\",\"email\":\"não é e-mail\"}";
        var p=previa();
        assertThat(p.path("candidatos").get(0).path("email").isNull()).isTrue();
        assertThat(p.path("candidatos").get(0).path("emailInvalido").asBoolean()).isTrue();
        assertThat(aplicar(ana,lead,p,false,false,true).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT email FROM lead WHERE id=?",String.class,lead)).isNull();
    }

    @Test void openApiDocumentaConsultaPorCpfConhecidoSemPrometerBuscaPorTelefone() throws Exception {
        var resposta = pedido(ana,HttpMethod.GET,"/v3/api-docs",null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        var contrato = json.readTree(resposta.getBody());
        var consulta = contrato.path("paths").path("/api/v1/leads/{id}/darwin/consultas").path("post");
        assertThat(consulta.path("description").asText()).contains("CPF", "Não descobre CPF por telefone");
        assertThat(consulta.path("requestBody").isMissingNode()).isFalse();
        var esquema = consulta.path("requestBody").path("content").path("application/json").path("schema");
        String referencia = esquema.path("$ref").asText();
        if (!referencia.isEmpty()) esquema = contrato.at(referencia.substring(1));
        assertThat(esquema.path("properties").has("cpf")).isTrue();
        var aplicacao = contrato.path("paths").path("/api/v1/leads/{id}/darwin/aplicacoes").path("post")
                .path("requestBody").path("content").path("application/json").path("schema");
        String referenciaAplicacao = aplicacao.path("$ref").asText();
        if (!referenciaAplicacao.isEmpty()) aplicacao = contrato.at(referenciaAplicacao.substring(1));
        assertThat(aplicacao.path("properties").has("email")).isTrue();
        assertThat(CHAMADAS).hasValue(0);
    }
}
