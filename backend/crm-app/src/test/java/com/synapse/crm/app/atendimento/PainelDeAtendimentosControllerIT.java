package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * {@code GET /api/v1/atendimentos?visao=} ponta a ponta — o endpoint que faltava para a lista de
 * conversas da E11. Cada teste representa um agrupamento (RF-CRM-20/21) e o par negativo de RLS que
 * garante que o parametro {@code visao} nao vira porta lateral para a RN-CRM-01.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class PainelDeAtendimentosControllerIT extends PostgresIT {

    private static final String PREFIXO = "E11-painel-";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private UUID idAna;
    private UUID idBruno;

    private UUID atendimentoAtivoDaAna;
    private UUID atendimentoPendenteDaAna;
    private UUID atendimentoPendenteDoBruno;
    private UUID atendimentoPotencial;

    @BeforeEach
    void prepararCenario() {
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        UUID leadAtivoDaAna = criarLead("Ativo Ana " + sufixo, idAna, "EM_ATENDIMENTO");
        atendimentoAtivoDaAna = criarAtendimento(leadAtivoDaAna, idAna, "EM_ATENDIMENTO");
        inserirMensagem(atendimentoAtivoDaAna, "ATENDENTE", idAna, "resposta da Ana");

        UUID leadPendenteDaAna = criarLead("Pendente Ana " + sufixo, idAna, "EM_ATENDIMENTO");
        atendimentoPendenteDaAna = criarAtendimento(leadPendenteDaAna, idAna, "EM_ATENDIMENTO");
        inserirMensagem(atendimentoPendenteDaAna, "LEAD", null, "pergunta do lead");

        UUID leadPendenteDoBruno = criarLead("Pendente Bruno " + sufixo, idBruno, "EM_ATENDIMENTO");
        atendimentoPendenteDoBruno = criarAtendimento(leadPendenteDoBruno, idBruno, "EM_ATENDIMENTO");
        inserirMensagem(atendimentoPendenteDoBruno, "LEAD", null, "pergunta para o Bruno");

        UUID leadPotencial = criarLead("Potencial " + sufixo, null, "IA");
        atendimentoPotencial = criarAtendimento(leadPotencial, null, "EM_IA");
    }

    @AfterEach
    void limpar() {
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

    @Test
    @DisplayName("ATIVOS: atendente ve so os proprios EM_ATENDIMENTO")
    void ativos_atendente_veApenasOsProprios() {
        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS");

        assertThat(corpo).contains(atendimentoAtivoDaAna.toString());
        assertThat(corpo).contains(atendimentoPendenteDaAna.toString());
        assertThat(corpo).doesNotContain(atendimentoPendenteDoBruno.toString());
    }

    @Test
    @DisplayName("PENDENTES: atendente ve so os proprios com ultima mensagem do lead")
    void pendentes_atendente_veApenasOsProprios() {
        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES");

        assertThat(corpo).contains(atendimentoPendenteDaAna.toString());
        assertThat(corpo).doesNotContain(atendimentoAtivoDaAna.toString());
        assertThat(corpo).doesNotContain(atendimentoPendenteDoBruno.toString());
    }

    @Test
    @DisplayName("PENDENTES: gestor ve de todos, nao so os proprios")
    void pendentes_gestor_veDeTodos() {
        String corpo = listarComo(EMAIL_GESTOR, SENHA_GESTOR, "PENDENTES");

        assertThat(corpo).contains(atendimentoPendenteDaAna.toString());
        assertThat(corpo).contains(atendimentoPendenteDoBruno.toString());
    }

    @Test
    @DisplayName("POTENCIAIS: sem dono, aparece para qualquer atendente")
    void potenciais_apareceParaQualquerAtendente() {
        String corpo = listarComo(EMAIL_BRUNO, SENHA_ATENDENTE, "POTENCIAIS");

        assertThat(corpo).contains(atendimentoPotencial.toString());
    }

    @Test
    @DisplayName("cartao informa o tipo real do canal")
    void cartao_informaCanalDoAtendimento() {
        UUID canalWhatsapp = jdbc.queryForObject(
                "SELECT id FROM canal WHERE tipo = 'WHATSAPP' ORDER BY id LIMIT 1", UUID.class);
        jdbc.update(
                "UPDATE atendimento SET canal_id = ? WHERE id = ?",
                canalWhatsapp,
                atendimentoAtivoDaAna);

        String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS");

        assertThat(corpo)
                .contains(atendimentoAtivoDaAna.toString())
                .contains("\"canalTipo\":\"WHATSAPP\"");
    }

    @Test
    @DisplayName("TODOS: gestor ve tudo; atendente pedindo TODOS nao contorna a RN-CRM-01")
    void todos_gestorVeTudoAtendenteNao() {
        String comoGestor = listarComo(EMAIL_GESTOR, SENHA_GESTOR, "TODOS");
        assertThat(comoGestor)
                .contains(atendimentoAtivoDaAna.toString())
                .contains(atendimentoPendenteDoBruno.toString())
                .contains(atendimentoPotencial.toString());

        String comoAna = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "TODOS");
        assertThat(comoAna).doesNotContain(atendimentoPendenteDoBruno.toString());
    }

    @Nested
    @DisplayName("nao lidas")
    class NaoLidas {

        @Test
        @DisplayName("cartao conta somente mensagens do lead posteriores a leitura")
        void cartao_contaSomenteMensagensDoLeadDepoisDaLeitura() throws Exception {
            JsonNode pendente = cartao(
                    listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"),
                    atendimentoPendenteDaAna);
            JsonNode ativo = cartao(
                    listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS"),
                    atendimentoAtivoDaAna);

            assertThat(pendente.path("naoLidas").asLong()).isEqualTo(1);
            assertThat(ativo.path("naoLidas").asLong()).isZero();
        }

        @Test
        @DisplayName("gestor abre conversa alheia e marca somente a propria leitura")
        void gestor_abreConversaAlheia_marcaSomenteSuaLeitura() throws Exception {
            ResponseEntity<String> resposta = marcarComoLidoComo(
                    EMAIL_GESTOR, SENHA_GESTOR, atendimentoPendenteDaAna);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM atendimento_leitura"
                                    + " WHERE atendimento_id = ? AND usuario_id = ?",
                            Integer.class,
                            atendimentoPendenteDaAna,
                            idDoUsuario(EMAIL_GESTOR)))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM atendimento_leitura"
                                    + " WHERE atendimento_id = ? AND usuario_id = ?",
                            Integer.class,
                            atendimentoPendenteDaAna,
                            idAna))
                    .isZero();
            assertThat(jdbc.queryForObject(
                            "SELECT lido_ate IS NULL FROM atendimento WHERE id = ?",
                            Boolean.class,
                            atendimentoPendenteDaAna))
                    .isTrue();
            assertThat(cartao(
                                    listarComo(EMAIL_GESTOR, SENHA_GESTOR, "PENDENTES"),
                                    atendimentoPendenteDaAna)
                            .path("naoLidas")
                            .asLong())
                    .isZero();
            assertThat(cartao(
                                    listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"),
                                    atendimentoPendenteDaAna)
                            .path("naoLidas")
                            .asLong())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("responsavel abre conversa e zera as nao lidas existentes")
        void responsavel_abreConversa_marcaComoLida() throws Exception {
            ResponseEntity<String> resposta = marcarComoLidoComo(
                    EMAIL_ANA, SENHA_ATENDENTE, atendimentoPendenteDaAna);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM atendimento_leitura"
                                    + " WHERE atendimento_id = ? AND usuario_id = ?",
                            Integer.class,
                            atendimentoPendenteDaAna,
                            idAna))
                    .isEqualTo(1);
            assertThat(cartao(
                                    listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"),
                                    atendimentoPendenteDaAna)
                            .path("naoLidas")
                            .asLong())
                    .isZero();

            inserirMensagem(atendimentoPendenteDaAna, "LEAD", null, "nova pergunta depois da leitura");
            assertThat(cartao(
                                    listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"),
                                    atendimentoPendenteDaAna)
                            .path("naoLidas")
                            .asLong())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("usuario pode marcar atendimento em IA sem responsavel")
        void atendimentoEmIa_podeSerMarcadoPorQuemAbre() throws Exception {
            ResponseEntity<String> resposta = marcarComoLidoComo(
                    EMAIL_BRUNO, SENHA_ATENDENTE, atendimentoPotencial);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM atendimento_leitura"
                                    + " WHERE atendimento_id = ? AND usuario_id = ?",
                            Integer.class,
                            atendimentoPotencial,
                            idBruno))
                    .isEqualTo(1);
            assertThat(cartao(
                                    listarComo(EMAIL_BRUNO, SENHA_ATENDENTE, "POTENCIAIS"),
                                    atendimentoPotencial)
                            .path("naoLidas")
                            .asLong())
                    .isZero();
        }
    }

    /**
     * {@code GET /api/v1/atendimentos/contagem} (E17b §Bloco 6) — os badges das abas. O teste
     * negativo do enunciado: contagem pedida por atendente devolve o numero restrito, gestor devolve
     * o total. Comparacao relativa, e nao numero fixo, porque a suite roda contra o mesmo Postgres
     * que outras IT (e o seed de demonstracao) tambem povoam — o que importa e que Ana nunca alcanca
     * o que so o gestor alcanca, nao a contagem exata de um instante.
     */
    @Nested
    @DisplayName("GET /api/v1/atendimentos/contagem")
    class Contagem {

        @Test
        @DisplayName("PENDENTES: atendente recebe numero restrito, gestor recebe o total")
        void pendentes_atendenteRestritoGestorTotal() {
            long paraAna = contarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES");
            long paraGestor = contarComo(EMAIL_GESTOR, SENHA_GESTOR, "PENDENTES");

            assertThat(paraAna).isLessThan(paraGestor);
        }

        @Test
        @DisplayName("TODOS: atendente recebe numero restrito, gestor recebe o total")
        void todos_atendenteRestritoGestorTotal() {
            long paraAna = contarComo(EMAIL_ANA, SENHA_ATENDENTE, "TODOS");
            long paraGestor = contarComo(EMAIL_GESTOR, SENHA_GESTOR, "TODOS");

            assertThat(paraAna).isLessThan(paraGestor);
        }

        @Test
        @DisplayName("a contagem por visao bate com o tamanho da listagem da mesma visao")
        void contagem_bateComOTamanhoDaListagem() {
            String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();

            long contagem = contarComToken(token, "PENDENTES");
            String listagem = ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=PENDENTES", String.class)
                    .getBody();

            assertThat(contagem).isEqualTo(quantidadeDeCartoes(listagem));
        }

        @Test
        @DisplayName("sem autenticacao, devolve 401")
        void semAutenticacao_devolve401() {
            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/atendimentos/contagem", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        private long contarComo(String email, String senha, String visao) {
            String token = ApoioAutenticacao.login(http, email, senha).accessToken();
            return contarComToken(token, visao);
        }

        private long contarComToken(String token, String visaoQualquer) {
            String corpo = ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class)
                    .getBody();
            return Long.parseLong(corpo.replaceAll(".*\"" + visaoQualquer + "\":(\\d+).*", "$1"));
        }

        private int quantidadeDeCartoes(String corpoJson) {
            return corpoJson.split("\"atendimentoId\"", -1).length - 1;
        }
    }

    // --- apoio ------------------------------------------------------------

    private String listarComo(String email, String senha, String visao) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=" + visao, String.class)
                .getBody();
    }

    private ResponseEntity<String> marcarComoLidoComo(
            String email, String senha, UUID atendimentoId) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(
                http,
                token,
                HttpMethod.POST,
                "/api/v1/atendimentos/" + atendimentoId + "/leitura",
                String.class);
    }

    private JsonNode cartao(String corpo, UUID atendimentoId) throws Exception {
        for (JsonNode item : json.readTree(corpo)) {
            if (atendimentoId.toString().equals(item.path("atendimentoId").asText())) {
                return item;
            }
        }
        throw new AssertionError("cartao nao encontrado: " + atendimentoId);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, UUID dono, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico)"
                        + " VALUES (?, ?, ?, ?::status_basico_lead)",
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

    private void inserirMensagem(
            UUID atendimentoId, String remetenteTipo, UUID remetenteId, String conteudo) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,"
                        + " conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, ?::remetente_tipo, ?, 'TEXTO'::tipo_mensagem, ?,"
                        + " 'ENVIADO'::status_entrega, now())",
                UUID.randomUUID(),
                atendimentoId,
                remetenteTipo,
                remetenteId,
                conteudo);
    }
}
