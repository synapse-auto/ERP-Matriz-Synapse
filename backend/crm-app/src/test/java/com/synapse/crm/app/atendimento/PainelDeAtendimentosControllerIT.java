package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
    @DisplayName("TODOS: gestor ve tudo; atendente pedindo TODOS recebe 403")
    void todos_gestorVeTudoAtendenteRecebe403() {
        String comoGestor = listarComo(EMAIL_GESTOR, SENHA_GESTOR, "TODOS");
        assertThat(comoGestor)
                .contains(atendimentoAtivoDaAna.toString())
                .contains(atendimentoPendenteDoBruno.toString())
                .contains(atendimentoPotencial.toString());

        ResponseEntity<String> comoAna = respostaListarComo(EMAIL_ANA, SENHA_ATENDENTE, "TODOS");
        assertThat(comoAna.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("um lead com tres atendimentos ocupa uma linha e a contagem acompanha a listagem")
    void umLeadComTresAtendimentosTemUmCartao() throws Exception {
        UUID lead = criarLead("Lead com tres atendimentos", idAna, "EM_ATENDIMENTO");
        UUID antigo = criarAtendimento(lead, idAna, "FINALIZADO");
        UUID intermediario = criarAtendimento(lead, idAna, "FINALIZADO");
        UUID ativo = criarAtendimento(lead, idAna, "EM_ATENDIMENTO");
        Instant base = Instant.parse("2026-08-20T10:00:00Z");
        definirInicio(antigo, base);
        definirInicio(intermediario, base.plusSeconds(10));
        definirInicio(ativo, base.plusSeconds(20));
        inserirMensagem(antigo, "LEAD", null, "historico antigo");
        inserirMensagem(intermediario, "LEAD", null, "historico intermediario");
        inserirMensagem(ativo, "LEAD", null, "historico atual");
        definirUltimaMensagem(antigo, base.plusSeconds(1));
        definirUltimaMensagem(intermediario, base.plusSeconds(11));
        definirUltimaMensagem(ativo, base.plusSeconds(21));

        JsonNode lista = json.readTree(listarComo(EMAIL_GESTOR, SENHA_GESTOR, "TODOS"));
        List<JsonNode> cartoesDoLead = new java.util.ArrayList<>();
        lista.forEach(cartao -> {
            if (lead.toString().equals(cartao.path("leadId").asText())) {
                cartoesDoLead.add(cartao);
            }
        });

        assertThat(cartoesDoLead).hasSize(1);
        assertThat(cartoesDoLead.getFirst().path("atendimentoId").asText())
                .isEqualTo(ativo.toString());
        assertThat(cartoesDoLead.getFirst().path("atendimentoAtivoId").asText())
                .isEqualTo(ativo.toString());
        String contagemJson = ApoioAutenticacao.comToken(
                        http,
                        ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken(),
                        HttpMethod.GET,
                        "/api/v1/atendimentos/contagem",
                        String.class)
                .getBody();
        assertThat(json.readTree(contagemJson).path("TODOS").asLong()).isEqualTo(lista.size());
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
        @DisplayName("abrir conversa zera nao lidas de todos os atendimentos do lead")
        void abrirConversa_marcaLeituraDeTodosOsAtendimentosDoLead() throws Exception {
            UUID lead = criarLead(
                    "Lead com historico " + UUID.randomUUID().toString().substring(0, 8),
                    idAna,
                    "EM_ATENDIMENTO");
            UUID encerrado = criarAtendimento(lead, idAna, "FINALIZADO");
            UUID aberto = criarAtendimento(lead, idAna, "EM_ATENDIMENTO");
            inserirMensagem(encerrado, "LEAD", null, "mensagem do atendimento encerrado");
            inserirMensagem(aberto, "LEAD", null, "mensagem do atendimento aberto");

            JsonNode cartaoAntes = cartao(listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS"), aberto);
            assertThat(cartaoAntes.path("naoLidas").asLong()).isEqualTo(2);

            ResponseEntity<String> resposta = marcarComoLidoComo(EMAIL_ANA, SENHA_ATENDENTE, aberto);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM atendimento_leitura WHERE usuario_id = ? AND atendimento_id IN (?, ?)",
                            Integer.class,
                            idAna,
                            encerrado,
                            aberto))
                    .isEqualTo(2);
            assertThat(cartao(listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS"), aberto)
                            .path("naoLidas")
                            .asLong())
                    .isZero();
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
     * negativo do enunciado: a contagem pedida por atendente omite a visao TODOS, enquanto a de
     * gestao continua incluindo-a.
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
        @DisplayName("TODOS: atendente nao recebe a chave, gestor recebe o total")
        void todos_atendenteNaoRecebeChaveGestorRecebe() throws Exception {
            String paraAna = corpoContagem(EMAIL_ANA, SENHA_ATENDENTE);
            String paraGestor = corpoContagem(EMAIL_GESTOR, SENHA_GESTOR);

            assertThat(json.readTree(paraAna).has("TODOS")).isFalse();
            assertThat(json.readTree(paraGestor).has("TODOS")).isTrue();
        }

        private long contarComo(String email, String senha, String visao) {
            String token = ApoioAutenticacao.login(http, email, senha).accessToken();
            String corpo = ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class)
                    .getBody();
            return Long.parseLong(corpo.replaceAll(".*\"" + visao + "\":(\\d+).*", "$1"));
        }

        @Test
        @DisplayName("a contagem de cada visao bate com o tamanho da listagem")
        void contagem_bateComOTamanhoDaListagem() throws Exception {
            assertContagemBateComListagem(
                    EMAIL_ANA,
                    SENHA_ATENDENTE,
                    List.of("ATIVOS", "PENDENTES", "POTENCIAIS", "FINALIZADOS"));
            assertContagemBateComListagem(
                    EMAIL_GESTOR,
                    SENHA_GESTOR,
                    List.of("TODOS", "ATIVOS", "PENDENTES", "POTENCIAIS", "FINALIZADOS"));
        }

        @Test
        @DisplayName("sem autenticacao, devolve 401")
        void semAutenticacao_devolve401() {
            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/atendimentos/contagem", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        private String corpoContagem(String email, String senha) {
            String token = ApoioAutenticacao.login(http, email, senha).accessToken();
            return ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class)
                    .getBody();
        }

        private void assertContagemBateComListagem(
                String email, String senha, List<String> visoes) throws Exception {
            String token = ApoioAutenticacao.login(http, email, senha).accessToken();
            String corpo = ApoioAutenticacao.comToken(
                    http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class).getBody();
            JsonNode contagens = json.readTree(corpo);
            for (String visao : visoes) {
                String listagem = ApoioAutenticacao.comToken(
                                http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=" + visao, String.class)
                        .getBody();
                assertThat(contagens.path(visao).asLong())
                        .as("visao %s para %s", visao, email)
                        .isEqualTo(quantidadeDeCartoes(listagem));
            }
        }

        private int quantidadeDeCartoes(String corpoJson) {
            return corpoJson.split("\"atendimentoId\"", -1).length - 1;
        }
    }

    @Nested
    @DisplayName("FINALIZADOS (E136)")
    class Finalizados {

        private UUID leadFinalizadoDaAna;
        private UUID atendimentoFinalizadoDaAna;
        private UUID leadFinalizadoDoBruno;
        private UUID atendimentoFinalizadoDoBruno;
        private UUID leadComHistoricoEAberto;
        private UUID atendimentoAbertoDoHistorico;

        @BeforeEach
        void prepararFinalizados() {
            Instant base = Instant.parse("2026-09-01T10:00:00Z");

            leadFinalizadoDaAna = criarLead("Finalizado Ana", idAna, "FINALIZADO");
            atendimentoFinalizadoDaAna = criarAtendimento(leadFinalizadoDaAna, idAna, "FINALIZADO");
            definirInicio(atendimentoFinalizadoDaAna, base);
            inserirMensagem(atendimentoFinalizadoDaAna, "ATENDENTE", idAna, "encerrado pela Ana");
            definirUltimaMensagem(atendimentoFinalizadoDaAna, base.plusSeconds(1));

            leadFinalizadoDoBruno = criarLead("Finalizado Bruno", idBruno, "FINALIZADO");
            atendimentoFinalizadoDoBruno = criarAtendimento(leadFinalizadoDoBruno, idBruno, "FINALIZADO");
            definirInicio(atendimentoFinalizadoDoBruno, base.plusSeconds(10));
            inserirMensagem(atendimentoFinalizadoDoBruno, "ATENDENTE", idBruno, "encerrado pelo Bruno");
            definirUltimaMensagem(atendimentoFinalizadoDoBruno, base.plusSeconds(11));

            leadComHistoricoEAberto = criarLead("Historico com aberto", idAna, "EM_ATENDIMENTO");
            UUID historico = criarAtendimento(leadComHistoricoEAberto, idAna, "FINALIZADO");
            atendimentoAbertoDoHistorico = criarAtendimento(leadComHistoricoEAberto, idAna, "EM_ATENDIMENTO");
            definirInicio(historico, base.plusSeconds(20));
            definirInicio(atendimentoAbertoDoHistorico, base.plusSeconds(30));
            inserirMensagem(historico, "LEAD", null, "mensagem antiga");
            inserirMensagem(atendimentoAbertoDoHistorico, "ATENDENTE", idAna, "ainda aberto");
            definirUltimaMensagem(historico, base.plusSeconds(21));
            definirUltimaMensagem(atendimentoAbertoDoHistorico, base.plusSeconds(31));
        }

        @Test
        @DisplayName("atendente ve finalizados de qualquer colega para poder reativar")
        void atendente_veFinalizadosDeQualquerColega() {
            String corpo = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "FINALIZADOS");

            assertThat(corpo).contains(atendimentoFinalizadoDaAna.toString());
            assertThat(corpo).contains(atendimentoFinalizadoDoBruno.toString());
            assertThat(corpo).doesNotContain(atendimentoAbertoDoHistorico.toString());
            assertThat(corpo).doesNotContain(leadComHistoricoEAberto.toString());
        }

        @Test
        @DisplayName("gestor ve finalizados de outros atendentes tambem")
        void gestor_veDeTodos() {
            String corpo = listarComo(EMAIL_GESTOR, SENHA_GESTOR, "FINALIZADOS");

            assertThat(corpo).contains(atendimentoFinalizadoDaAna.toString());
            assertThat(corpo).contains(atendimentoFinalizadoDoBruno.toString());
            assertThat(corpo).doesNotContain(atendimentoAbertoDoHistorico.toString());
        }

        @Test
        @DisplayName("lead com finalizado e outro aberto nao entra em FINALIZADOS e permanece em ATIVOS")
        void leadComAberto_naoApareceEmFinalizadosEPermaneceEmAtivos() {
            String finalizados = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "FINALIZADOS");
            String ativos = listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS");

            assertThat(finalizados).doesNotContain(leadComHistoricoEAberto.toString());
            assertThat(ativos).contains(atendimentoAbertoDoHistorico.toString());
        }

        @Test
        @DisplayName("contagem de FINALIZADOS bate com a listagem para os dois papeis")
        void contagem_bateComListagemParaOsDoisPapeis() throws Exception {
            assertContagemFinalizados(EMAIL_ANA, SENHA_ATENDENTE);
            assertContagemFinalizados(EMAIL_GESTOR, SENHA_GESTOR);
        }

        @Test
        @DisplayName("paginacao por cursor devolve o mesmo conjunto da listagem sem paginacao")
        void paginacao_devolveOMesmoConjunto() throws Exception {
            String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
            JsonNode listaCompleta = json.readTree(listarComo(EMAIL_GESTOR, SENHA_GESTOR, "FINALIZADOS"));
            java.util.Set<String> idsEsperados = new java.util.LinkedHashSet<>();
            listaCompleta.forEach(cartao -> idsEsperados.add(cartao.path("atendimentoId").asText()));

            java.util.Set<String> idsPaginados = new java.util.LinkedHashSet<>();
            String cursor = null;
            for (int pagina = 0; pagina < 50; pagina++) {
                String url = "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=50"
                        + (cursor == null ? "" : "&cursor=" + cursor);
                JsonNode corpo = json.readTree(ApoioAutenticacao.comToken(
                                http, token, HttpMethod.GET, url, String.class)
                        .getBody());
                for (JsonNode item : corpo.path("itens")) {
                    if (!"EQUIPE_INTERNA".equals(item.path("tipo").asText())) {
                        idsPaginados.add(item.path("atendimentoId").asText());
                    }
                }
                if (corpo.path("proximoCursor").isNull()
                        || corpo.path("proximoCursor").asText("").isBlank()) {
                    break;
                }
                cursor = corpo.path("proximoCursor").asText();
            }

            assertThat(idsPaginados).containsExactlyInAnyOrderElementsOf(idsEsperados);
        }

        @Test
        @DisplayName("paginacao de finalizados tambem inclui colega para atendente")
        void paginacao_deFinalizadosIncluiColegasParaAtendente() throws Exception {
            String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
            Set<String> idsPaginados = new java.util.LinkedHashSet<>();
            String cursor = null;
            for (int pagina = 0; pagina < 50; pagina++) {
                String url = "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=50"
                        + (cursor == null ? "" : "&cursor=" + cursor);
                String corpo = ApoioAutenticacao.comToken(http, token, HttpMethod.GET, url, String.class)
                        .getBody();
                for (JsonNode item : json.readTree(corpo).path("itens")) {
                    if (!"EQUIPE_INTERNA".equals(item.path("tipo").asText())) {
                        idsPaginados.add(item.path("atendimentoId").asText());
                    }
                }
                JsonNode proximo = json.readTree(corpo).path("proximoCursor");
                if (proximo.isNull() || proximo.asText("").isBlank()) break;
                cursor = proximo.asText();
            }
            assertThat(idsPaginados).contains(atendimentoFinalizadoDaAna.toString(), atendimentoFinalizadoDoBruno.toString());
        }

        @Test
        @DisplayName("TODOS continua barrada para atendente")
        void todos_continuaBarradaParaAtendente() {
            ResponseEntity<String> resposta = respostaListarComo(EMAIL_ANA, SENHA_ATENDENTE, "TODOS");
            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        private void assertContagemFinalizados(String email, String senha) throws Exception {
            String token = ApoioAutenticacao.login(http, email, senha).accessToken();
            String contagemJson = ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class)
                    .getBody();
            String listagem = ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=FINALIZADOS", String.class)
                    .getBody();
            assertThat(json.readTree(contagemJson).path("FINALIZADOS").asLong())
                    .isEqualTo(json.readTree(listagem).size());
        }
    }

    // --- apoio ------------------------------------------------------------

    private String listarComo(String email, String senha, String visao) {
        return respostaListarComo(email, senha, visao).getBody();
    }

    private ResponseEntity<String> respostaListarComo(String email, String senha, String visao) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(
                http, token, HttpMethod.GET, "/api/v1/atendimentos?visao=" + visao, String.class);
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

    private void definirInicio(UUID atendimentoId, Instant instante) {
        jdbc.update("UPDATE atendimento SET iniciado_em = ? WHERE id = ?", Timestamp.from(instante), atendimentoId);
    }

    private void definirUltimaMensagem(UUID atendimentoId, Instant instante) {
        jdbc.update(
                "UPDATE mensagem SET enviado_em = ? WHERE atendimento_id = ?",
                Timestamp.from(instante),
                atendimentoId);
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
