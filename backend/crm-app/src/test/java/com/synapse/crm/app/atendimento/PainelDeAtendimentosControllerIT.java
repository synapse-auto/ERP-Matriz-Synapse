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

    private UUID leadAtivoDaAna;
    private UUID leadPendenteDoBruno;
    private UUID atendimentoAtivoDaAna;
    private UUID atendimentoPendenteDaAna;
    private UUID atendimentoPendenteDoBruno;
    private UUID atendimentoPotencial;

    private long contarComo(String email, String senha, String visao) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        String corpo = ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class)
                .getBody();
        return Long.parseLong(corpo.replaceAll(".*\"" + visao + "\":(\\d+).*", "$1"));
    }

    @BeforeEach
    void prepararCenario() {
        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        leadAtivoDaAna = criarLead("Ativo Ana " + sufixo, idAna, "EM_ATENDIMENTO");
        definirTelefone(leadAtivoDaAna, "5561981536371");
        atendimentoAtivoDaAna = criarAtendimento(leadAtivoDaAna, idAna, "EM_ATENDIMENTO");
        inserirMensagem(atendimentoAtivoDaAna, "ATENDENTE", idAna, "resposta da Ana");

        UUID leadPendenteDaAna = criarLead("Pendente Ana " + sufixo, idAna, "EM_ATENDIMENTO");
        atendimentoPendenteDaAna = criarAtendimento(leadPendenteDaAna, idAna, "EM_ATENDIMENTO");
        inserirMensagem(atendimentoPendenteDaAna, "LEAD", null, "pergunta do lead");

        leadPendenteDoBruno = criarLead("Pendente Bruno " + sufixo, idBruno, "EM_ATENDIMENTO");
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

    @Nested
    @DisplayName("PENDENTES: mensagens automaticas")
    class Pendentes {

        @Test
        @DisplayName("confirmacao automatica nao remove a pendencia do lead")
        void ignoraMensagemDaIaAoClassificar() {
            long contagemAnaAntes = contarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES");
            long contagemGestorAntes = contarComo(EMAIL_GESTOR, SENHA_GESTOR, "PENDENTES");
            inserirMensagem(
                    atendimentoPendenteDaAna,
                    "IA",
                    null,
                    "Você foi transferido para o atendente Ana.",
                    Instant.now().plusSeconds(1));

            assertThat(listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"))
                    .contains(atendimentoPendenteDaAna.toString());
            assertThat(contarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"))
                    .isEqualTo(contagemAnaAntes);
            assertThat(listarComo(EMAIL_GESTOR, SENHA_GESTOR, "PENDENTES"))
                    .contains(atendimentoPendenteDaAna.toString());
            assertThat(contarComo(EMAIL_GESTOR, SENHA_GESTOR, "PENDENTES"))
                    .isEqualTo(contagemGestorAntes);
        }

        @Test
        @DisplayName("mensagem automatica posterior nao reabre atendimento respondido")
        void naoReabreAposRespostaHumanaMesmoComMensagemAutomatica() {
            Instant base = Instant.now();
            inserirMensagem(atendimentoPendenteDaAna, "LEAD", null, "pergunta do lead", base);
            inserirMensagem(atendimentoPendenteDaAna, "ATENDENTE", idAna, "resposta da Ana", base.plusSeconds(1));
            inserirMensagem(atendimentoPendenteDaAna, "SISTEMA", null, "aviso automatico", base.plusSeconds(2));

            assertThat(listarComo(EMAIL_ANA, SENHA_ATENDENTE, "PENDENTES"))
                    .doesNotContain(atendimentoPendenteDaAna.toString());
        }
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

    @Nested
    @DisplayName("GET /api/v1/atendimentos/busca")
    class BuscaPontual {

        @Test
        @DisplayName("leadId visível devolve o cartão representativo")
        void leadIdVisivel_devolveCartao() throws Exception {
            ResponseEntity<String> resposta = respostaComo(
                    EMAIL_ANA,
                    SENHA_ATENDENTE,
                    "/api/v1/atendimentos/busca?leadId=" + leadAtivoDaAna);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(resposta.getBody()).path("leadId").asText())
                    .isEqualTo(leadAtivoDaAna.toString());
            assertThat(json.readTree(resposta.getBody()).path("atendimentoId").asText())
                    .isEqualTo(atendimentoAtivoDaAna.toString());
        }

        @Test
        @DisplayName("leadId de colega e lead inexistente respondem 404 sem vazar RLS")
        void leadIdForaDoAlcanceOuInexistente_devolve404() {
            assertThat(respostaComo(
                            EMAIL_ANA,
                            SENHA_ATENDENTE,
                            "/api/v1/atendimentos/busca?leadId=" + leadPendenteDoBruno)
                    .getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(respostaComo(
                            EMAIL_ANA,
                            SENHA_ATENDENTE,
                            "/api/v1/atendimentos/busca?leadId=" + UUID.randomUUID())
                    .getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("telefone com e sem nono dígito resolve o mesmo cartão")
        void telefoneNormalizado_resolveMesmoCartao() throws Exception {
            String comNono = "/api/v1/atendimentos/busca?telefone=5561981536371";
            String semNono = "/api/v1/atendimentos/busca?telefone=6181536371";

            ResponseEntity<String> respostaComNono = respostaComo(EMAIL_ANA, SENHA_ATENDENTE, comNono);
            ResponseEntity<String> respostaSemNono = respostaComo(EMAIL_ANA, SENHA_ATENDENTE, semNono);

            assertThat(respostaComNono.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(respostaSemNono.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(respostaComNono.getBody()).path("atendimentoId").asText())
                    .isEqualTo(atendimentoAtivoDaAna.toString());
            assertThat(json.readTree(respostaSemNono.getBody()).path("atendimentoId").asText())
                    .isEqualTo(atendimentoAtivoDaAna.toString());
        }

        @Test
        @DisplayName("telefone sem lead correspondente responde 404")
        void telefoneSemCorrespondencia_devolve404() {
            assertThat(respostaComo(
                            EMAIL_ANA,
                            SENHA_ATENDENTE,
                            "/api/v1/atendimentos/busca?telefone=61%208154-6371")
                    .getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("E211: telefone de lead de colega responde 404 ao atendente e 200 ao gestor")
        void telefoneDeColega_404ParaAtendente200ParaGestor() throws Exception {
            // O número existe (o gestor o encontra); para a Ana, o 404 é idêntico ao de inexistente
            // — conhecer o número de um contato compartilhado não abre a conversa do Bruno.
            definirTelefone(leadPendenteDoBruno, "5561977770001");
            String url = "/api/v1/atendimentos/busca?telefone=5561977770001";

            ResponseEntity<String> paraAna = respostaComo(EMAIL_ANA, SENHA_ATENDENTE, url);
            ResponseEntity<String> paraGestor = respostaComo(EMAIL_GESTOR, SENHA_GESTOR, url);

            assertThat(paraAna.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(paraAna.getBody()).doesNotContain(leadPendenteDoBruno.toString(), "Pendente Bruno");
            assertThat(paraGestor.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(paraGestor.getBody()).path("leadId").asText())
                    .isEqualTo(leadPendenteDoBruno.toString());
        }

        @Test
        @DisplayName("E211: telefone recusado pelo normalizador responde 400")
        void telefoneInvalido_devolve400() {
            assertThat(respostaComo(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/atendimentos/busca?telefone=0800%2012")
                    .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("E211: conversa finalizada visível é devolvida como FINALIZADO, sem abrir atendimento")
        void conversaFinalizada_devolveCartaoFinalizado() throws Exception {
            UUID leadFinalizado = criarLead("Finalizado por telefone", idAna, "FINALIZADO");
            definirTelefone(leadFinalizado, "5561977770002");
            UUID finalizado = criarAtendimento(leadFinalizado, idAna, "FINALIZADO");
            int atendimentosAntes = jdbc.queryForObject(
                    "SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, leadFinalizado);

            ResponseEntity<String> resposta = respostaComo(
                    EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/atendimentos/busca?telefone=5561977770002");

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode cartao = json.readTree(resposta.getBody());
            assertThat(cartao.path("atendimentoId").asText()).isEqualTo(finalizado.toString());
            assertThat(cartao.path("status").asText()).isEqualTo("FINALIZADO");
            assertThat(cartao.path("atendimentoAtivoId").isNull()).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM atendimento WHERE lead_id = ?", Integer.class, leadFinalizado))
                    .isEqualTo(atendimentosAntes);
        }

        @Test
        @DisplayName("zero ou dois parâmetros respondem 400")
        void parametrosInvalidos_devolve400() {
            assertThat(respostaComo(EMAIL_ANA, SENHA_ATENDENTE, "/api/v1/atendimentos/busca")
                    .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(respostaComo(
                            EMAIL_ANA,
                            SENHA_ATENDENTE,
                            "/api/v1/atendimentos/busca?leadId=" + leadAtivoDaAna
                                    + "&telefone=61%2098153-6371")
                    .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @DisplayName("cartão pontual abre participação visível fora de ATIVOS")
    void cartaoPontual_abreParticipacaoVisivelForaDaVisaoAtual() throws Exception {
        jdbc.update(
                "INSERT INTO atendimento_participante(atendimento_id, usuario_id) VALUES (?, ?)",
                atendimentoPendenteDoBruno,
                idAna);
        assertThat(listarComo(EMAIL_ANA, SENHA_ATENDENTE, "ATIVOS"))
                .doesNotContain(atendimentoPendenteDoBruno.toString());

        ResponseEntity<String> resposta = respostaComo(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                "/api/v1/atendimentos/" + atendimentoPendenteDoBruno + "/cartao");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cartao = json.readTree(resposta.getBody());
        assertThat(cartao.path("atendimentoId").asText()).isEqualTo(atendimentoPendenteDoBruno.toString());
        assertThat(cartao.path("leadId").asText()).isNotBlank();
    }

    @Test
    @DisplayName("cartão pontual não revela atendimento fora do recorte RLS")
    void cartaoPontual_atendenteSemAcessoRecebe404() {
        ResponseEntity<String> resposta = respostaComo(
                EMAIL_ANA,
                SENHA_ATENDENTE,
                "/api/v1/atendimentos/" + atendimentoPendenteDoBruno + "/cartao");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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

    /**
     * E206: lead com o ciclo anterior FINALIZADO (dono X, mensagem mais recente) e outro atendimento
     * aberto (dono Y). O cartão abre o ativo; lista e cabeçalho precisam apontar o mesmo dono.
     */
    @Nested
    @DisplayName("dono do cartao")
    class DonoDoCartao {

        private UUID leadComDoisCiclos;
        private UUID finalizadoDoBruno;
        private UUID abertoDaAna;

        @BeforeEach
        void prepararDoisCiclos() {
            leadComDoisCiclos = criarLead("Dois ciclos", idAna, "EM_ATENDIMENTO");
            abertoDaAna = criarAtendimento(leadComDoisCiclos, idAna, "EM_ATENDIMENTO");
            finalizadoDoBruno = criarAtendimento(leadComDoisCiclos, idBruno, "FINALIZADO");
            Instant base = Instant.parse("2026-09-14T17:00:00Z");
            definirInicio(abertoDaAna, base);
            definirInicio(finalizadoDoBruno, base.plusSeconds(60));
            inserirMensagem(abertoDaAna, "LEAD", null, "pedido de confirmacao", base.plusSeconds(1));
            inserirMensagem(finalizadoDoBruno, "ATENDENTE", idBruno, "obgd!!", base.plusSeconds(120));
        }

        @Test
        @DisplayName("lista exibe o dono do atendimento que o clique abre, igual ao /estado")
        void listaECabecalho_exibemOMesmoDono() throws Exception {
            JsonNode cartao = cartaoDoLead(listarComo(EMAIL_GESTOR, SENHA_GESTOR, "TODOS"), leadComDoisCiclos);

            assertThat(cartao.path("atendimentoId").asText()).isEqualTo(finalizadoDoBruno.toString());
            assertThat(cartao.path("atendimentoAtivoId").asText()).isEqualTo(abertoDaAna.toString());
            assertThat(cartao.path("atendenteId").asText()).isEqualTo(idAna.toString());
            assertThat(cartao.path("atendenteNome").asText()).isEqualTo(nomeDoUsuario(idAna));

            JsonNode estado = json.readTree(respostaComo(
                            EMAIL_GESTOR,
                            SENHA_GESTOR,
                            "/api/v1/atendimentos/" + abertoDaAna + "/estado")
                    .getBody());
            assertThat(estado.path("cartao").path("atendenteNome").asText())
                    .isEqualTo(cartao.path("atendenteNome").asText());
        }

        @Test
        @DisplayName("lead com um único atendimento continua exibindo o próprio dono")
        void atendimentoUnico_mantemODono() throws Exception {
            JsonNode cartao = cartao(listarComo(EMAIL_GESTOR, SENHA_GESTOR, "TODOS"), atendimentoPendenteDoBruno);

            assertThat(cartao.path("atendenteId").asText()).isEqualTo(idBruno.toString());
            assertThat(cartao.path("atendenteNome").asText()).isEqualTo(nomeDoUsuario(idBruno));
        }

        @Test
        @DisplayName("dono do ciclo finalizado não passa a enxergar o lead do colega")
        void donoDoCicloFinalizado_naoVeOLead() {
            assertThat(listarComo(EMAIL_BRUNO, SENHA_ATENDENTE, "ATIVOS"))
                    .doesNotContain(leadComDoisCiclos.toString());
            assertThat(listarComo(EMAIL_BRUNO, SENHA_ATENDENTE, "PENDENTES"))
                    .doesNotContain(leadComDoisCiclos.toString());
        }

        private JsonNode cartaoDoLead(String corpo, UUID leadId) throws Exception {
            for (JsonNode item : json.readTree(corpo)) {
                if (leadId.toString().equals(item.path("leadId").asText())) {
                    return item;
                }
            }
            throw new AssertionError("cartao do lead nao encontrado: " + leadId);
        }

        private String nomeDoUsuario(UUID usuarioId) {
            return jdbc.queryForObject("SELECT nome FROM usuario WHERE id = ?", String.class, usuarioId);
        }
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
        @DisplayName("a contagem otimizada bate com as seis visoes, inclusive convite pendente")
        void contagem_bateComOTamanhoDaListagem() throws Exception {
            inserirMensagem(
                    atendimentoPendenteDaAna,
                    "IA",
                    null,
                    "confirmacao automatica",
                    Instant.now().plusSeconds(1));
            jdbc.update(
                    "INSERT INTO pedido_entrada_atendimento"
                            + " (atendimento_id, solicitante_id, status, tipo)"
                            + " VALUES (?, ?, 'PENDENTE', 'CONVITE')",
                    atendimentoPendenteDoBruno,
                    idAna);
            UUID leadFinalizado = criarLead(
                    "Finalizado para equivalencia " + UUID.randomUUID().toString().substring(0, 8),
                    idAna,
                    "FINALIZADO");
            criarAtendimento(leadFinalizado, idAna, "FINALIZADO");

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
                    http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem?incluirFinalizados=true", String.class).getBody();
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
        private UUID leadDoBrunoComHistoricoEAberto;

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

            // E209: o ciclo antigo FINALIZADO do Bruno e visivel para a Ana pela RLS de
            // atendimento, o ciclo aberto nao, e o lead (EM_ATENDIMENTO do Bruno) tambem nao. A
            // lista nunca mostrou esse lead; a contagem da E199 o contava. A mensagem mais recente
            // de todas o coloca no topo da escolha da primeira pagina.
            leadDoBrunoComHistoricoEAberto = criarLead("Historico aberto Bruno", idBruno, "EM_ATENDIMENTO");
            UUID historicoDoBruno = criarAtendimento(leadDoBrunoComHistoricoEAberto, idBruno, "FINALIZADO");
            UUID abertoDoBruno = criarAtendimento(leadDoBrunoComHistoricoEAberto, idBruno, "EM_ATENDIMENTO");
            definirInicio(historicoDoBruno, base.plusSeconds(40));
            definirInicio(abertoDoBruno, base.plusSeconds(50));
            inserirMensagem(historicoDoBruno, "LEAD", null, "ciclo antigo do Bruno");
            definirUltimaMensagem(historicoDoBruno, Instant.now().plusSeconds(3600));
        }

        @Test
        @DisplayName("E209: contagem padrao so traz as abas; FINALIZADOS so quando pedido")
        void contagemPadrao_omiteFinalizados() throws Exception {
            for (String[] credencial : List.of(
                    new String[] {EMAIL_ANA, SENHA_ATENDENTE}, new String[] {EMAIL_GESTOR, SENHA_GESTOR})) {
                String token = ApoioAutenticacao.login(http, credencial[0], credencial[1]).accessToken();
                JsonNode padrao = json.readTree(ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem", String.class).getBody());
                JsonNode completa = json.readTree(ApoioAutenticacao.comToken(
                        http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem?incluirFinalizados=true",
                        String.class).getBody());

                assertThat(padrao.has("FINALIZADOS")).as(credencial[0]).isFalse();
                assertThat(completa.has("FINALIZADOS")).as(credencial[0]).isTrue();
                assertThat(completa.has("TODOS")).as(credencial[0]).isEqualTo(padrao.has("TODOS"));
                for (String aba : List.of("ATIVOS", "PENDENTES", "POTENCIAIS")) {
                    assertThat(padrao.has(aba)).as("%s %s", credencial[0], aba).isTrue();
                }
            }
        }

        @Test
        @DisplayName("E209: lead escondido pela RLS de lead nao encolhe a pagina da inbox")
        void paginaDaInbox_naoEncolheComLeadEscondidoPelaRlsDeLead() throws Exception {
            String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
            JsonNode corpo = json.readTree(ApoioAutenticacao.comToken(
                    http, token, HttpMethod.GET, "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=1",
                    String.class).getBody());

            assertThat(corpo.path("itens")).hasSize(1);
            assertThat(corpo.toString()).doesNotContain(leadDoBrunoComHistoricoEAberto.toString());
            assertThat(corpo.path("proximoCursor").asText("")).isNotBlank();
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
        @DisplayName("gestor filtra finalizados por atendente no endpoint da inbox")
        void gestor_filtraPorAtendente() throws Exception {
            String token = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
            String url = "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=50&atendenteId=" + idAna;
            JsonNode corpo = json.readTree(ApoioAutenticacao.comToken(
                            http, token, HttpMethod.GET, url, String.class)
                    .getBody());

            assertThat(corpo.toString()).contains(atendimentoFinalizadoDaAna.toString());
            assertThat(corpo.toString()).doesNotContain(atendimentoFinalizadoDoBruno.toString());
        }

        @Test
        @DisplayName("atendente pode filtrar os próprios finalizados")
        void atendente_filtraOsProprios() {
            String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
            String url = "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=50&atendenteId=" + idAna;
            String corpo = ApoioAutenticacao.comToken(http, token, HttpMethod.GET, url, String.class).getBody();

            assertThat(corpo).contains(atendimentoFinalizadoDaAna.toString());
            assertThat(corpo).doesNotContain(atendimentoFinalizadoDoBruno.toString());
        }

        @Test
        @DisplayName("atendente não pode consultar finalizados de outro atendente pelo parâmetro")
        void atendente_naoFiltraColega() {
            String token = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
            String url = "/api/v1/atendimentos/inbox?visao=FINALIZADOS&limite=50&atendenteId=" + idBruno;
            ResponseEntity<String> resposta = ApoioAutenticacao.comToken(
                    http, token, HttpMethod.GET, url, String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
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
                            http, token, HttpMethod.GET, "/api/v1/atendimentos/contagem?incluirFinalizados=true", String.class)
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

    private ResponseEntity<String> respostaComo(String email, String senha, String caminho) {
        String token = ApoioAutenticacao.login(http, email, senha).accessToken();
        return ApoioAutenticacao.comToken(http, token, HttpMethod.GET, caminho, String.class);
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

    private void definirTelefone(UUID leadId, String telefone) {
        jdbc.update("UPDATE lead SET telefone = ? WHERE id = ?", telefone, leadId);
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
        inserirMensagem(atendimentoId, remetenteTipo, remetenteId, conteudo, Instant.now());
    }

    private void inserirMensagem(
            UUID atendimentoId,
            String remetenteTipo,
            UUID remetenteId,
            String conteudo,
            Instant enviadoEm) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, remetente_id, tipo,"
                        + " conteudo, status_entrega, enviado_em)"
                        + " VALUES (?, ?, ?::remetente_tipo, ?, 'TEXTO'::tipo_mensagem, ?,"
                        + " 'ENVIADO'::status_entrega, ?)",
                UUID.randomUUID(),
                atendimentoId,
                remetenteTipo,
                remetenteId,
                conteudo,
                Timestamp.from(enviadoEm));
    }
}
