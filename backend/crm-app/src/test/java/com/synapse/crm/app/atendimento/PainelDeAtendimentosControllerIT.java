package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

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
