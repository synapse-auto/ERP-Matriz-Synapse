package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.equipe.application.disponibilidade.ListarAtendentesDisponiveisUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * E222: o 409 de {@code transferir-proximo-humano} nunca dizia por que o rodizio ficou vazio. Aqui se prova que cada
 * filtro que zera vira um {@code motivo} no corpo e um WARN com contagens, e que nada do contrato antigo mudou
 * (status, titulo e mensagem) nem vaza dado pessoal no log.
 *
 * <p>O estado do rodizio (usuarios e disponibilidade) e global e a base de teste ja traz usuarios do seed, entao cada
 * teste coloca todos num estado conhecido e o {@code @AfterEach} devolve tudo como estava.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(
        properties = {
            "synapse.seguranca.token-interno=e222-token",
            "synapse.canal.whatsapp.provedor=fake",
            "synapse.canal.outbox.intervalo-ms=3600000",
            "synapse.canal.webhook.intervalo-ms=3600000"
        })
class TransferenciaSemDestinoMotivoIT extends PostgresIT {

    private static final String TOKEN = "e222-token";
    private static final String PREFIXO = "E222-";
    private static final String MARCADOR = "[TRANSFERENCIA_SEM_DESTINO]";
    private static final String MENSAGEM = "nenhum atendente esta online e disponivel para receber a conversa";
    private static final String CHAVE_ESTRATEGIA = "ia.distribuicao.sequencial";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockitoSpyBean private ListarAtendentesDisponiveisUseCase disponiveis;

    private List<Map<String, Object>> usuariosAntes;
    private List<Map<String, Object>> disponibilidadeAntes;
    private String estrategiaAntes;

    @BeforeEach
    void guardarEstadoDoRodizio() {
        usuariosAntes = jdbc.queryForList("SELECT id, ativo, status_presenca::text AS presenca FROM usuario");
        disponibilidadeAntes = jdbc.queryForList(
                "SELECT atendente_id, disponivel_para_ia, atualizado_em FROM disponibilidade_atendente_ia");
        estrategiaAntes = jdbc.queryForObject(
                "SELECT valor FROM configuracao_automacao WHERE chave = ?", String.class, CHAVE_ESTRATEGIA);
    }

    @AfterEach
    void restaurar() {
        jdbc.update("DELETE FROM comando_automacao_idempotencia WHERE atendimento_id IN"
                + " (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM outbox_evento WHERE payload->>'leadId' IN (SELECT id::text FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM disponibilidade_atendente_ia");
        for (Map<String, Object> linha : disponibilidadeAntes) {
            jdbc.update(
                    "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia, atualizado_em)"
                            + " VALUES (?,?,?)",
                    linha.get("atendente_id"),
                    linha.get("disponivel_para_ia"),
                    linha.get("atualizado_em"));
        }
        for (Map<String, Object> linha : usuariosAntes) {
            jdbc.update(
                    "UPDATE usuario SET ativo = ?, status_presenca = CAST(? AS status_presenca) WHERE id = ?",
                    linha.get("ativo"),
                    linha.get("presenca"),
                    linha.get("id"));
        }
        jdbc.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", estrategiaAntes, CHAVE_ESTRATEGIA);
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
    }

    // --- um motivo por filtro que zera ---------------------------------------------------------------------

    @Test
    void semUsuarioComPapelQueRecebeAtendimentoOMotivoEhElegivel(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("ELEGIVEL");
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE papel IN ('ATENDENTE','SUBGESTOR')");

        ResponseEntity<String> resposta = proximoHumano(atendimento, "elegivel-1");

        assertContratoAntigoIntacto(resposta);
        assertThat(motivo(resposta)).isEqualTo("SEM_ATENDENTE_ELEGIVEL");
        assertThat(saida.getAll())
                .contains(MARCADOR, atendimento.toString(), "motivo=SEM_ATENDENTE_ELEGIVEL", "comPapelPermitido=0");
        assertThat(dono(atendimento)).isNull();
    }

    @Test
    void elegiveisSemNenhumMarcadoParaIaOMotivoEhDisponivelParaIa(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("SEM-FLAG");
        UUID tester = atendente("SEM-FLAG", "ONLINE");
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE");
        jdbc.update("UPDATE usuario SET status_presenca = 'ONLINE' WHERE id = ?", tester);

        ResponseEntity<String> resposta = proximoHumano(atendimento, "sem-flag-1");

        assertContratoAntigoIntacto(resposta);
        assertThat(motivo(resposta)).isEqualTo("SEM_ATENDENTE_DISPONIVEL_PARA_IA");
        assertThat(saida.getAll()).contains("motivo=SEM_ATENDENTE_DISPONIVEL_PARA_IA", "disponiveisParaIa=0");
    }

