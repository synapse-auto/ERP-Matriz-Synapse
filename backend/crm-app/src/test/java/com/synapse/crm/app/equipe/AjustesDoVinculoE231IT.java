package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.canal.CanalFake;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.core.domain.timeline.OrigemEvento;
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;

/**
 * E231 sobre o atendente vinculado (D3b): (a) com setores ligados, o vinculado direto exige setor que recebe da
 * triagem; (b) o dono que fala e quem abre o contato como responsavel tambem viram o vinculado; e as guardas do
 * caminho desligado. Os ITs de rodizio e transferencia existentes nao foram editados.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
    "synapse.seguranca.token-interno=e231-vinculo-token",
    "synapse.canal.whatsapp.provedor=fake"
})
class AjustesDoVinculoE231IT extends PostgresIT {

    private static final String TOKEN = "e231-vinculo-token";
    private static final String PREFIXO = "E231-VINCULO-";
    private static final String CHAVE_VINCULADO = "ia.distribuicao.atendente_vinculado";
    private static final String TRANSACAO_DO_LISTENER =
            "com.synapse.crm.atendimento.infrastructure.vinculo.VinculoDoLeadListener.aoAcontecer";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private CanalFake canal;
    @Autowired private ResolvedorDePermissoesEfetivas flags;
    @Autowired private VinculoDoLeadRepositorio vinculos;
    @Autowired private ApplicationEventPublisher eventos;
    @Autowired private List<PlatformTransactionManager> gerenciadores;
    @Autowired private PlatformTransactionManager transacao;

    /** Estatica: o JUnit cria uma instancia por teste e telefone/nome de setor sao unicos no banco compartilhado. */
    private static final AtomicInteger sequencia = new AtomicInteger();
    private UUID ana;
    private UUID bruno;

    @BeforeEach
    void preparar() {
        canal.limpar();
        ana = idDoUsuario(EMAIL_ANA);
        bruno = idDoUsuario(EMAIL_BRUNO);
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
        jdbc.update("UPDATE configuracao_automacao SET valor = 'false' WHERE chave = 'ia.distribuicao.sequencial'");
        desligarTudo();
    }

    @AfterEach
    void limpar() {
        canal.limpar();
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem_envio_idempotencia WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario_setor WHERE setor_id IN (SELECT id FROM setor WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM setor WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN (SELECT id FROM usuario WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
        desligarTudo();
    }

    // --- (a) vinculado so vale em setor que recebe da triagem ------------------------------------------------------

    @Test
    @DisplayName("(a) setores ligados: vinculado sem setor nao recebe direto; cai no rodizio e o novo vira o vinculado")
    void vinculadoSemSetorCaiNoRodizio() {
        ligarSetores();
        ligarVinculado();
        UUID vendas = criarSetor("Vendas", true, true);
        UUID semSetor = criarAtendente("SEM-SETOR", null);
        UUID vendedor = criarAtendente("VENDEDOR", vendas);
        criarAbertos("vendedor", vendedor, 3); // o sem-setor e mais ocioso: se fosse elegivel ao vinculo, receberia
        UUID atendimento = criarAtendimentoNaIa("sem-setor");
        UUID lead = leadDe(atendimento);
        vincular(lead, semSetor);

        ResponseEntity<String> resposta = transferirProximo(atendimento, "a-1", Map.of("setorId", vendas));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isEqualTo(vendedor);
        assertThat(vinculoDe(lead)).as("o novo atendente vira o vinculado").isEqualTo(vendedor);
    }

    @Test
    @DisplayName("(a) controle: vinculado em setor ativo que recebe da triagem continua sendo honrado, mesmo mais carregado")
    void vinculadoEmSetorQueRecebeEHonrado() {
        ligarSetores();
        ligarVinculado();
        UUID vendas = criarSetor("Vendas", true, true);
        UUID vinculado = criarAtendente("VINCULADO", vendas);
        UUID outro = criarAtendente("OUTRO", vendas);
        criarAbertos("vinculado", vinculado, 3);
        UUID atendimento = criarAtendimentoNaIa("em-setor");
        vincular(leadDe(atendimento), vinculado);

        assertThat(transferirProximo(atendimento, "a-2", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(dono(atendimento)).isEqualTo(vinculado).isNotEqualTo(outro);
    }

    @Test
    @DisplayName("(a) vinculado em setor inativo ou que nao recebe da triagem nao e honrado")
    void vinculadoEmSetorInativoOuQueNaoRecebeNaoEHonrado() {
        ligarSetores();
        ligarVinculado();
        UUID vendas = criarSetor("Vendas", true, true);
        UUID inativo = criarSetor("Inativo", true, false);
        UUID diretoria = criarSetor("Diretoria", false, true);
        UUID vendedor = criarAtendente("VENDEDOR", vendas);
        for (UUID setorDoVinculado : List.of(inativo, diretoria)) {
            UUID vinculado = criarAtendente("VINCULADO", setorDoVinculado);
            UUID atendimento = criarAtendimentoNaIa("fora-" + sequencia.incrementAndGet());
            vincular(leadDe(atendimento), vinculado);

            ResponseEntity<String> resposta = transferirProximo(atendimento, "a-3-" + sequencia.incrementAndGet(), null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(dono(atendimento)).isEqualTo(vendedor);
        }
    }

    @Test
    @DisplayName("(a) setores desligados: nada muda — vinculado sem setor continua sendo honrado")
    void setoresDesligadosNaoMudaNada() {
        ligarVinculado();
        UUID vinculado = criarAtendente("VINCULADO", null);
        UUID outro = criarAtendente("OUTRO", null);
        criarAbertos("vinculado", vinculado, 3);
        UUID atendimento = criarAtendimentoNaIa("desligado");
        vincular(leadDe(atendimento), vinculado);

        assertThat(transferirProximo(atendimento, "a-4", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(dono(atendimento)).isEqualTo(vinculado).isNotEqualTo(outro);
    }

    @Test
    @DisplayName("(a) o rodizio em si nao mudou: com setores ligados, usuario sem setor continua elegivel a ele")
    void rodizioContinuaElegivelSemSetor() {
        ligarSetores();
        UUID semSetor = criarAtendente("SEM-SETOR", null);
        UUID atendimento = criarAtendimentoNaIa("rodizio");

        assertThat(transferirProximo(atendimento, "a-5", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(dono(atendimento)).isEqualTo(semSetor);
    }

    // --- (b) quem responde pelo lead vira o vinculado --------------------------------------------------------------

    @Test
    @DisplayName("(b) o dono que envia mensagem sem ser participante vira o vinculado")
    void donoQueEnviaViraVinculado() {
        ligarVinculado();
        UUID lead = leadComAtendimentoDe("dono-envia", ana);

        ResponseEntity<String> resposta = enviarComo(EMAIL_ANA, lead, "Oi, aqui e a Ana");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(vinculoDe(lead)).isEqualTo(ana);
        assertThat(origemDoVinculo(lead)).isEqualTo("ENVIO_MANUAL");
        assertThat(dono(lead)).as("o envio nao trocou o responsavel").isEqualTo(ana);
    }

    @Test
    @DisplayName("(b) chave desligada: o dono que envia nao grava nada")
    void chaveDesligadaDonoNaoGrava() {
        UUID lead = leadComAtendimentoDe("dono-desligado", ana);

        assertThat(enviarComo(EMAIL_ANA, lead, "Oi").getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(vinculoDe(lead)).isNull();
    }

    @Test
    @DisplayName("(b) negativo: colaborador convidado que fala nao vira o vinculado e nao leva o lead")
    void colaboradorConvidadoNaoViraVinculado() {
        ligarVinculado();
        UUID lead = leadComAtendimentoDe("colaborador", ana);
        jdbc.update(
                "INSERT INTO atendimento_participante (atendimento_id, usuario_id, origem) VALUES (?, ?, 'CONVITE')",
                atendimentoDoLead(lead), bruno);

        ResponseEntity<String> resposta = enviarComo(EMAIL_BRUNO, lead, "Bruno ajudando");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"transferiuOLead\":false");
        assertThat(dono(lead)).isEqualTo(ana);
        assertThat(vinculoDe(lead)).isNull();
    }

    @Test
    @DisplayName("(b) abrir contato novo pela Agenda sem mensagem e ficar como responsavel grava o vinculo")
    void novoContatoSemMensagemGrava() {
        ligarVinculado();

        ResponseEntity<String> resposta = novoContatoComo(EMAIL_ANA, telefone(), PREFIXO + "novo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID lead = UUID.fromString(campo(resposta, "leadId"));
        assertThat(dono(lead)).isEqualTo(ana);
        assertThat(vinculoDe(lead)).isEqualTo(ana);
        assertThat(origemDoVinculo(lead)).isEqualTo("CONTATO_ABERTO");
    }

    @Test
    @DisplayName("(b) abrir atendimento para lead existente que ja e seu grava o vinculo; chave desligada, nao")
    void abrirParaLeadExistenteGrava() {
        UUID desligado = criarLead("abrir-desligado", ana, "EM_ATENDIMENTO");
        assertThat(abrirParaLeadExistenteComo(EMAIL_ANA, desligado).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(vinculoDe(desligado)).isNull();

        ligarVinculado();
        UUID lead = criarLead("abrir", ana, "EM_ATENDIMENTO");

        assertThat(abrirParaLeadExistenteComo(EMAIL_ANA, lead).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(vinculoDe(lead)).isEqualTo(ana);
    }

    @Test
    @DisplayName("(b) negativo: colaborador que abre o contato de outro atendente sem mensagem nao vira vinculado")
    void colaboradorQueAbreContatoDeOutroNaoGrava() {
        ligarVinculado();
        String telefone = telefone();
        UUID lead = UUID.fromString(campo(novoContatoComo(EMAIL_ANA, telefone, PREFIXO + "da-ana"), "leadId"));
        jdbc.update("DELETE FROM lead_atendente_vinculado WHERE lead_id = ?", lead); // so a abertura do Bruno interessa

        ResponseEntity<String> resposta = novoContatoComo(EMAIL_BRUNO, telefone, PREFIXO + "tentativa");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(campo(resposta, "leadId")).isEqualTo(lead.toString());
        assertThat(dono(lead)).as("Bruno colabora; o responsavel segue sendo a Ana").isEqualTo(ana);
        assertThat(vinculoDe(lead)).isNull();
    }

    @Test
    @DisplayName("(b) negativo: abrir a ficha do lead nao grava vinculo nem muda o responsavel")
    void abrirAFichaNaoGrava() {
        ligarVinculado();
        UUID lead = leadComAtendimentoDe("ficha", ana);

        ResponseEntity<String> daAna = fichaComo(EMAIL_ANA, lead);
        ResponseEntity<String> doBruno = fichaComo(EMAIL_BRUNO, lead);

        assertThat(daAna.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(doBruno.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(vinculoDe(lead)).isNull();
        assertThat(dono(lead)).isEqualTo(ana);
    }

    @Test
    @DisplayName("negativo RN-CRM-01/02: ser o vinculado nao deixa abrir a ficha nem falar no lead de outro atendente")
    void vinculoNaoConcedeVisibilidadeNemEnvio() {
        ligarVinculado();
        UUID lead = leadComAtendimentoDe("sem-acesso", ana);
        vincular(lead, bruno);

        assertThat(fichaComo(EMAIL_BRUNO, lead).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviarComo(EMAIL_BRUNO, lead, "Posso falar?").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(dono(lead)).isEqualTo(ana);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Integer.class, atendimentoDoLead(lead)))
                .isZero();
    }

    // --- o caminho desligado nao paga nada -------------------------------------------------------------------------

    @Test
    @DisplayName("evento irrelevante nao abre transacao no listener do vinculo; o que casa abre so a da gravacao")
    void eventoIrrelevanteNaoAbreTransacao() {
        ligarVinculado();
        UUID lead = leadComAtendimentoDe("transacao", ana);
        UUID atendimento = atendimentoDoLead(lead);
        List<String> iniciadas = new ArrayList<>();
        TransactionExecutionListener ouvinte = new TransactionExecutionListener() {
            @Override
            public void beforeBegin(TransactionExecution transacao) {
                iniciadas.add(String.valueOf(transacao.getTransactionName()));
            }
        };
        List<AbstractPlatformTransactionManager> observados = gerenciadores.stream()
                .filter(AbstractPlatformTransactionManager.class::isInstance)
                .map(AbstractPlatformTransactionManager.class::cast)
                .toList();
        observados.forEach(g -> g.addListener(ouvinte));
        try {
            publicarEmTransacao(new EventoDeAtendimento.MensagemRecebida(lead, atendimento, UUID.randomUUID(), false, Instant.now()));
            publicarEmTransacao(new EventoDeAtendimento.AtendimentoFinalizado(lead, atendimento, ana, Instant.now()));
            assertThat(iniciadas).as("mensagem recebida e finalizacao").noneMatch(nome -> nome.contains("VinculoDoLead"));

            publicarEmTransacao(new EventoDeAtendimento.AtendimentoTransferido(
                    lead, "Lead", atendimento, null, bruno, null, OrigemEvento.AUTOMACAO, Instant.now()));
            assertThat(iniciadas)
                    .as("transferencia para humano: so a transacao da gravacao")
                    .anyMatch(nome -> nome.endsWith("VinculoDoLeadRepositorioJdbc.registrar"))
                    .noneMatch(nome -> nome.equals(TRANSACAO_DO_LISTENER));
        } finally {
            observados.forEach(g -> g.getTransactionExecutionListeners().remove(ouvinte));
        }
        assertThat(vinculoDe(lead)).isEqualTo(bruno);
    }

    @Test
    @DisplayName("chave desligada: transferencias da IA nao tocam em lead_atendente_vinculado (log de SQL do Postgres)")
    void chaveDesligadaNaoTocaATabelaDoVinculo() throws Exception {
        UUID a = criarAtendente("A", null);
        jdbc.execute("ALTER SYSTEM SET log_statement = 'all'");
        jdbc.execute("SELECT pg_reload_conf()");
        try {
            // A recarga da configuracao chega as conexoes do pool com atraso: sonda ate o log passar a registrar.
            Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).until(() -> {
                String sonda = "E231_SONDA_" + UUID.randomUUID().toString().replace("-", "");
                jdbc.queryForObject("SELECT '" + sonda + "'", String.class);
                return POSTGRES.getLogs().contains(sonda);
            });
            String marcaInicial = "E231_INICIO_" + UUID.randomUUID().toString().replace("-", "");
            String marcaFinal = "E231_FIM_" + UUID.randomUUID().toString().replace("-", "");
            jdbc.queryForObject("SELECT '" + marcaInicial + "'", String.class);
            for (int rodada = 0; rodada < 5; rodada++) {
                UUID atendimento = criarAtendimentoNaIa("log-" + rodada);
                assertThat(transferirProximo(atendimento, "log-" + rodada, null).getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(dono(atendimento)).isEqualTo(a);
            }
            jdbc.queryForObject("SELECT '" + marcaFinal + "'", String.class);
            // O log do container chega com algum atraso: espera a marca final aparecer antes de ler o trecho.
            Awaitility.await().atMost(Duration.ofSeconds(15)).until(() -> POSTGRES.getLogs().contains(marcaFinal));
            String logs = POSTGRES.getLogs();
            int inicio = logs.lastIndexOf(marcaInicial);
            int fim = logs.lastIndexOf(marcaFinal);
            assertThat(inicio).as("o log de SQL do Postgres precisa conter a marca inicial").isGreaterThanOrEqualTo(0);
            assertThat(fim).as("e a final").isGreaterThan(inicio);
            String trecho = logs.substring(inicio, fim);
            assertThat(trecho).as("o trecho contem as transferencias").contains("UPDATE atendimento");
            assertThat(trecho).as("nenhuma consulta ou escrita na tabela do vinculo com a chave desligada")
                    .doesNotContain("lead_atendente_vinculado");
        } finally {
            jdbc.execute("ALTER SYSTEM RESET log_statement");
            jdbc.execute("SELECT pg_reload_conf()");
        }
    }

    // --- apoio ---------------------------------------------------------------------------------------------------

    private void publicarEmTransacao(Object evento) {
        new TransactionTemplate(transacao).executeWithoutResult(status -> eventos.publishEvent(evento));
    }

    private void desligarTudo() {
        jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'setores'");
        jdbc.update("UPDATE configuracao_automacao SET valor = 'false' WHERE chave = ?", CHAVE_VINCULADO);
        CachesDeConfiguracao.aguardarSetores(flags, false);
        CachesDeConfiguracao.aguardarVinculado(vinculos, false);
    }

    private void ligarSetores() {
        jdbc.update("UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'setores'");
        CachesDeConfiguracao.aguardarSetores(flags, true);
    }

    private void ligarVinculado() {
        jdbc.update("UPDATE configuracao_automacao SET valor = 'true' WHERE chave = ?", CHAVE_VINCULADO);
        CachesDeConfiguracao.aguardarVinculado(vinculos, true);
    }

    private ResponseEntity<String> transferirProximo(UUID atendimento, String chave, Object corpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        headers.set("Idempotency-Key", chave);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/internal/v1/atendimentos/" + atendimento + "/transferir-proximo-humano",
                HttpMethod.POST,
                new HttpEntity<>(corpo, headers),
                String.class);
    }

    private ResponseEntity<String> enviarComo(String email, UUID lead, String conteudo) {
        return comoUsuario(email, HttpMethod.POST, "/api/v1/atendimentos/mensagens",
                Map.of("leadId", lead, "conteudo", conteudo));
    }

    private ResponseEntity<String> novoContatoComo(String email, String telefone, String nome) {
        return comoUsuario(email, HttpMethod.POST, "/api/v1/atendimentos/novo-contato",
                Map.of("nome", nome, "telefone", telefone));
    }

    private ResponseEntity<String> abrirParaLeadExistenteComo(String email, UUID lead) {
        return comoUsuario(email, HttpMethod.POST, "/api/v1/atendimentos/leads/" + lead + "/novo", null);
    }

    private ResponseEntity<String> fichaComo(String email, UUID lead) {
        return comoUsuario(email, HttpMethod.GET, "/api/v1/leads/" + lead, null);
    }

    private ResponseEntity<String> comoUsuario(String email, HttpMethod metodo, String url, Object corpo) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        cabecalhos.set("Idempotency-Key", "e231-" + UUID.randomUUID());
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private String campo(ResponseEntity<String> resposta, String nome) {
        try {
            return json.readTree(resposta.getBody()).path(nome).asText();
        } catch (Exception erro) {
            throw new AssertionError("corpo sem JSON: " + resposta.getBody(), erro);
        }
    }

    private String telefone() {
        long n = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L;
        return String.format("839%08d", n);
    }

    private UUID criarSetor(String nome, boolean recebeDaTriagem, boolean ativo) {
        return jdbc.queryForObject(
                "INSERT INTO setor (nome, recebe_da_triagem, ativo) VALUES (?, ?, ?) RETURNING id",
                UUID.class, PREFIXO + nome + sequencia.incrementAndGet(), recebeDaTriagem, ativo);
    }

    private UUID criarAtendente(String marcador, UUID setor) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE)",
                id, PREFIXO + marcador, id + "@e231.invalid", senha);
        jdbc.update("INSERT INTO disponibilidade_atendente_ia (atendente_id,disponivel_para_ia) VALUES (?,TRUE)", id);
        if (setor != null) {
            jdbc.update("INSERT INTO usuario_setor (usuario_id, setor_id) VALUES (?, ?)", id, setor);
        }
        return id;
    }

    private void criarAbertos(String marcador, UUID dono, int quantidade) {
        for (int indice = 0; indice < quantidade; indice++) {
            UUID lead = criarLead(marcador + "-aberto-" + indice, dono, "EM_ATENDIMENTO");
            insertAtendimento(lead, dono, "EM_ATENDIMENTO");
        }
    }

    private UUID criarAtendimentoNaIa(String marcador) {
        UUID lead = criarLead(marcador, null, "IA");
        return insertAtendimento(lead, null, "EM_IA");
    }

    private UUID leadComAtendimentoDe(String marcador, UUID dono) {
        UUID lead = criarLead(marcador, dono, "EM_ATENDIMENTO");
        insertAtendimento(lead, dono, "EM_ATENDIMENTO");
        return lead;
    }

    private UUID insertAtendimento(UUID lead, UUID dono, String status) {
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,?,?::status_atendimento,?)",
                atendimento, lead, dono, status, Timestamp.from(Instant.now()));
        return atendimento;
    }

    private UUID criarLead(String marcador, UUID dono, String status) {
        UUID lead = UUID.randomUUID();
        String telefone = "55619039" + String.format("%06d", sequencia.incrementAndGet());
        Timestamp agora = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,?,?::uuid,?::status_basico_lead,?,?)",
                lead, PREFIXO + marcador, telefone, dono, status, agora, agora);
        return lead;
    }

    private void vincular(UUID lead, UUID atendente) {
        jdbc.update(
                "INSERT INTO lead_atendente_vinculado (lead_id, atendente_id, origem) VALUES (?, ?, 'TRANSFERENCIA')"
                        + " ON CONFLICT (lead_id) DO UPDATE SET atendente_id = EXCLUDED.atendente_id",
                lead, atendente);
    }

    private UUID vinculoDe(UUID lead) {
        return jdbc.query("SELECT atendente_id FROM lead_atendente_vinculado WHERE lead_id = ?",
                        (rs, i) -> rs.getObject("atendente_id", UUID.class), lead)
                .stream().findFirst().orElse(null);
    }

    private String origemDoVinculo(UUID lead) {
        return jdbc.queryForObject("SELECT origem FROM lead_atendente_vinculado WHERE lead_id = ?", String.class, lead);
    }

    private UUID leadDe(UUID atendimento) {
        return jdbc.queryForObject("SELECT lead_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    private UUID atendimentoDoLead(UUID lead) {
        return jdbc.queryForObject(
                "SELECT id FROM atendimento WHERE lead_id = ? ORDER BY iniciado_em DESC LIMIT 1", UUID.class, lead);
    }

    /** Dono do atendimento (se o argumento e um atendimento) ou responsavel do lead (se e um lead). */
    private UUID dono(UUID id) {
        List<UUID> donoDoAtendimento = jdbc.query(
                "SELECT atendente_id FROM atendimento WHERE id = ?", (rs, i) -> rs.getObject(1, UUID.class), id);
        if (!donoDoAtendimento.isEmpty()) {
            return donoDoAtendimento.get(0);
        }
        return jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, id);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }
}
