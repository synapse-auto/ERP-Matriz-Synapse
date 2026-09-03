package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.app.seguranca.ApoioRls;
import com.synapse.crm.atendimento.application.IniciarNovoContatoUseCase;
import com.synapse.crm.atendimento.application.RegistrarMensagemRecebidaUseCase;
import com.synapse.crm.atendimento.application.painel.CartaoAtendimento;
import com.synapse.crm.atendimento.application.painel.ListarAtendimentosVisiveisUseCase;
import com.synapse.crm.atendimento.application.painel.VisaoAtendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;
import com.synapse.crm.sharedkernel.identidade.PapelUsuario;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * E142: cliente finalizado que volta a escrever precisa cair em Potenciais.
 *
 * <p>Desde a V59 (E145), {@code FINALIZADO} e visivel para qualquer atendente. Por isso "apareceu
 * na lista" nao prova a correcao — o controle (teste 3) e o que separa o grupo do escape da RLS.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class RetornoDoLeadFinalizadoIT extends PostgresIT {

    private static final String PREFIXO = "E142-";
    private static final String CONTEUDO = "oi, voltei";

    @Autowired
    private RegistrarMensagemRecebidaUseCase registrarRecebida;

    @Autowired
    private IniciarNovoContatoUseCase novoContato;

    @Autowired
    private LeadNoCaminhoDeMensagem leads;

    @Autowired
    private ListarAtendimentosVisiveisUseCase listarPainel;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    @Qualifier(Pools.CHAT_TRANSACTION_MANAGER) private PlatformTransactionManager gerenteDoChat;

    private TransactionTemplate transacaoDeChat;
    private UUID idAna;
    private UUID idBruno;

    @BeforeEach
    void preparar() {
        transacaoDeChat = new TransactionTemplate(gerenteDoChat);
        limpar();
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
    }

    @AfterEach
    void limparContexto() {
        ApoioRls.sair();
        limpar();
    }

    @Test
    @DisplayName("1. lead FINALIZADO com dono recebe mensagem: atendimento EM_IA e status_basico IA")
    void finalizadoQueVolta_abreEmIaEMarcaOLead() {
        UUID leadId = criarLead("dos-seis", idAna, "FINALIZADO");
        criarAtendimento(leadId, idAna, "FINALIZADO");

        var resultado = comoServico(() -> registrarRecebida.executar(entrada(leadId)));

        assertThat(resultado.abriuAtendimento()).isTrue();
        assertThat(resultado.atendimento().status()).isEqualTo(StatusAtendimento.EM_IA);
        assertThat(statusDoLead(leadId)).isEqualTo("IA");
        assertThat(donoDoLead(leadId)).isEqualTo(idAna);
        assertThat(contarAtendimentos(leadId)).isEqualTo(2);
    }

    @Test
    @DisplayName("2. o lead que voltou entra no grupo POTENCIAIS para quem nao e o dono anterior")
    void finalizadoQueVolta_apareceNoGrupoPotenciaisParaColega() throws Exception {
        UUID leadId = criarLead("que-voltou", idAna, "FINALIZADO");
        criarAtendimento(leadId, idAna, "FINALIZADO");
        var resultado = comoServico(() -> registrarRecebida.executar(entrada(leadId)));

        JsonNode cartao = cartaoDoLeadEm(EMAIL_BRUNO, "POTENCIAIS", leadId);

        assertThat(cartao.path("status").asText()).isEqualTo("EM_IA");
        assertThat(cartao.path("atendimentoId").asText())
                .isEqualTo(resultado.atendimento().id().toString());
        assertThat(cartoesDoGrupoComo(idBruno, VisaoAtendimento.POTENCIAIS)).anySatisfy(c -> {
            assertThat(c.leadId()).isEqualTo(leadId);
            assertThat(c.status()).isEqualTo(StatusAtendimento.EM_IA);
        });
    }

    @Test
    @DisplayName("3. controle: FINALIZADO que nao voltou nao entra em POTENCIAIS, embora a V59 o deixe visivel")
    void finalizadoQueNaoVoltou_naoEntraEmPotenciaisMasContinuaVisivel() throws Exception {
        UUID leadId = criarLead("so-finalizado", idAna, "FINALIZADO");
        criarAtendimento(leadId, idAna, "FINALIZADO");

        JsonNode potenciais = json.readTree(listarComo(EMAIL_BRUNO, "POTENCIAIS"));
        assertThat(idsDeLead(potenciais)).doesNotContain(leadId.toString());
        assertThat(cartoesDoGrupoComo(idBruno, VisaoAtendimento.POTENCIAIS))
                .noneMatch(c -> leadId.equals(c.leadId()));

        ResponseEntity<String> ficha = getLeadComo(EMAIL_BRUNO, leadId);
        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(ficha.getBody()).path("status").asText()).isEqualTo("FINALIZADO");
        assertThat(json.readTree(ficha.getBody()).path("atendenteResponsavelId").asText())
                .isEqualTo(idAna.toString());
    }

    @Test
    @DisplayName("4. atendimento humano aberto: status_basico permanece inalterado")
    void atendimentoAberto_naoViraIa() {
        UUID leadId = criarLead("em-curso", idAna, "EM_ATENDIMENTO");
        criarAtendimento(leadId, idAna, "EM_ATENDIMENTO");

        var resultado = comoServico(() -> registrarRecebida.executar(entrada(leadId)));

        assertThat(resultado.abriuAtendimento()).isFalse();
        assertThat(statusDoLead(leadId)).isEqualTo("EM_ATENDIMENTO");
        assertThat(donoDoLead(leadId)).isEqualTo(idAna);
    }

    @Test
    @DisplayName("5. lead novo criado pela mensagem continua nascendo IA")
    void leadNovoPelaMensagem_nasceIa() {
        String telefone = telefoneNacional();

        UUID leadId = comoServico(() -> transacaoDeChat.execute(status -> {
            UUID criado = leads.resolverPorTelefone(telefone, PREFIXO + "novo");
            registrarRecebida.executar(entrada(criado));
            return criado;
        }));

        assertThat(statusDoLead(leadId)).isEqualTo("IA");
        assertThat(donoDoLead(leadId)).isNull();
        assertThat(jdbc.queryForObject(
                        "SELECT status::text FROM atendimento WHERE lead_id = ?", String.class, leadId))
                .isEqualTo("EM_IA");
    }

    @Test
    @DisplayName("6. contato iniciado pelo atendente: resposta do cliente nao devolve o lead para a fila")
    void contatoIniciadoPeloAtendente_respostaNaoDevolveParaIa() {
        ApoioRls.entrarComo(idAna, PapelUsuario.ATENDENTE);
        String telefone = telefoneNacional();
        IniciarNovoContatoUseCase.Resultado iniciado =
                novoContato.executar(new IniciarNovoContatoUseCase.Pedido(PREFIXO + "Maria", telefone, null, null));
        ApoioRls.sair();

        assertThat(statusDoLead(iniciado.leadId())).isEqualTo("EM_ATENDIMENTO");
        assertThat(iniciado.atendimento().status()).isEqualTo(StatusAtendimento.EM_ATENDIMENTO);

        var resposta = comoServico(() -> registrarRecebida.executar(entrada(iniciado.leadId())));

        assertThat(resposta.abriuAtendimento()).isFalse();
        assertThat(resposta.atendimento().id()).isEqualTo(iniciado.atendimento().id());
        assertThat(statusDoLead(iniciado.leadId())).isEqualTo("EM_ATENDIMENTO");
        assertThat(donoDoLead(iniciado.leadId())).isEqualTo(idAna);
        assertThat(cartoesDoGrupoComo(idBruno, VisaoAtendimento.POTENCIAIS))
                .noneMatch(c -> iniciado.leadId().equals(c.leadId()));
    }

    private RegistrarMensagemRecebidaUseCase.MensagemRecebida entrada(UUID leadId) {
        return new RegistrarMensagemRecebidaUseCase.MensagemRecebida(leadId, null, null, CONTEUDO);
    }

    private <T> T comoServico(Supplier<T> acao) {
        ApoioRls.sair();
        return ContextoDeServico.buscarComo("teste-e142", acao);
    }

    private List<CartaoAtendimento> cartoesDoGrupoComo(UUID usuarioId, VisaoAtendimento visao) {
        ApoioRls.entrarComo(usuarioId, PapelUsuario.ATENDENTE);
        try {
            return listarPainel.executar(visao);
        } finally {
            ApoioRls.sair();
        }
    }

    private JsonNode cartaoDoLeadEm(String email, String visao, UUID leadId) throws Exception {
        JsonNode lista = json.readTree(listarComo(email, visao));
        for (JsonNode item : lista) {
            if (leadId.toString().equals(item.path("leadId").asText())) {
                return item;
            }
        }
        throw new AssertionError("lead " + leadId + " nao entrou no grupo " + visao);
    }

    private List<String> idsDeLead(JsonNode lista) {
        List<String> ids = new ArrayList<>();
        lista.forEach(item -> ids.add(item.path("leadId").asText()));
        return ids;
    }

    private String listarComo(String email, String visao) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        return ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=" + visao, String.class)
                .getBody();
    }

    private ResponseEntity<String> getLeadComo(String email, UUID leadId) {
        String token = ApoioAutenticacao.login(http, email, SENHA_ATENDENTE).accessToken();
        return ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/leads/" + leadId, String.class);
    }

    private void limpar() {
        jdbc.update(
                "DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update(
                """
                DELETE FROM mensagem WHERE atendimento_id IN (
                    SELECT a.id FROM atendimento a JOIN lead l ON l.id = a.lead_id
                     WHERE l.nome LIKE ?)
                """,
                PREFIXO + "%");
        jdbc.update(
                "DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico,"
                        + " ultima_interacao_em, ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, ?::status_basico_lead, now(), now())",
                id,
                PREFIXO + nome,
                dono,
                status);
        return id;
    }

    private UUID criarAtendimento(UUID leadId, UUID atendenteId, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, ?::status_atendimento, now())",
                id,
                leadId,
                atendenteId,
                status);
        return id;
    }

    private String statusDoLead(UUID leadId) {
        return jdbc.queryForObject("SELECT status_basico::text FROM lead WHERE id = ?", String.class, leadId);
    }

    private UUID donoDoLead(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, leadId);
    }

    private int contarAtendimentos(UUID leadId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, leadId);
    }

    private static String telefoneNacional() {
        long n = Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L;
        return String.format("839%08d", n);
    }
}