    @Test
    void quemNuncaTeveLinhaDeDisponibilidadeContaComoSemRegistroENaoEntraNoRodizio(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("SEM-LINHA");
        UUID tester = atendente("SEM-LINHA", "ONLINE");
        jdbc.update("DELETE FROM disponibilidade_atendente_ia");
        jdbc.update("UPDATE usuario SET status_presenca = 'ONLINE' WHERE id = ?", tester);

        ResponseEntity<String> resposta = proximoHumano(atendimento, "sem-linha-1");

        assertContratoAntigoIntacto(resposta);
        assertThat(motivo(resposta)).isEqualTo("SEM_ATENDENTE_DISPONIVEL_PARA_IA");
        // O log distingue "desligou a opcao" de "nunca teve linha": a coluna so mede o segundo caso.
        assertThat(saida.getAll()).containsPattern("semRegistroDeDisponibilidade=[1-9]");
    }

    @Test
    void marcadosParaIaMasNenhumOnlineOMotivoEhOnline(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("OFFLINE");
        UUID tester = atendente("OFFLINE", "OFFLINE");
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = TRUE WHERE atendente_id = ?", tester);
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE'");

        ResponseEntity<String> resposta = proximoHumano(atendimento, "offline-1");

        assertContratoAntigoIntacto(resposta);
        assertThat(motivo(resposta)).isEqualTo("SEM_ATENDENTE_ONLINE");
        assertThat(saida.getAll()).contains("motivo=SEM_ATENDENTE_ONLINE", "online=0");
        assertThat(saida.getAll()).containsPattern("disponiveisParaIa=[1-9]");
    }

    @Test
    void ausenteNaoContaComoOnline() {
        UUID atendimento = atendimentoNaIa("AUSENTE");
        UUID tester = atendente("AUSENTE", "AUSENTE");
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = TRUE WHERE atendente_id = ?", tester);
        jdbc.update("UPDATE usuario SET status_presenca = 'AUSENTE'");

        assertThat(motivo(proximoHumano(atendimento, "ausente-1"))).isEqualTo("SEM_ATENDENTE_ONLINE");
    }

    // --- o log -----------------------------------------------------------------------------------------------

    @Test
    void oWarnTrazOFunilCompletoOLeadComResponsavelEaEstrategiaSemDadoPessoal(CapturedOutput saida) {
        UUID dono = atendente("DONO", "ONLINE");
        UUID atendimento = atendimentoNaIa("LOG-COMPLETO", dono);
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE'");
        jdbc.update("UPDATE configuracao_automacao SET valor = 'true' WHERE chave = ?", CHAVE_ESTRATEGIA);

        proximoHumano(atendimento, "log-1");

        String linha = linhaDoWarn(saida, atendimento);
        assertThat(linha)
                .contains("estrategia=SEQUENCIAL")
                .contains("atendimentoStatus=EM_IA")
                .contains("atendimentoComAtendente=false")
                .contains("leadComResponsavel=true")
                .containsPattern("ativos=\\d+")
                .containsPattern("comPapelPermitido=\\d+")
                .containsPattern("disponiveisParaIa=\\d+")
                .contains("online=0");
        // Sem nome, e-mail ou telefone: so contagens, o id do atendimento e estados.
        assertThat(linha)
                .doesNotContain(PREFIXO + "DONO")
                .doesNotContain("@e222.invalid")
                .doesNotContainPattern("55\\d{9,}");
    }

    @Test
    void estrategiaMenorCargaApareceNoLog(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("MENOR-CARGA");
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE papel IN ('ATENDENTE','SUBGESTOR')");
        jdbc.update("UPDATE configuracao_automacao SET valor = 'false' WHERE chave = ?", CHAVE_ESTRATEGIA);

        proximoHumano(atendimento, "menor-carga-1");

        assertThat(linhaDoWarn(saida, atendimento)).contains("estrategia=MENOR_CARGA");
    }

    // --- o que nao pode mudar ----------------------------------------------------------------------------------

