package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_BRUNO;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_SUBGESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_SUBGESTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DuplicateKeyException;
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
 * Setores (D2 da Disbrel, docs/71): capacidade desligada = nada muda; ligada = cadastro e atribuição com a alçada
 * da equipe; e, nos dois casos, <b>setor não altera visibilidade de lead</b> (RN-CRM-01).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SetoresIT extends PostgresIT {

    private static final String PREFIXO = "IT-SETOR ";

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private String tokenGestor;
    private String tokenSubgestor;
    private String tokenAna;
    private UUID ana;
    private UUID bruno;
    private UUID leadDoBruno;

    @BeforeEach
    void preparar() {
        limpar();
        tokenGestor = ApoioAutenticacao.login(http, EMAIL_GESTOR, SENHA_GESTOR).accessToken();
        tokenSubgestor = ApoioAutenticacao.login(http, EMAIL_SUBGESTOR, SENHA_SUBGESTOR).accessToken();
        tokenAna = ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
        ana = idDe(EMAIL_ANA);
        bruno = idDe(EMAIL_BRUNO);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM usuario_setor");
        jdbc.update("DELETE FROM setor WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("UPDATE feature_flag SET habilitado = FALSE WHERE chave = 'setores'");
        if (leadDoBruno != null) {
            jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", leadDoBruno);
            jdbc.update("DELETE FROM lead WHERE id = ?", leadDoBruno);
            leadDoBruno = null;
        }
    }

    private void ligar() {
        jdbc.update("UPDATE feature_flag SET habilitado = TRUE WHERE chave = 'setores'");
    }

    // --- capacidade desligada ----------------------------------------------------------------------------

    @Test
    @DisplayName("a flag nasce desligada e, desligada, todas as rotas de setor respondem 404 (como antes de existirem)")
    void desligada_todasAsRotasDevolvem404() {
        assertThat(jdbc.queryForObject("SELECT habilitado FROM feature_flag WHERE chave = 'setores'", Boolean.class))
                .isFalse();

        assertThat(chamar(tokenGestor, HttpMethod.GET, "/api/v1/setores", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(tokenGestor, HttpMethod.POST, "/api/v1/setores", Map.of("nome", PREFIXO + "Vendas"))
                        .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(chamar(tokenGestor, HttpMethod.PUT, "/api/v1/usuarios/" + ana + "/setor", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM setor WHERE nome LIKE ?", Integer.class, PREFIXO + "%"))
                .isZero();
    }

    @Test
    @DisplayName("desligada, a lista de usuários não ganha nenhum campo de setor")
    void desligada_listaDeUsuariosInalterada() {
        String corpo = chamar(tokenGestor, HttpMethod.GET, "/api/v1/usuarios", null).getBody();

        assertThat(corpo).doesNotContain("setor");
    }

    // --- ligada: cadastro --------------------------------------------------------------------------------

    @Test
    @DisplayName("GESTOR cria setores; nome é único sem diferenciar caixa e espaços; nome vazio é recusado")
    void ligada_criaEValidaNome() throws Exception {
        ligar();

        ResponseEntity<String> criado = chamar(
                tokenGestor, HttpMethod.POST, "/api/v1/setores", Map.of("nome", PREFIXO + "Vendas"));
        assertThat(criado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode setor = json.readTree(criado.getBody());
        assertThat(setor.path("recebeDaTriagem").asBoolean()).isTrue();
        assertThat(setor.path("ativo").asBoolean()).isTrue();

        assertThat(chamar(tokenGestor, HttpMethod.POST, "/api/v1/setores", Map.of("nome", "  " + PREFIXO.toLowerCase() + "vendas "))
                        .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(chamar(tokenGestor, HttpMethod.POST, "/api/v1/setores", Map.of("nome", "   ")).getStatusCode())
                .isIn(HttpStatus.BAD_REQUEST, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("atualizar renomeia, liga/desliga recebeDaTriagem e inativa; inexistente devolve 404")
    void ligada_atualizaSetor() throws Exception {
        ligar();
        UUID id = criar("Diretoria", true);

        ResponseEntity<String> resposta = chamar(
                tokenGestor, HttpMethod.PUT, "/api/v1/setores/" + id,
                Map.of("nome", PREFIXO + "Diretoria geral", "recebeDaTriagem", false, "ativo", true));
        JsonNode setor = json.readTree(resposta.getBody());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setor.path("nome").asText()).isEqualTo(PREFIXO + "Diretoria geral");
        assertThat(setor.path("recebeDaTriagem").asBoolean()).isFalse();
        assertThat(chamar(tokenGestor, HttpMethod.PUT, "/api/v1/setores/" + UUID.randomUUID(),
                        Map.of("nome", PREFIXO + "X", "recebeDaTriagem", true, "ativo", true))
                .getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- ligada: permissao e alcada ----------------------------------------------------------------------

    @Test
    @DisplayName("ATENDENTE não lista, não cria e não atribui")
    void atendenteNaoAcessaNada() {
        ligar();
        UUID vendas = criar("Vendas", true);

        assertThat(chamar(tokenAna, HttpMethod.GET, "/api/v1/setores", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenAna, HttpMethod.POST, "/api/v1/setores", Map.of("nome", PREFIXO + "Y")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenAna, HttpMethod.PUT, "/api/v1/usuarios/" + bruno + "/setor", mapa("setorId", vendas))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario_setor", Integer.class)).isZero();
    }

    @Test
    @DisplayName("SUBGESTOR sem delegação vê os setores, mas não cria nem atribui (equipe.editar nasce negada a ele, como em usuários)")
    void subgestorSemDelegacaoSoLe() {
        ligar();
        UUID vendas = criar("Vendas", true);

        assertThat(chamar(tokenSubgestor, HttpMethod.GET, "/api/v1/setores", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(chamar(tokenSubgestor, HttpMethod.POST, "/api/v1/setores", Map.of("nome", PREFIXO + "Z")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(chamar(tokenSubgestor, HttpMethod.PUT, "/api/v1/usuarios/" + ana + "/setor", mapa("setorId", vendas))
                        .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(setorDe(ana)).isNull();
    }

    // --- ligada: atribuicao ------------------------------------------------------------------------------

    @Test
    @DisplayName("atribui, troca (um setor por usuário), remove com null, conta só integrantes ativos")
    void atribuiTrocaERemove() throws Exception {
        ligar();
        UUID vendas = criar("Vendas", true);
        UUID suporte = criar("Suporte", true);

        assertThat(atribuir(ana, vendas).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(atribuir(bruno, vendas).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contagem(vendas)).isEqualTo(2);

        assertThat(atribuir(ana, suporte).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setorDe(ana)).isEqualTo(suporte);
        assertThat(contagem(vendas)).isEqualTo(1);
        assertThat(contagem(suporte)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario_setor WHERE usuario_id = ?", Integer.class, ana))
                .isEqualTo(1);

        assertThat(atribuir(bruno, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(setorDe(bruno)).isNull();
        assertThat(contagem(vendas)).isZero();
    }

    @Test
    @DisplayName("o banco impede dois setores para o mesmo usuário (índice relaxável), e a atribuição audita")
    void bancoImpedeDoisSetoresEAuditaAAtribuicao() {
        ligar();
        UUID vendas = criar("Vendas", true);
        UUID suporte = criar("Suporte", true);
        atribuir(ana, vendas);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO usuario_setor (usuario_id, setor_id) VALUES (?, ?)", ana, suporte))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE acao = 'ATRIBUIR_SETOR_AO_USUARIO' AND entidade_id = ?",
                        Integer.class, ana))
                .isPositive();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_log WHERE acao = 'CRIAR_SETOR'", Integer.class))
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("setor inativo não aceita novos integrantes; quem já estava continua")
    void setorInativoNaoAceitaNovos() {
        ligar();
        UUID vendas = criar("Vendas", true);
        atribuir(ana, vendas);
        chamar(tokenGestor, HttpMethod.PUT, "/api/v1/setores/" + vendas,
                Map.of("nome", PREFIXO + "Vendas", "recebeDaTriagem", true, "ativo", false));

        assertThat(atribuir(bruno, vendas).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(setorDe(bruno)).isNull();
        assertThat(setorDe(ana)).isEqualTo(vendas);
    }

    @Test
    @DisplayName("setor inexistente e usuário inexistente devolvem 404 e não gravam")
    void inexistentesDevolvem404() {
        ligar();
        UUID vendas = criar("Vendas", true);

        assertThat(atribuir(ana, UUID.randomUUID()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(atribuir(UUID.randomUUID(), vendas).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario_setor", Integer.class)).isZero();
    }

    // --- o teste negativo que importa: setor nao da acesso a lead ----------------------------------------

    @Test
    @DisplayName("NEGATIVO RN-CRM-01: atendente do MESMO setor do colega continua sem ver o lead dele")
    void setorNaoAlteraAVisibilidadeDeLead() {
        leadDoBruno = criarLeadEmAtendimentoDe(bruno);
        // Antes de qualquer setor, com a capacidade desligada.
        assertThat(anaVeOLeadDoBruno()).isFalse();

        ligar();
        UUID vendas = criar("Vendas", true);
        atribuir(ana, vendas);
        atribuir(bruno, vendas);

        assertThat(anaVeOLeadDoBruno())
                .as("setor compartilhado nao pode abrir o lead do colega")
                .isFalse();
        assertThat(comoBrunoVeOProprioLead()).isTrue();
    }

    @Test
    @DisplayName("o painel de Ana é idêntico com a capacidade desligada e com ela ligada e Ana num setor")
    void listaDeLeadsDaAnaNaoMuda() {
        leadDoBruno = criarLeadEmAtendimentoDe(bruno);
        String antes = chamar(tokenAna, HttpMethod.GET, "/api/v1/leads", null).getBody();

        ligar();
        UUID vendas = criar("Vendas", true);
        atribuir(ana, vendas);
        atribuir(bruno, vendas);
        String depois = chamar(tokenAna, HttpMethod.GET, "/api/v1/leads", null).getBody();

        assertThat(depois).isEqualTo(antes);
    }

    // --- apoio -------------------------------------------------------------------------------------------

    private boolean anaVeOLeadDoBruno() {
        ResponseEntity<String> ficha = chamar(tokenAna, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno, null);
        String lista = chamar(tokenAna, HttpMethod.GET, "/api/v1/leads", null).getBody();
        return ficha.getStatusCode().is2xxSuccessful() || lista.contains("IT-SETOR lead do Bruno");
    }

    private boolean comoBrunoVeOProprioLead() {
        String tokenBruno = ApoioAutenticacao.login(http, EMAIL_BRUNO, SENHA_ATENDENTE).accessToken();
        return chamar(tokenBruno, HttpMethod.GET, "/api/v1/leads/" + leadDoBruno, null).getStatusCode().is2xxSuccessful();
    }

    private UUID criarLeadEmAtendimentoDe(UUID atendente) {
        UUID lead = UUID.randomUUID();
        Instant agora = Instant.now();
        jdbc.update("INSERT INTO lead (id, nome, atendente_responsavel_id, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_basico_lead, ?, ?)",
                lead, "IT-SETOR lead do Bruno", atendente, Timestamp.from(agora), Timestamp.from(agora));
        jdbc.update("INSERT INTO atendimento (id, lead_id, atendente_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, 'EM_ATENDIMENTO'::status_atendimento, ?)",
                UUID.randomUUID(), lead, atendente, Timestamp.from(agora.minusSeconds(60)));
        return lead;
    }

    private UUID criar(String nome, boolean recebeDaTriagem) {
        ResponseEntity<String> resposta = chamar(
                tokenGestor, HttpMethod.POST, "/api/v1/setores",
                Map.of("nome", PREFIXO + nome, "recebeDaTriagem", recebeDaTriagem));
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        try {
            return UUID.fromString(json.readTree(resposta.getBody()).path("id").asText());
        } catch (Exception erro) {
            throw new IllegalStateException(erro);
        }
    }

    private ResponseEntity<String> atribuir(UUID usuario, UUID setor) {
        return chamar(tokenGestor, HttpMethod.PUT, "/api/v1/usuarios/" + usuario + "/setor", mapa("setorId", setor));
    }

    private static Map<String, Object> mapa(String chave, Object valor) {
        Map<String, Object> mapa = new java.util.HashMap<>();
        mapa.put(chave, valor);
        return mapa;
    }

    private UUID setorDe(UUID usuario) {
        return jdbc.query("SELECT setor_id FROM usuario_setor WHERE usuario_id = ?",
                        (linha, indice) -> linha.getObject("setor_id", UUID.class), usuario)
                .stream().findFirst().orElse(null);
    }

    private int contagem(UUID setor) throws Exception {
        JsonNode visao = json.readTree(chamar(tokenGestor, HttpMethod.GET, "/api/v1/setores", null).getBody());
        for (JsonNode item : visao.path("setores")) {
            if (setor.toString().equals(item.path("id").asText())) {
                return item.path("quantidadeDeUsuarios").asInt();
            }
        }
        throw new IllegalStateException("setor fora da lista: " + setor);
    }

    private UUID idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    private ResponseEntity<String> chamar(String token, HttpMethod metodo, String url, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }
}
