package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

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

    @AfterEach
    void limpar() {
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN "
                        + "(SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                MARCADOR + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                MARCADOR + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", MARCADOR + "%");
    }

    @Test
    void listaEInboxOrdenamPelaExistenciaDeAtendimentoAbertoEAtravessamAFronteira() throws Exception {
        UUID ana = usuario(EMAIL_ANA);
        UUID canal = canal();

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

        String token = token(EMAIL_ANA);
        JsonNode lista = json.readTree(get(token, "/api/v1/atendimentos?visao=TODOS").getBody());
        List<String> leads = valores(lista, "leadId");

        assertThat(leads.indexOf(leadAberto.toString())).isLessThan(leads.indexOf(leadSemAberto.toString()));
        assertThat(leads.indexOf(leadComHistoricoFinal.toString()))
                .isLessThan(leads.indexOf(leadSemAberto.toString()));
        JsonNode cartaoComHistorico = encontrarPorLead(lista, leadComHistoricoFinal);
        assertThat(cartaoComHistorico.path("status").asText()).isEqualTo("FINALIZADO");
        assertThat(cartaoComHistorico.path("atendimentoAtivoId").asText())
                .isEqualTo(ativoDoHistorico.toString());

        JsonNode contagem = json.readTree(get(token, "/api/v1/atendimentos/contagem").getBody());
        assertThat(contagem.path("TODOS").asLong()).isEqualTo(lista.size());

        List<String> idsPaginados = percorrerInbox(token);
        assertThat(idsPaginados).doesNotHaveDuplicates();
        assertThat(idsPaginados.indexOf(atendimentoAberto.toString()))
                .isLessThan(idsPaginados.indexOf(finalizado.toString()));
        assertThat(idsPaginados.indexOf(finalMaisRecente.toString()))
                .isLessThan(idsPaginados.indexOf(finalizado.toString()));
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
    void atendenteRecebe404ParaLeadFinalizadoDeColegaESemEfeitoColateral() {
        UUID bruno = usuario(EMAIL_BRUNO);
        UUID lead = lead("invisivel", bruno, "FINALIZADO", null);
        UUID antigo = atendimento(
                lead,
                canal(),
                bruno,
                "FINALIZADO",
                Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-20T11:00:00Z"));

        ResponseEntity<String> resposta = post(
                token(EMAIL_ANA), "/api/v1/atendimentos/leads/" + lead + "/novo");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(quantidade("atendimento", "lead_id = ?", lead)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, antigo))
                .isEqualTo(bruno);
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
                .containsOnly(gestor);
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