    @Test
    void comAtendenteElegivelTransfereSemMotivoENaoRegistraOWarn(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("SUCESSO");
        UUID tester = atendente("SUCESSO", "ONLINE");
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE WHERE atendente_id <> ?", tester);
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE id <> ?", tester);

        ResponseEntity<String> resposta = proximoHumano(atendimento, "sucesso-1");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).doesNotContain("motivo");
        assertThat(dono(atendimento)).isEqualTo(tester);
        assertThat(saida.getAll()).doesNotContain(MARCADOR + " atendimentoId=" + atendimento);
    }

    @Test
    void outro409DoMesmoEndpointNaoGanhaMotivo() {
        UUID tester = atendente("OUTRO-409", "ONLINE");
        UUID atendimento = atendimentoNaIa("EM-ATENDIMENTO", tester, "EM_ATENDIMENTO");

        ResponseEntity<String> resposta = proximoHumano(atendimento, "outro-409-1");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resposta.getBody()).doesNotContain("motivo").doesNotContain(MENSAGEM);
    }

    @Test
    void seODiagnosticoFalharOConflitoContinuaIgualEOMotivoViraNaoDeterminado(CapturedOutput saida) {
        UUID atendimento = atendimentoNaIa("DIAGNOSTICO-FALHA");
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE papel IN ('ATENDENTE','SUBGESTOR')");
        // O stub chama o metodo real uma vez para registrar a expectativa, e ele exige papel SERVICO.
        ContextoDeServico.executarComo(
                "e222-teste",
                () -> doThrow(new IllegalStateException("banco indisponivel para o diagnostico"))
                        .when(disponiveis)
                        .diagnosticar());

        ResponseEntity<String> resposta = proximoHumano(atendimento, "falha-1");

        assertContratoAntigoIntacto(resposta);
        assertThat(motivo(resposta)).isEqualTo("NAO_DETERMINADO");
        assertThat(saida.getAll()).contains(MARCADOR, "diagnostico indisponivel", "tipoErro=IllegalStateException");
        // O texto do erro nunca vai para o log, so a classe.
        assertThat(saida.getAll()).doesNotContain("banco indisponivel para o diagnostico");
    }

    // --- apoio ---------------------------------------------------------------------------------------------------

    private void assertContratoAntigoIntacto(ResponseEntity<String> resposta) {
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode corpo = corpo(resposta);
        assertThat(corpo.path("status").asInt()).isEqualTo(409);
        assertThat(corpo.path("title").asText()).isEqualTo("Operacao nao pode ser aplicada");
        assertThat(corpo.path("detail").asText()).isEqualTo(MENSAGEM);
    }

    private String motivo(ResponseEntity<String> resposta) {
        return corpo(resposta).path("motivo").asText();
    }

    private JsonNode corpo(ResponseEntity<String> resposta) {
        try {
            return json.readTree(resposta.getBody());
        } catch (Exception erro) {
            throw new AssertionError("corpo ilegivel: " + resposta.getBody(), erro);
        }
    }

    private static String linhaDoWarn(CapturedOutput saida, UUID atendimento) {
        return saida.getAll()
                .lines()
                .filter(linha -> linha.contains(MARCADOR) && linha.contains(atendimento.toString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("nenhum WARN " + MARCADOR + " para " + atendimento));
    }

    private ResponseEntity<String> proximoHumano(UUID atendimento, String chave) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", TOKEN);
        cabecalhos.set("Idempotency-Key", chave);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/internal/v1/atendimentos/" + atendimento + "/transferir-proximo-humano",
                HttpMethod.POST,
                new HttpEntity<>(null, cabecalhos),
                String.class);
    }

    private UUID atendimentoNaIa(String marcador) {
        return atendimentoNaIa(marcador, null, "EM_IA");
    }

    private UUID atendimentoNaIa(String marcador, UUID responsavelDoLead) {
        return atendimentoNaIa(marcador, responsavelDoLead, "EM_IA");
    }

    private UUID atendimentoNaIa(String marcador, UUID responsavelDoLead, String status) {
        UUID lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,"
                        + "ultima_mensagem_do_lead_em) VALUES (?,?,?,?::uuid,?::status_basico_lead,now(),now())",
                lead,
                PREFIXO + marcador,
                "55619022" + String.format("%05d", Math.abs(marcador.hashCode()) % 100000),
                responsavelDoLead,
                responsavelDoLead == null ? "IA" : "EM_ATENDIMENTO");
        UUID atendimento = UUID.randomUUID();
        UUID atendente = "EM_IA".equals(status) ? null : responsavelDoLead;
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em)"
                        + " VALUES (?,?,?::uuid,?::status_atendimento,now())",
                atendimento,
                lead,
                atendente,
                status);
        return atendimento;
    }

    private UUID atendente(String marcador, String presenca) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo)"
                        + " VALUES (?,?,?,?,'ATENDENTE',CAST(? AS status_presenca),TRUE)",
                id,
                PREFIXO + marcador,
                id + "@e222.invalid",
                senha,
                presenca);
        jdbc.update("INSERT INTO disponibilidade_atendente_ia (atendente_id,disponivel_para_ia) VALUES (?,TRUE)", id);
        return id;
    }

    private UUID dono(UUID atendimento) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }
}
