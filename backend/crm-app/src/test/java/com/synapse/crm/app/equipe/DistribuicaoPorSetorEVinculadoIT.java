package com.synapse.crm.app.equipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.context.TestPropertySource;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;
import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.equipe.application.permissao.ResolvedorDePermissoesEfetivas;

/**
 * Rodizio por setor (D3a) e atendente vinculado (D3b) da Disbrel. Tudo nasce desligado: com as chaves desligadas os
 * ITs de rodizio e transferencia existentes passam sem edicao (provado a parte); aqui estao os cenarios novos, ligados
 * explicitamente, e os negativos (setor e vinculo nao dao acesso nem posse).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=e229-distribuicao-token")
class DistribuicaoPorSetorEVinculadoIT extends PostgresIT {

    private static final String TOKEN = "e229-distribuicao-token";
    private static final String PREFIXO = "E229-DIST-";
    private static final String CHAVE_VINCULADO = "ia.distribuicao.atendente_vinculado";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ResolvedorDePermissoesEfetivas flags;
    @Autowired private VinculoDoLeadRepositorio vinculos;

    private final AtomicInteger sequencia = new AtomicInteger();

    @BeforeEach
    void preparar() {
        // Os atendentes do seed ficam fora do cenario.
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
        jdbc.update("UPDATE configuracao_automacao SET valor = 'false' WHERE chave = 'ia.distribuicao.sequencial'");
        desligarTudo();
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
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

    // --- D3c: chaves ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("as tres chaves novas existem, nascem false e sao servidas pelo contrato interno ja existente")
    void chavesNascemDesligadasEAparecemNoContratoInterno() throws Exception {
        for (String chave : List.of(CHAVE_VINCULADO, "ia.distribuicao.por_botoes", "ia.distribuicao.perguntar_preferencia")) {
            assertThat(jdbc.queryForObject("SELECT tipo FROM configuracao_automacao WHERE chave = ?", String.class, chave))
                    .as(chave)
                    .isEqualTo("BOOLEAN");
            ResponseEntity<String> resposta = get("/internal/v1/automation-config/" + chave);
            assertThat(resposta.getStatusCode()).as(chave).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(resposta.getBody()).toString()).as(chave).contains("false");
        }
    }

    // --- D3a: rodizio por setor ----------------------------------------------------------------------------------

    @Test
    @DisplayName("capacidade desligada: setor no corpo e recusado (422 SETORES_DESLIGADOS) e nada muda")
    void setorComCapacidadeDesligadaRecusa() {
        criarAtendente("A", null);
        UUID atendimento = criarAtendimentoNaIa("desligada");

        ResponseEntity<String> resposta = transferirProximo(atendimento, "desligada-1", Map.of("setorId", UUID.randomUUID()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(motivo(resposta)).isEqualTo("SETORES_DESLIGADOS");
        assertThat(dono(atendimento)).isNull();
    }

    @Test
    @DisplayName("corpo vazio ou {} continua sendo o rodizio de sempre")
    void corpoVazioOuSemSetorNaoMudaNada() {
        UUID a = criarAtendente("A", null);
        UUID atendimentoUm = criarAtendimentoNaIa("vazio-1");
        UUID atendimentoDois = criarAtendimentoNaIa("vazio-2");

        assertThat(transferirProximo(atendimentoUm, "vazio-1", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transferirProximo(atendimentoDois, "vazio-2", Map.of()).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimentoUm)).isEqualTo(a);
        assertThat(dono(atendimentoDois)).isEqualTo(a);
    }

    @Test
    @DisplayName("com setor, so participa do rodizio quem esta no setor; o chamador nunca escolhe o usuario")
    void rodizioRestritoAoSetor() {
        ligarSetores();
        UUID vendas = criarSetor("Vendas", true);
        UUID suporte = criarSetor("Suporte", true);
        UUID v1 = criarAtendente("V1", vendas);
        UUID v2 = criarAtendente("V2", vendas);
        UUID s1 = criarAtendente("S1", suporte);
        UUID semSetor = criarAtendente("SEM-SETOR", null);
        // Suporte e o mais ocioso: sem o filtro seria ele (ou o sem-setor) o escolhido.
        criarAbertos("v1", v1, 2);
        criarAbertos("v2", v2, 1);

        UUID atendimentoUm = criarAtendimentoNaIa("setor-1");
        UUID atendimentoDois = criarAtendimentoNaIa("setor-2");
        // Corpo com tentativa de forjar o destino: propriedades desconhecidas sao ignoradas.
        ResponseEntity<String> primeira = transferirProximo(atendimentoUm, "setor-1", Map.of("setorId", vendas, "atendenteId", s1));
        ResponseEntity<String> segunda = transferirProximo(atendimentoDois, "setor-2", Map.of("setorId", vendas));

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimentoUm)).isEqualTo(v2); // menor carga do setor
        assertThat(List.of(dono(atendimentoUm), dono(atendimentoDois))).doesNotContain(s1, semSetor);
    }

    @Test
    @DisplayName("setor inexistente, inativo ou que nao recebe da triagem e recusado com motivo; nada muda")
    void setoresInvalidosSaoRecusadosComMotivo() {
        ligarSetores();
        UUID inativo = criarSetor("Inativo", true);
        jdbc.update("UPDATE setor SET ativo = FALSE WHERE id = ?", inativo);
        UUID diretoria = criarSetor("Diretoria", false);
        criarAtendente("A", null);
        UUID atendimento = criarAtendimentoNaIa("invalidos");

        assertThat(motivo(transferirProximo(atendimento, "inv-1", Map.of("setorId", UUID.randomUUID()))))
                .isEqualTo("SETOR_INEXISTENTE");
        assertThat(motivo(transferirProximo(atendimento, "inv-2", Map.of("setorId", inativo)))).isEqualTo("SETOR_INATIVO");
        ResponseEntity<String> diretoriaResposta = transferirProximo(atendimento, "inv-3", Map.of("setorId", diretoria));
        assertThat(diretoriaResposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(motivo(diretoriaResposta)).isEqualTo("SETOR_NAO_RECEBE_DA_TRIAGEM");
        assertThat(dono(atendimento)).isNull();
    }

    @Test
    @DisplayName("Diretoria (setor que nao recebe da triagem) nunca recebe lead da triagem, nem sem setor no corpo")
    void diretoriaNuncaRecebeDaTriagem() {
        ligarSetores();
        UUID vendas = criarSetor("Vendas", true);
        UUID diretoria = criarSetor("Diretoria", false);
        UUID vendedor = criarAtendente("VENDEDOR", vendas);
        UUID diretor = criarAtendente("DIRETOR", diretoria);
        criarAbertos("vendedor", vendedor, 3); // o diretor, ocioso, seria o escolhido pelo criterio de carga

        assertThat(disponiveis()).containsExactly(vendedor).doesNotContain(diretor);
        UUID atendimento = criarAtendimentoNaIa("diretoria");
        assertThat(transferirProximo(atendimento, "diretoria-1", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isEqualTo(vendedor);
    }

    @Test
    @DisplayName("sem setor ligado a capacidade, o diretor volta a aparecer: o filtro so existe com a capacidade ligada")
    void comCapacidadeDesligadaODiretorContinuaNaLista() {
        UUID diretoria = criarSetor("Diretoria", false);
        UUID diretor = criarAtendente("DIRETOR", diretoria);

        assertThat(disponiveis()).contains(diretor);
    }

    @Test
    @DisplayName("setor sem ninguem online devolve o 409 de sempre, com o motivo explicado dentro do setor")
    void setorSemDisponivelDevolve409ComMotivo() {
        ligarSetores();
        UUID vendas = criarSetor("Vendas", true);
        UUID suporte = criarSetor("Suporte", true);
        UUID vendedor = criarAtendente("VENDEDOR", vendas);
        criarAtendente("SUPORTE", suporte);
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE id = ?", vendedor);
        UUID atendimento = criarAtendimentoNaIa("sem-online");

        ResponseEntity<String> resposta = transferirProximo(atendimento, "sem-online-1", Map.of("setorId", vendas));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(motivo(resposta)).isEqualTo("SEM_ATENDENTE_ONLINE");
        assertThat(dono(atendimento)).isNull();
    }

    @Test
    @DisplayName("a chave de idempotencia nao serve para outro setor: mesmo setor repete a resposta, outro setor conflita")
    void idempotenciaConsideraOSetor() {
        ligarSetores();
        UUID vendas = criarSetor("Vendas", true);
        UUID suporte = criarSetor("Suporte", true);
        criarAtendente("V", vendas);
        criarAtendente("S", suporte);
        UUID atendimento = criarAtendimentoNaIa("idempotencia");

        ResponseEntity<String> primeira = transferirProximo(atendimento, "idem-1", Map.of("setorId", vendas));
        ResponseEntity<String> repeticao = transferirProximo(atendimento, "idem-1", Map.of("setorId", vendas));
        ResponseEntity<String> outroSetor = transferirProximo(atendimento, "idem-1", Map.of("setorId", suporte));

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeticao.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(repeticao.getBody()).isEqualTo(primeira.getBody());
        assertThat(outroSetor.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- D3b: atendente vinculado --------------------------------------------------------------------------------

    @Test
    @DisplayName("desligado: receber um lead nao grava vinculo e um vinculo existente nao e honrado")
    void desligadoNaoGravaNemHonra() {
        UUID x = criarAtendente("X", null);
        UUID y = criarAtendente("Y", null);
        criarAbertos("x", x, 3); // X mais carregado: o rodizio escolhe Y
        UUID atendimento = criarAtendimentoNaIa("desligado");
        UUID lead = leadDe(atendimento);
        vincular(lead, x);

        assertThat(transferirProximo(atendimento, "des-1", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(dono(atendimento)).isEqualTo(y);
        assertThat(vinculoDe(lead)).as("o vinculo existente fica como estava").isEqualTo(x);
        UUID outro = criarAtendimentoNaIa("desligado-2");
        transferirProximo(outro, "des-2", null);
        assertThat(vinculoDe(leadDe(outro))).as("desligado: nenhum vinculo novo").isNull();
    }

    @Test
    @DisplayName("ligado: quem recebe pelo rodizio vira o vinculado; o lead volta direto a ele, mesmo mais carregado")
    void ligadoGravaEHonraOVinculo() {
        ligarVinculado();
        UUID x = criarAtendente("X", null);
        UUID y = criarAtendente("Y", null);
        criarAbertos("y", y, 2); // X e o mais ocioso: o 1o lead vai para X pelo rodizio
        UUID primeiro = criarAtendimentoNaIa("vinculo-1");
        UUID lead = leadDe(primeiro);

        assertThat(transferirProximo(primeiro, "vin-1", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(primeiro)).isEqualTo(x);
        assertThat(vinculoDe(lead)).isEqualTo(x);

        // O ciclo termina e o cliente volta: novo atendimento em IA, mesmo lead; agora X esta mais carregado que Y.
        finalizarECriarNovoCiclo(primeiro);
        criarAbertos("x-carga", x, 4);
        UUID volta = ultimoAtendimentoDoLead(lead);

        assertThat(transferirProximo(volta, "vin-2", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(volta)).as("vai direto ao vinculado, nao ao menos carregado").isEqualTo(x);
    }

    @Test
    @DisplayName("ligado: vinculado offline, indisponivel para a IA ou desativado cai no rodizio e o novo vira o vinculado")
    void vinculadoIndisponivelCaiNoRodizioENovoViraVinculado() {
        ligarVinculado();
        for (String indisponibilidade : List.of(
                "UPDATE usuario SET status_presenca = 'OFFLINE' WHERE id = ?",
                "UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE WHERE atendente_id = ?",
                "UPDATE usuario SET ativo = FALSE WHERE id = ?")) {
            UUID x = criarAtendente("X", null);
            UUID y = criarAtendente("Y", null);
            UUID atendimento = criarAtendimentoNaIa("indisponivel");
            UUID lead = leadDe(atendimento);
            vincular(lead, x);
            jdbc.update(indisponibilidade, x);

            ResponseEntity<String> resposta = transferirProximo(atendimento, "indisp-" + sequencia.incrementAndGet(), null);

            assertThat(resposta.getStatusCode()).as(indisponibilidade).isEqualTo(HttpStatus.OK);
            assertThat(dono(atendimento)).as(indisponibilidade).isEqualTo(y);
            assertThat(vinculoDe(lead)).as("o novo atendente vira o vinculado: " + indisponibilidade).isEqualTo(y);
            jdbc.update("DELETE FROM atendimento WHERE lead_id = ?", lead);
            jdbc.update("DELETE FROM lead WHERE id = ?", lead); // cascata: apaga o vinculo antes de limpar usuarios
            jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE', ativo = FALSE WHERE id IN (?, ?)", x, y);
        }
    }

    @Test
    @DisplayName("ligado com setores: vinculado de setor que nao recebe da triagem nao e honrado")
    void vinculadoEmSetorQueNaoRecebeNaoERespeitado() {
        ligarVinculado();
        ligarSetores();
        UUID diretoria = criarSetor("Diretoria", false);
        UUID vendas = criarSetor("Vendas", true);
        UUID diretor = criarAtendente("DIRETOR", diretoria);
        UUID vendedor = criarAtendente("VENDEDOR", vendas);
        UUID atendimento = criarAtendimentoNaIa("vinculo-diretoria");
        vincular(leadDe(atendimento), diretor);

        assertThat(transferirProximo(atendimento, "vd-1", null).getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(dono(atendimento)).isEqualTo(vendedor);
    }

    @Test
    @DisplayName("negativo RN-CRM-01/02: ser o vinculado nao da acesso ao lead de outro responsavel")
    void vinculoNaoDaAcessoAoLead() {
        ligarVinculado();
        UUID ana = idDoUsuario("ana@dev.local");
        UUID bruno = idDoUsuario("bruno@dev.local");
        UUID lead = criarLead("acesso", bruno, "EM_ATENDIMENTO");
        vincular(lead, ana);
        String tokenAna = ApoioAutenticacao.login(http, "ana@dev.local", ApoioAutenticacao.SENHA_ATENDENTE).accessToken();

        ResponseEntity<String> ficha = ApoioAutenticacao.comToken(
                http, tokenAna, HttpMethod.GET, "/api/v1/leads/" + lead, String.class);

        assertThat(ficha.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT atendente_responsavel_id FROM lead WHERE id = ?", UUID.class, lead))
                .as("o vinculo nao muda o responsavel (RN-CRM-02)")
                .isEqualTo(bruno);
    }

    @Test
    @DisplayName("negativo RN-CRM-06: o rodizio nunca tira da mao do colega um atendimento que ja tem dono, vinculado ou nao")
    void vinculoNaoPermiteTomarAtendimentoComDono() {
        ligarVinculado();
        UUID dono = criarAtendente("DONO", null);
        UUID vinculado = criarAtendente("VINCULADO", null);
        UUID atendimento = criarAtendimento("com-dono", "EM_ATENDIMENTO", dono, Instant.now());
        vincular(leadDe(atendimento), vinculado);

        ResponseEntity<String> resposta = transferirProximo(atendimento, "dono-1", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dono(atendimento)).isEqualTo(dono);
    }

    // --- apoio ---------------------------------------------------------------------------------------------------

    private List<UUID> disponiveis() {
        ResponseEntity<String> resposta = get("/internal/v1/atendentes/disponiveis");
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            List<UUID> ids = new java.util.ArrayList<>();
            for (JsonNode item : json.readTree(resposta.getBody())) {
                ids.add(UUID.fromString(item.get("usuarioId").asText()));
            }
            // So os atendentes deste teste: os do seed ficam OFFLINE e os demais testes limpam os seus.
            return ids.stream().filter(this::eDesteTeste).toList();
        } catch (Exception erro) {
            throw new AssertionError("resposta de disponiveis invalida", erro);
        }
    }

    private boolean eDesteTeste(UUID id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT nome LIKE ? FROM usuario WHERE id = ?", Boolean.class, PREFIXO + "%", id));
    }

    private ResponseEntity<String> get(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
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

    private String motivo(ResponseEntity<String> resposta) {
        try {
            return json.readTree(resposta.getBody()).path("motivo").asText();
        } catch (Exception erro) {
            throw new AssertionError("corpo sem JSON: " + resposta.getBody(), erro);
        }
    }

    private UUID criarSetor(String nome, boolean recebeDaTriagem) {
        return jdbc.queryForObject(
                "INSERT INTO setor (nome, recebe_da_triagem) VALUES (?, ?) RETURNING id",
                UUID.class,
                PREFIXO + nome,
                recebeDaTriagem);
    }

    private UUID criarAtendente(String marcador, UUID setor) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE)",
                id, PREFIXO + marcador, id + "@e229.invalid", senha);
        jdbc.update("INSERT INTO disponibilidade_atendente_ia (atendente_id,disponivel_para_ia) VALUES (?,TRUE)", id);
        if (setor != null) {
            jdbc.update("INSERT INTO usuario_setor (usuario_id, setor_id) VALUES (?, ?)", id, setor);
        }
        return id;
    }

    private void criarAbertos(String marcador, UUID dono, int quantidade) {
        for (int indice = 0; indice < quantidade; indice++) {
            criarAtendimento(marcador + "-aberto-" + indice, "EM_ATENDIMENTO", dono, Instant.now());
        }
    }

    private UUID criarAtendimentoNaIa(String marcador) {
        return criarAtendimento(marcador, "EM_IA", null, Instant.now());
    }

    private UUID criarAtendimento(String marcador, String status, UUID dono, Instant iniciadoEm) {
        UUID lead = criarLead(marcador, dono, dono == null ? "IA" : "EM_ATENDIMENTO");
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,?,?::status_atendimento,?)",
                atendimento, lead, dono, status, Timestamp.from(iniciadoEm));
        return atendimento;
    }

    private UUID criarLead(String marcador, UUID dono, String status) {
        UUID lead = UUID.randomUUID();
        String telefone = "55619038" + String.format("%06d", sequencia.incrementAndGet());
        Timestamp agora = Timestamp.from(Instant.now());
        jdbc.update(
                "INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,?,?::uuid,?::status_basico_lead,?,?)",
                lead, PREFIXO + marcador, telefone, dono, status, agora, agora);
        return lead;
    }

    private void finalizarECriarNovoCiclo(UUID atendimentoFinalizado) {
        UUID lead = leadDe(atendimentoFinalizado);
        jdbc.update("UPDATE atendimento SET status = 'FINALIZADO'::status_atendimento, finalizado_em = now() WHERE id = ?", atendimentoFinalizado);
        // Finalizar limpa o responsavel (RetornoDoLeadFinalizadoIT): o cliente volta sem dono, em IA.
        jdbc.update("UPDATE lead SET atendente_responsavel_id = NULL, status_basico = 'IA'::status_basico_lead WHERE id = ?", lead);
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,NULL,'EM_IA'::status_atendimento,now())",
                UUID.randomUUID(), lead);
    }

    private UUID ultimoAtendimentoDoLead(UUID lead) {
        return jdbc.queryForObject(
                "SELECT id FROM atendimento WHERE lead_id = ? ORDER BY iniciado_em DESC LIMIT 1", UUID.class, lead);
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

    private UUID leadDe(UUID atendimento) {
        return jdbc.queryForObject("SELECT lead_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    private UUID dono(UUID atendimento) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }

    private UUID idDoUsuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }
}
