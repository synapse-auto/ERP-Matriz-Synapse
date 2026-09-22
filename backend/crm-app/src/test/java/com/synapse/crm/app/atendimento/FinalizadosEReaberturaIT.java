package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class FinalizadosEReaberturaIT extends PostgresIT {

    private static final String MARCADOR = "E99-finalizados-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private RegistrarMensagemRecebidaUseCase registrarRecebida;

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)"
                        + " OR ator_id IN (SELECT id FROM usuario WHERE nome LIKE ?)",
                MARCADOR + "%",
                MARCADOR + "%");
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                MARCADOR + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN "
                        + "(SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                MARCADOR + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                MARCADOR + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", MARCADOR + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", MARCADOR + "%");
    }

    @Test
    void listaEInboxOrdenamPelaExistenciaDeAtendimentoAbertoEAtravessamAFronteira() throws Exception {
        UUID ana = usuario(EMAIL_ANA);
        UUID canal = canal();
        String token = tokenGestor();
        long contagemTodosAntes = json.readTree(get(token, "/api/v1/atendimentos/contagem").getBody())
                .path("TODOS")
                .asLong();

        UUID leadAberto = lead("aberto", ana, "EM_ATENDIMENTO", null);
        UUID atendimentoAberto = atendimento(
                leadAberto, canal, ana, "EM_ATENDIMENTO", Instant.parse("2026-08-01T10:00:00Z"), null);
        mensagem(atendimentoAberto, "ATENDENTE", ana, "aberto mais antigo", Instant.parse("2026-08-01T10:01:00Z"));

        UUID leadComHistoricoFinal = lead("historico-com-ativo", ana, "EM_ATENDIMENTO", null);
        UUID ativoDoHistorico = atendimento(
                leadComHistoricoFinal,
                canal,
                ana,
                "EM_ATENDIMENTO",
                Instant.parse("2026-08-02T10:00:00Z"),
                null);
        UUID finalMaisRecente = atendimento(
                leadComHistoricoFinal,
                canal,
                ana,
                "FINALIZADO",
                Instant.parse("2026-08-29T10:00:00Z"),
                Instant.parse("2026-08-29T11:00:00Z"));
        mensagem(finalMaisRecente, "ATENDENTE", ana, "historico recente", Instant.parse("2026-08-29T10:01:00Z"));

        UUID leadSemAberto = lead("sem-aberto", ana, "FINALIZADO", null);
        UUID finalizado = atendimento(
                leadSemAberto,
                canal,
                ana,
                "FINALIZADO",
                Instant.parse("2026-08-30T10:00:00Z"),
                Instant.parse("2026-08-30T11:00:00Z"));
        mensagem(finalizado, "ATENDENTE", ana, "finalizado mais recente", Instant.parse("2026-08-30T10:01:00Z"));

        // TODOS mostra somente leads com atendimento aberto; um histórico finalizado continua no
        // cartão quando o mesmo lead também possui atendimento ativo. O balcão de reativação fica em
        // FINALIZADOS; atendente já não possui a aba TODOS.
        JsonNode lista = json.readTree(get(token, "/api/v1/atendimentos?visao=TODOS").getBody());
        List<String> leads = valores(lista, "leadId");

        assertThat(leads).contains(leadAberto.toString(), leadComHistoricoFinal.toString());
        assertThat(leads).doesNotContain(leadSemAberto.toString());
        JsonNode cartaoComHistorico = encontrarPorLead(lista, leadComHistoricoFinal);
        assertThat(cartaoComHistorico.path("status").asText()).isEqualTo("FINALIZADO");
        assertThat(cartaoComHistorico.path("atendimentoAtivoId").asText())
                .isEqualTo(ativoDoHistorico.toString());

        JsonNode contagem = json.readTree(get(token, "/api/v1/atendimentos/contagem").getBody());
        // Medir o delta evita depender de dados compartilhados por outros testes. Somente os dois
        // leads com atendimento aberto entram em TODOS; o lead apenas finalizado fica em FINALIZADOS.
        assertThat(contagem.path("TODOS").asLong()).isEqualTo(contagemTodosAntes + 2);

        List<String> idsPaginados = percorrerInbox(token);
        assertThat(idsPaginados).doesNotHaveDuplicates();
        assertThat(idsPaginados).doesNotContain(finalizado.toString());
    }

    @Test
    void abrirParaLeadFinalizadoCriaHumanoSemMensagemEPreservaOFinalizado() throws Exception {
        UUID ana = usuario(EMAIL_ANA);
        UUID lead = lead(
                "reabrir-fora-da-janela",
                ana,
                "FINALIZADO",
                Instant.parse("2026-08-20T10:00:00Z"));
        UUID antigo = atendimento(
                lead,
                canal(),
                ana,
                "FINALIZADO",
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T11:00:00Z"));
        mensagem(antigo, "LEAD", null, "mensagem antiga do cliente", Instant.parse("2026-08-20T10:30:00Z"));
        int mensagensAntes = quantidade("mensagem", "atendimento_id = ?", antigo);

        ResponseEntity<String> resposta = post(
                token(EMAIL_ANA), "/api/v1/atendimentos/leads/" + lead + "/novo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode corpo = json.readTree(resposta.getBody());
        UUID novo = UUID.fromString(corpo.path("atendimentoId").asText());
        assertThat(novo).isNotEqualTo(antigo);
        assertThat(corpo.path("mensagemId").isNull()).isTrue();
        assertThat(corpo.path("leadCriado").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, antigo))
                .isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT finalizado_em IS NOT NULL FROM atendimento WHERE id = ?", Boolean.class, antigo))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, novo))
                .isEqualTo("EM_ATENDIMENTO");
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, novo))
                .isEqualTo(ana);
        assertThat(jdbc.queryForObject("SELECT finalizado_em FROM atendimento WHERE id = ?", Instant.class, novo))
                .isNull();
        assertThat(quantidade("mensagem", "atendimento_id IN (?, ?)", antigo, novo))
                .isEqualTo(mensagensAntes);
    }

    @Test
    void finalizacaoManualLiberaOLeadEPreservaODonoHistoricoDoAtendimento() {
        UUID bruno = usuario(EMAIL_BRUNO);
        UUID lead = lead("finaliza-manual", bruno, "EM_ATENDIMENTO", null);
        UUID aberto = atendimento(
                lead, canal(), bruno, "EM_ATENDIMENTO", Instant.parse("2026-08-20T10:00:00Z"), null);

        ResponseEntity<String> resposta =
                post(token(EMAIL_BRUNO), "/api/v1/atendimentos/" + aberto + "/finalizar");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusDoLead(lead)).isEqualTo("FINALIZADO");
        assertThat(responsavelDoLead(lead)).isNull();
        assertThat(statusDoAtendimento(aberto)).isEqualTo("FINALIZADO");
        assertThat(atendenteDoAtendimento(aberto)).isEqualTo(bruno);
        assertThat(eventosDeFinalizacao(aberto)).isEqualTo(1);
    }

    @Test
    void finalizacaoEmLoteLiberaOLeadEPreservaODonoHistoricoDoAtendimento() throws Exception {
        UUID atendente = atendenteDeTeste("lote");
        UUID lead = lead("finaliza-lote", atendente, "EM_ATENDIMENTO", null);
        UUID aberto = atendimento(
                lead, canal(), atendente, "EM_ATENDIMENTO", Instant.parse("2026-08-20T10:00:00Z"), null);

        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(tokenGestor());
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        // O filtro por atendente confina o lote ao usuario criado aqui; sem ele, o gestor
        // finalizaria atendimentos de outros testes no Postgres compartilhado.
        ResponseEntity<String> resposta = http.exchange(
                "/api/v1/atendimentos/finalizar-lote",
                HttpMethod.POST,
                new HttpEntity<>(java.util.Map.of("atendenteId", atendente.toString()), cabecalhos),
                String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(resposta.getBody()).path("finalizados").asInt()).isEqualTo(1);
        assertThat(statusDoLead(lead)).isEqualTo("FINALIZADO");
        assertThat(responsavelDoLead(lead)).isNull();
        assertThat(atendenteDoAtendimento(aberto)).isEqualTo(atendente);
        assertThat(eventosDeFinalizacao(aberto)).isEqualTo(1);
    }

    @Test
    void atendenteReabreLeadFinalizadoPorColegaEAssumeONovoCiclo() throws Exception {
        UUID bruno = usuario(EMAIL_BRUNO);
        UUID ana = usuario(EMAIL_ANA);
        UUID lead = lead("reativa-colega", bruno, "EM_ATENDIMENTO", null);
        UUID antigo = atendimento(
                lead, canal(), bruno, "EM_ATENDIMENTO", Instant.parse("2026-08-20T10:00:00Z"), null);
        mensagem(antigo, "LEAD", null, "conversa antiga do colega", Instant.parse("2026-08-20T10:30:00Z"));
        assertThat(post(token(EMAIL_BRUNO), "/api/v1/atendimentos/" + antigo + "/finalizar").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> resposta = post(
                token(EMAIL_ANA), "/api/v1/atendimentos/leads/" + lead + "/novo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID novo = UUID.fromString(json.readTree(resposta.getBody()).path("atendimentoId").asText());
        assertThat(novo).isNotEqualTo(antigo);
        assertThat(statusDoAtendimento(antigo)).isEqualTo("FINALIZADO");
        assertThat(atendenteDoAtendimento(antigo)).isEqualTo(bruno);
        assertThat(statusDoAtendimento(novo)).isEqualTo("EM_ATENDIMENTO");
        assertThat(atendenteDoAtendimento(novo)).isEqualTo(ana);
        assertThat(responsavelDoLead(lead)).isEqualTo(ana);
        assertThat(quantidade("atendimento_participante", "atendimento_id = ?", novo)).isZero();

        ResponseEntity<String> historico = get(
                token(EMAIL_ANA), "/api/v1/atendimentos/" + novo + "/mensagens");
        assertThat(historico.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historico.getBody()).contains("conversa antiga do colega");

        // Negativo: o dono do ciclo anterior nao carrega o ciclo novo entre os seus ativos.
        assertThat(ativosDe(token(EMAIL_BRUNO))).doesNotContain(lead.toString());
    }

    /**
     * Os nomes reproduzem o incidente relatado; nao ha regra por atendente — os dois casos percorrem
     * exatamente o mesmo fluxo com usuarios comuns criados pelo teste.
     */
    @ParameterizedTest(name = "cliente que estava com {0} nao volta automaticamente para {0}")
    @ValueSource(strings = {"Michael", "Michele"})
    void clienteQueVoltaDepoisDeFinalizadoNaoVoltaParaODonoAnterior(String nome) throws Exception {
        UUID donoAnterior = atendenteDeTeste(nome);
        UUID ana = usuario(EMAIL_ANA);
        UUID lead = lead("retorno-" + nome, donoAnterior, "EM_ATENDIMENTO", null);
        UUID antigo = atendimento(
                lead, canal(), donoAnterior, "EM_ATENDIMENTO", Instant.parse("2026-08-20T10:00:00Z"), null);
        String tokenDoDonoAnterior = token(emailDeTeste(nome));
        assertThat(post(tokenDoDonoAnterior, "/api/v1/atendimentos/" + antigo + "/finalizar").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        RegistrarMensagemRecebidaUseCase.Resultado retorno = ContextoDeServico.buscarComo(
                "teste-finalizados-retorno",
                () -> registrarRecebida.executar(
                        new RegistrarMensagemRecebidaUseCase.MensagemRecebida(lead, null, null, "voltei")));

        UUID novo = retorno.atendimento().id();
        assertThat(retorno.abriuAtendimento()).isTrue();
        assertThat(statusDoAtendimento(novo)).isEqualTo("EM_IA");
        assertThat(atendenteDoAtendimento(novo)).isNull();
        assertThat(statusDoLead(lead)).isEqualTo("IA");
        assertThat(responsavelDoLead(lead)).isNull();

        // Outra atendente assume a partir de Potenciais: o ciclo novo e dela, nao do dono anterior.
        assertThat(post(token(EMAIL_ANA), "/api/v1/atendimentos/leads/" + lead + "/novo").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(atendenteDoAtendimento(novo)).isEqualTo(ana);
        assertThat(responsavelDoLead(lead)).isEqualTo(ana);
        assertThat(atendenteDoAtendimento(antigo)).isEqualTo(donoAnterior);
        assertThat(ativosDe(tokenDoDonoAnterior)).doesNotContain(lead.toString());
        assertThat(ativosDe(token(EMAIL_ANA))).contains(lead.toString());
    }

    @Test
    void atendenteAbreAtendimentoEmAndamentoDeColegaSemDuplicar() throws Exception {
        UUID bruno = usuario(EMAIL_BRUNO);
        UUID lead = lead("aberto-colega", bruno, "EM_ATENDIMENTO", null);
        UUID aberto = atendimento(
                lead,
                canal(),
                bruno,
                "EM_ATENDIMENTO",
                Instant.parse("2026-08-20T10:00:00Z"),
                null);

        ResponseEntity<String> resposta = post(
                token(EMAIL_ANA), "/api/v1/atendimentos/leads/" + lead + "/novo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(quantidade("atendimento", "lead_id = ?", lead)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, aberto))
                .isEqualTo(bruno);
        assertThat(json.readTree(resposta.getBody()).path("atendimentoId").asText())
                .isEqualTo(aberto.toString());
        assertThat(jdbc.queryForObject(
                        "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead))
                .isEqualTo(bruno);
    }

    @Test
    void gestorReabreLeadFinalizadoDeOutroAtendenteESemDono() {
        UUID bruno = usuario(EMAIL_BRUNO);
        UUID gestor = usuario(EMAIL_GESTOR);
        UUID canal = canal();
        UUID leadDoBruno = lead("gestor-reabre-colega", bruno, "FINALIZADO", null);
        UUID leadSemDono = lead("gestor-reabre-sem-dono", null, "FINALIZADO", null);
        atendimento(
                leadDoBruno,
                canal,
                bruno,
                "FINALIZADO",
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T11:00:00Z"));
        atendimento(
                leadSemDono,
                canal,
                null,
                "FINALIZADO",
                Instant.parse("2026-08-20T12:00:00Z"),
                Instant.parse("2026-08-20T13:00:00Z"));

        assertThat(post(tokenGestor(), "/api/v1/atendimentos/leads/" + leadDoBruno + "/novo").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(post(tokenGestor(), "/api/v1/atendimentos/leads/" + leadSemDono + "/novo").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForList(
                        "SELECT atendente_responsavel_id FROM lead WHERE id IN (?, ?)",
                        UUID.class,
                        leadDoBruno,
                        leadSemDono))
                .containsExactlyInAnyOrder(bruno, gestor);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM atendimento WHERE lead_id IN (?, ?) AND status = 'EM_ATENDIMENTO'",
                        Integer.class,
                        leadDoBruno,
                        leadSemDono))
                .isEqualTo(2);
    }

    private List<String> percorrerInbox(String token) throws Exception {
        List<String> ids = new ArrayList<>();
        Set<String> cursores = new HashSet<>();
        String cursor = null;
        for (int pagina = 0; pagina < 300; pagina++) {
            String rota = "/api/v1/atendimentos/inbox?visao=TODOS&limite=1"
                    + (cursor == null ? "" : "&cursor=" + java.net.URLEncoder.encode(
                            cursor, java.nio.charset.StandardCharsets.UTF_8));
            JsonNode corpo = json.readTree(get(token, rota).getBody());
            for (JsonNode item : corpo.path("itens")) {
                String id = item.path("identificadorVisual").asText();
                if (!id.isBlank()) ids.add(id);
            }
            JsonNode proximo = corpo.path("proximoCursor");
            if (proximo.isNull() || proximo.asText().isBlank()) return ids;
            cursor = proximo.asText();
            assertThat(cursores.add(cursor)).as("cursor deve sempre avançar").isTrue();
        }
        throw new AssertionError("inbox não terminou em 300 páginas");
    }

    private UUID lead(String sufixo, UUID atendente, String status, Instant ultimaInteracao) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,status_basico,atendente_responsavel_id,ultima_interacao_em,"
                        + "ultima_mensagem_do_lead_em) "
                        + "VALUES (?, ?, ?::status_basico_lead, ?, ?, ?)",
                id,
                MARCADOR + sufixo,
                status,
                atendente,
                timestamp(ultimaInteracao),
                timestamp(ultimaInteracao));
        return id;
    }

    private UUID atendimento(
            UUID lead,
            UUID canal,
            UUID atendente,
            String status,
            Instant inicio,
            Instant fim) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status,iniciado_em,finalizado_em) "
                        + "VALUES (?, ?, ?, ?, ?::status_atendimento, ?, ?)",
                id,
                lead,
                canal,
                atendente,
                status,
                timestamp(inicio),
                timestamp(fim));
        return id;
    }

    private void mensagem(UUID atendimento, String remetenteTipo, UUID remetente, String texto, Instant quando) {
        jdbc.update(
                "INSERT INTO mensagem(id,atendimento_id,remetente_tipo,remetente_id,tipo,conteudo,enviado_em) "
                        + "VALUES (?, ?, ?::remetente_tipo, ?, 'TEXTO', ?, ?)",
                UUID.randomUUID(),
                atendimento,
                remetenteTipo,
                remetente,
                texto,
                timestamp(quando));
    }

    private UUID atendenteDeTeste(String nome) {
        UUID id = UUID.randomUUID();
        String senhaDeAtendente = jdbc.queryForObject(
                "SELECT senha_hash FROM usuario WHERE email = ?", String.class, EMAIL_ANA);
        jdbc.update(
                // senha_alterada_em preenchida: sem ela a senha e provisoria e o
                // SenhaProvisoriaFilter recusa toda rota com 403.
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo,senha_alterada_em) "
                        + "VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE,now())",
                id,
                MARCADOR + nome,
                emailDeTeste(nome),
                senhaDeAtendente);
        return id;
    }

    private static String emailDeTeste(String nome) {
        return "e99-finalizados-" + nome.toLowerCase(java.util.Locale.ROOT) + "@dev.invalid";
    }

    private List<String> ativosDe(String token) throws Exception {
        return valores(json.readTree(get(token, "/api/v1/atendimentos?visao=ATIVOS").getBody()), "leadId");
    }

    private String statusDoLead(UUID lead) {
        return jdbc.queryForObject("SELECT status_basico::text FROM lead WHERE id = ?", String.class, lead);
    }

    private UUID responsavelDoLead(UUID lead) {
        return jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead);
    }

    private String statusDoAtendimento(UUID atendimento) {
        return jdbc.queryForObject("SELECT status::text FROM atendimento WHERE id = ?", String.class, atendimento);
    }

    private UUID atendenteDoAtendimento(UUID atendimento) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    /** Timeline e escrita apos o commit; espera por condicao em vez de afirmar de imediato. */
    private int eventosDeFinalizacao(UUID atendimento) {
        String condicao = "atendimento_id = ? AND tipo = 'ATENDIMENTO_FINALIZADO'";
        await().atMost(Duration.ofSeconds(5))
                .until(() -> quantidade("evento_timeline", condicao, atendimento) > 0);
        return quantidade("evento_timeline", condicao, atendimento);
    }

    private UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID canal() {
        return jdbc.queryForObject("SELECT id FROM canal ORDER BY id LIMIT 1", UUID.class);
    }

    private String token(String email) {
        return ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
    }

    private String tokenGestor() {
        return ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
    }

    private ResponseEntity<String> get(String token, String rota) {
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, rota, String.class);
    }

    private ResponseEntity<String> post(String token, String rota) {
        return ApoioAutenticacao.comToken(http, token, HttpMethod.POST, rota, String.class);
    }

    private JsonNode encontrarPorLead(JsonNode lista, UUID lead) {
        return java.util.stream.StreamSupport.stream(lista.spliterator(), false)
                .filter(item -> lead.toString().equals(item.path("leadId").asText()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> valores(JsonNode lista, String campo) {
        return java.util.stream.StreamSupport.stream(lista.spliterator(), false)
                .map(item -> item.path(campo).asText())
                .toList();
    }

    private int quantidade(String tabela, String condicao, Object... parametros) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE " + condicao, Integer.class, parametros);
    }

    private Timestamp timestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
