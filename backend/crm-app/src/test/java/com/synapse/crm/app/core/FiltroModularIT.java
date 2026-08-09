package com.synapse.crm.app.core;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * Filtro modular ponta a ponta (E03b).
 *
 * <p>Duas perguntas dominam esta suite. A primeira e se a arvore aninhada devolve o conjunto certo. A
 * segunda, mais importante, e se ela consegue devolver algo <b>alem</b> do que o usuario ja enxergava
 * — porque este e o ponto onde o isolamento de agenda da E02 poderia vazar depois de toda a
 * blindagem: um {@code OU} do cliente que escapasse do {@code AND} da visibilidade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class FiltroModularIT extends PostgresIT {

    private static final String PREFIXO = "E03B-";
    private static final String QUALIFICACAO = "e1000000-0000-4000-8000-000000000002";
    private static final String PROPOSTA = "e1000000-0000-4000-8000-000000000003";
    private static final String TAG_URGENTE = "7a000000-0000-4000-8000-000000000002";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID idAna;
    private UUID idBruno;

    /**
     * Cinco leads que separam as duas pernas do OU e o corte da janela.
     *
     * <p>Nenhum fica com {@code status_basico = 'IA'}: lead sem dono e visivel a todo mundo, e um
     * deles no meio do cenario mascararia justamente o vazamento que a suite procura.
     */
    @BeforeEach
    void prepararCenario() {
        jdbc.update(
                "DELETE FROM lead_tag WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");

        idAna = idDoUsuario(EMAIL_ANA);
        idBruno = idDoUsuario(EMAIL_BRUNO);

        // casa pela etapa e pela janela
        criarLead("Alfa", QUALIFICACAO, idAna, 60);
        // casa pela tag e pela janela
        comTag(criarLead("Beta", PROPOSTA, idBruno, 90), TAG_URGENTE);
        // casa a etapa, mas interagiu ontem: fora da janela
        criarLead("Gama", QUALIFICACAO, idAna, 1);
        // dentro da janela, mas nao casa nenhuma perna do OU
        criarLead("Delta", PROPOSTA, idBruno, 200);
        // casa as duas pernas
        comTag(criarLead("Epsilon", QUALIFICACAO, idAna, 45), TAG_URGENTE);
    }

    @Nested
    @DisplayName("arvore aninhada")
    class Aninhamento {

        /** {@code (etapa = Qualificacao OU tag = Urgente) E semRetornoDias > 30}. */
        @Test
        @DisplayName("(etapa OU tag) E janela devolve o conjunto correto")
        void filtroAninhado_comoGestor_devolveOConjuntoCorreto() {
            String corpo = filtrar(gestor(), filtroDoEnunciado()).getBody();

            assertThat(corpo)
                    .contains(PREFIXO + "Alfa")
                    .contains(PREFIXO + "Beta")
                    .contains(PREFIXO + "Epsilon");
            assertThat(corpo).doesNotContain(PREFIXO + "Gama").doesNotContain(PREFIXO + "Delta");
        }

        /**
         * A tela mostra o total antes de salvar o filtro. Se o numero divergir do resultado, o usuario
         * dispara campanha para um publico que nao e o que ele viu.
         */
        @Test
        @DisplayName("a contagem bate com o tamanho do resultado")
        void contagem_mesmoFiltro_bateComOResultado() {
            String token = gestor();

            long total = contar(token, filtroDoEnunciado());
            int noResultado = quantidadeDeLeads(filtrar(token, filtroDoEnunciado()).getBody());

            assertThat(total).isEqualTo(noResultado);
        }

        /** A tag mora em lead_tag: com JOIN, um lead de duas tags casadas contaria duas vezes. */
        @Test
        @DisplayName("filtro por tag nao duplica o lead na contagem")
        void contagem_filtroPorTag_naoDuplica() {
            Map<String, Object> porTag = simples("tag", "EM", List.of(TAG_URGENTE));
            String token = gestor();

            assertThat(contar(token, porTag))
                    .isEqualTo(quantidadeDeLeads(filtrar(token, porTag).getBody()));
        }
    }

    @Nested
    @DisplayName("composicao com a visibilidade (RN-CRM-01)")
    class Visibilidade {

        /**
         * O teste central da etapa. Ana monta o filtro que <em>pegaria</em> os leads do Bruno e
         * continua sem ver nenhum: o {@code AND} com a visibilidade so consegue tirar linhas.
         */
        @Test
        @DisplayName("atendente filtrando pelos leads do colega nao ve nenhum")
        void filtro_atendenteApontandoParaColega_devolveVazio() {
            Map<String, Object> doColega =
                    simples("atendenteResponsavel", "IGUAL", List.of(idBruno.toString()));

            ResponseEntity<String> resposta = filtrar(ana(), doColega);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(quantidadeDeLeads(resposta.getBody())).isZero();
            assertThat(contar(ana(), doColega)).isZero();
        }

        /**
         * A variante que engana: um {@code OU} na raiz. Se o filtro do cliente fosse aplicado no lugar
         * da visibilidade — ou ligado a ela por {@code OU} —, esta chamada devolveria a base inteira.
         *
         * <p>As duas metades importam. A segunda prova que Ana nao ve os leads do Bruno; a primeira
         * prova que <b>o filtro sabe traze-los</b>, e portanto que o vazio da Ana veio do {@code AND}
         * com a visibilidade e nao de um filtro que simplesmente nao casa nada. Sem esse par, o teste
         * continuaria verde com o interpretador quebrado — que e exatamente o modo de falha que a E00,
         * a E02b e a E03a ja produziram neste projeto.
         */
        @Test
        @DisplayName("OU na raiz nao escapa do AND da visibilidade")
        void filtro_comOuNaRaiz_naoAmpliaOQueOAtendenteVe() {
            Map<String, Object> qualquerDono = composto(
                    "OU",
                    List.of(
                            simples("atendenteResponsavel", "IGUAL", List.of(idBruno.toString())),
                            simples("atendenteResponsavel", "IGUAL", List.of(idAna.toString()))));

            String paraOGestor = filtrar(gestor(), qualquerDono).getBody();
            assertThat(paraOGestor).contains(PREFIXO + "Beta").contains(PREFIXO + "Delta");

            String paraAAna = filtrar(ana(), qualquerDono).getBody();
            assertThat(paraAAna).contains(PREFIXO + "Alfa").contains(PREFIXO + "Gama");
            assertThat(paraAAna).doesNotContain(PREFIXO + "Beta").doesNotContain(PREFIXO + "Delta");
        }

        /** O mesmo filtro devolve conjuntos diferentes por papel — e o recorte, nao a permissao. */
        @Test
        @DisplayName("o mesmo filtro rende menos para o atendente do que para o gestor")
        void mesmoFiltro_atendenteEGestor_conjuntosDiferentes() {
            assertThat(contar(ana(), filtroDoEnunciado()))
                    .isLessThan(contar(gestor(), filtroDoEnunciado()));
        }
    }

    @Nested
    @DisplayName("entrada hostil")
    class EntradaHostil {

        @Test
        @DisplayName("campo fora da allowlist devolve 400")
        void campo_foraDaAllowlist_devolve400() {
            ResponseEntity<String> resposta = filtrar(ana(), simples("senhaHash", "IGUAL", List.of("x")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("campo nao permitido");
        }

        @Test
        @DisplayName("operador fora da allowlist devolve 400")
        void operador_foraDaAllowlist_devolve400() {
            ResponseEntity<String> resposta = filtrar(ana(), simples("nome", "REGEX", List.of("^a")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("operador nao permitido");
        }

        /** Nome de coluna vindo do cliente jamais vira identificador — e a tabela continua de pe. */
        @Test
        @DisplayName("injecao pelo campo nao executa SQL arbitrario")
        void injecao_peloCampo_naoExecutaSql() {
            ResponseEntity<String> resposta =
                    filtrar(ana(), simples("nome); DROP TABLE lead; --", "CONTEM", List.of("a")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM lead", Integer.class)).isPositive();
        }

        /**
         * Injecao pelo <em>valor</em> tem outro desfecho: o campo e valido, entao a consulta roda — e
         * nao casa nada, porque o texto foi para bind em vez de virar clausula.
         */
        @Test
        @DisplayName("injecao pelo valor vira texto procurado, nao clausula")
        void injecao_peloValor_viraTextoProcurado() {
            ResponseEntity<String> resposta =
                    filtrar(ana(), simples("nome", "CONTEM", List.of("' OR '1'='1")));

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(quantidadeDeLeads(resposta.getBody())).isZero();
        }

        @Test
        @DisplayName("aninhamento alem do limite devolve 400")
        void aninhamento_alemDoLimite_devolve400() {
            Map<String, Object> fundo = simples("etapa", "IGUAL", List.of(QUALIFICACAO));
            for (int nivel = 0; nivel < 8; nivel++) {
                fundo = composto("E", List.of(fundo));
            }

            ResponseEntity<String> resposta = filtrar(ana(), fundo);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resposta.getBody()).contains("aninhado alem de");
        }

        @Test
        @DisplayName("sem token, o filtro nao responde")
        void filtro_semAutenticacao_devolve401() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/leads/filtrar",
                    HttpMethod.POST,
                    new HttpEntity<>(envelope(simples("nome", "CONTEM", List.of("a"))), cabecalhos),
                    String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * E16 §Bloco 1: a paginacao entra <b>depois</b> da visibilidade — nao pode virar um caminho
     * novo para o mesmo vazamento que {@link Visibilidade} ja fecha sem paginacao.
     */
    @Nested
    @DisplayName("paginacao (E16)")
    class Paginacao {

        @Test
        @DisplayName("tamanho menor que o total devolve temMais e a pagina pedida")
        void tamanhoMenorQueOTotal_devolveTemMaisEAPaginaPedida() {
            String corpo = filtrarPaginado(gestor(), filtroDoEnunciado(), null, 2).getBody();

            assertThat(quantidadeDeLeads(corpo)).isEqualTo(2);
            assertThat(corpo).contains("\"temMais\":true").contains("\"pagina\":0");
        }

        @Test
        @DisplayName("ultima pagina nao anuncia mais paginas")
        void ultimaPagina_naoAnunciaMaisPaginas() {
            String corpo = filtrarPaginado(gestor(), filtroDoEnunciado(), null, 10).getBody();

            assertThat(corpo).contains("\"temMais\":false");
        }

        /**
         * O teste central desta secao: atendente pedindo os leads do colega, com paginacao,
         * continua vazio — LIMIT/OFFSET nunca vira caminho para alcancar o que a visibilidade
         * ja recusou.
         */
        @Test
        @DisplayName("atendente filtrando pelo colega, com paginacao, continua vazio")
        void atendenteApontandoParaColega_comPaginacao_continuaVazio() {
            Map<String, Object> doColega =
                    simples("atendenteResponsavel", "IGUAL", List.of(idBruno.toString()));

            String corpo = filtrarPaginado(ana(), doColega, null, 1).getBody();

            assertThat(quantidadeDeLeads(corpo)).isZero();
            assertThat(corpo).contains("\"temMais\":false").contains("\"pagina\":0");
        }

        @Test
        @DisplayName("tamanho fora da faixa devolve 400")
        void tamanhoForaDaFaixa_devolve400() {
            ResponseEntity<String> resposta = filtrarPaginado(gestor(), filtroDoEnunciado(), null, 0);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("pagina negativa devolve 400")
        void paginaNegativa_devolve400() {
            ResponseEntity<String> resposta = filtrarPaginado(gestor(), filtroDoEnunciado(), -1, null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        private ResponseEntity<String> filtrarPaginado(
                String token, Map<String, Object> criterio, Integer pagina, Integer tamanho) {
            Map<String, Object> corpo = new HashMap<>();
            corpo.put("criterio", criterio);
            if (pagina != null) corpo.put("pagina", pagina);
            if (tamanho != null) corpo.put("tamanho", tamanho);

            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(token);
            cabecalhos.setContentType(MediaType.APPLICATION_JSON);
            return http.postForEntity(
                    "/api/v1/leads/filtrar", new HttpEntity<>(corpo, cabecalhos), String.class);
        }
    }

    /** E16 §Bloco 1: a barra de filtros da tela se monta a partir daqui — sem campo hardcoded. */
    @Nested
    @DisplayName("GET /api/v1/leads/filtrar/campos")
    class CamposFiltraveis {

        @Test
        @DisplayName("devolve apelido, rotulo, tipo e operadores dos campos estaticos")
        void devolveApelidoRotuloTipoEOperadores() {
            HttpHeaders cabecalhos = new HttpHeaders();
            cabecalhos.setBearerAuth(ana());

            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/leads/filtrar/campos",
                    HttpMethod.GET,
                    new HttpEntity<>(cabecalhos),
                    String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody())
                    .contains("\"apelido\":\"atendenteResponsavel\"")
                    .contains("\"apelido\":\"tag\"")
                    .contains("\"tipo\":\"REFERENCIA\"")
                    .contains("IGUAL");
        }

        @Test
        @DisplayName("sem autenticacao, devolve 401")
        void semAutenticacao_devolve401() {
            ResponseEntity<String> resposta = http.exchange(
                    "/api/v1/leads/filtrar/campos", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), String.class);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- montagem do filtro ---------------------------------------------------

    private static Map<String, Object> filtroDoEnunciado() {
        return composto(
                "E",
                List.of(
                        composto(
                                "OU",
                                List.of(
                                        simples("etapa", "IGUAL", List.of(QUALIFICACAO)),
                                        simples("tag", "IGUAL", List.of(TAG_URGENTE)))),
                        simples("semRetornoDias", "MAIOR_QUE", List.of("30"))));
    }

    private static Map<String, Object> simples(String campo, String operador, List<String> valores) {
        Map<String, Object> criterio = new HashMap<>();
        criterio.put("tipo", "SIMPLES");
        criterio.put("campo", campo);
        criterio.put("operador", operador);
        criterio.put("valores", valores);
        return criterio;
    }

    private static Map<String, Object> composto(String conector, List<Map<String, Object>> filhos) {
        Map<String, Object> criterio = new HashMap<>();
        criterio.put("tipo", "COMPOSTO");
        criterio.put("conector", conector);
        criterio.put("criterios", new ArrayList<>(filhos));
        return criterio;
    }

    private static Map<String, Object> envelope(Map<String, Object> criterio) {
        return Map.of("criterio", criterio);
    }

    // --- chamadas -------------------------------------------------------------

    private ResponseEntity<String> filtrar(String token, Map<String, Object> criterio) {
        return postar(token, "/api/v1/leads/filtrar", criterio);
    }

    private long contar(String token, Map<String, Object> criterio) {
        ResponseEntity<String> resposta = postar(token, "/api/v1/leads/filtrar/contagem", criterio);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return Long.parseLong(resposta.getBody().replaceAll("[^0-9]", ""));
    }

    private ResponseEntity<String> postar(String token, String url, Map<String, Object> criterio) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.postForEntity(url, new HttpEntity<>(envelope(criterio), cabecalhos), String.class);
    }

    private String ana() {
        return ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
    }

    private String gestor() {
        return ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
    }

    // --- apoio ----------------------------------------------------------------

    /** Conta objetos pelo campo {@code id}, que aparece uma vez por lead na resposta. */
    private static int quantidadeDeLeads(String corpoJson) {
        return corpoJson.split("\"id\"", -1).length - 1;
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private UUID criarLead(String nome, String etapa, UUID dono, int diasSemInteracao) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO lead (id, nome, etapa_atendimento_id, atendente_responsavel_id,
                                  status_basico, ultima_interacao_em)
                VALUES (?, ?, ?::uuid, ?, 'EM_ATENDIMENTO', ?)
                """,
                id,
                PREFIXO + nome,
                etapa,
                dono,
                OffsetDateTime.now().minusDays(diasSemInteracao));
        return id;
    }

    private void comTag(UUID leadId, String tagId) {
        jdbc.update("INSERT INTO lead_tag (lead_id, tag_id) VALUES (?, ?::uuid)", leadId, tagId);
    }
}
