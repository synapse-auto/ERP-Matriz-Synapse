package com.synapse.crm.app.equipe;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ADMINISTRADOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * E223 (PR A): toda mudanca de presenca deixa rastro, e nada do comportamento de hoje muda (o rodizio continua
 * exigindo ONLINE + marcado para a IA). A migration so cria a linha de disponibilidade que falta, sempre FALSE.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=e223-token")
class PresencaHistoricoIT extends PostgresIT {

    private static final String PREFIXO = "E223-";
    private static final String TOKEN_INTERNO = "e223-token";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapeador;

    private UUID ana;
    private String presencaDaAnaAntes;
    private Boolean flagDaAnaAntes;

    @BeforeEach
    void guardarEstadoDaAna() {
        ana = jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, EMAIL_ANA);
        presencaDaAnaAntes =
                jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id = ?", String.class, ana);
        flagDaAnaAntes = flagDe(ana);
        jdbc.update("DELETE FROM presenca_historico WHERE usuario_id = ?", ana);
    }

    @AfterEach
    void restaurar() {
        jdbc.update("DELETE FROM presenca_historico WHERE usuario_id = ?", ana);
        jdbc.update(
                "UPDATE usuario SET status_presenca = CAST(? AS status_presenca) WHERE id = ?", presencaDaAnaAntes, ana);
        if (flagDaAnaAntes == null) {
            jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id = ?", ana);
        } else {
            jdbc.update(
                    "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia) VALUES (?,?)"
                            + " ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = EXCLUDED.disponivel_para_ia",
                    ana,
                    flagDaAnaAntes);
        }
        jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN"
                + " (SELECT id FROM usuario WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
    }

    // --- mudanca manual grava historico --------------------------------------------------------------------

    @Test
    void cadaMudancaManualGravaUmaLinhaComEstadoAnteriorENovoEOrigemManual() {
        definirPresenca(ana, "OFFLINE");
        String token = tokenDaAna();

        assertThat(patchPresenca(token, "ONLINE").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchPresenca(token, "AUSENTE").getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Map<String, Object>> linhas = historico(ana);
        assertThat(linhas).hasSize(2);
        assertThat(linhas.get(0))
                .containsEntry("estado_anterior", "OFFLINE")
                .containsEntry("estado_novo", "ONLINE")
                .containsEntry("origem", "MANUAL")
                .containsEntry("motivo", null);
        assertThat(linhas.get(1))
                .containsEntry("estado_anterior", "ONLINE")
                .containsEntry("estado_novo", "AUSENTE")
                .containsEntry("origem", "MANUAL");
        assertThat(presencaDe(ana)).isEqualTo("AUSENTE");
    }

    @Test
    void repetirOMesmoEstadoNaoGravaHistorico() {
        definirPresenca(ana, "ONLINE");

        ResponseEntity<String> resposta = patchPresenca(tokenDaAna(), "ONLINE");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(corpo(resposta).path("status").asText()).isEqualTo("ONLINE");
        assertThat(historico(ana)).isEmpty();
    }

    @Test
    void cadaMudancaGravaUmInfoSoComIdsEEstados(CapturedOutput saida) {
        definirPresenca(ana, "OFFLINE");

        patchPresenca(tokenDaAna(), "ONLINE");

        String linha = saida.getAll().lines().filter(l -> l.contains("[PRESENCA_ALTERADA]")).findFirst().orElseThrow();
        assertThat(linha)
                .contains("usuarioId=" + ana, "de=OFFLINE", "para=ONLINE", "origem=MANUAL")
                .contains(" INFO ")
                .doesNotContain(EMAIL_ANA);
    }

    @Test
    void presencaInvalidaNaoGravaNada() {
        definirPresenca(ana, "OFFLINE");

        ResponseEntity<String> resposta = patchPresenca(tokenDaAna(), "VOANDO");

        assertThat(resposta.getStatusCode().is4xxClientError()).isTrue();
        assertThat(historico(ana)).isEmpty();
        assertThat(presencaDe(ana)).isEqualTo("OFFLINE");
    }

    @Test
    void desativarUsuarioOnlineGravaHistoricoDoSistemaComMotivo() {
        UUID alvo = usuario("DESATIVAR", "ATENDENTE", "ONLINE", true);

        ResponseEntity<String> resposta =
                chamar(tokenDoAdmin(), HttpMethod.PATCH, "/api/v1/usuarios/" + alvo + "/desativar", null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(historico(alvo)).singleElement().satisfies(l -> assertThat(l)
                .containsEntry("estado_anterior", "ONLINE")
                .containsEntry("estado_novo", "OFFLINE")
                .containsEntry("origem", "SISTEMA")
                .containsEntry("motivo", "DESATIVACAO"));
    }

    @Test
    void desativarQuemJaEstavaOfflineNaoGravaHistorico() {
        UUID alvo = usuario("JA-OFFLINE", "ATENDENTE", "OFFLINE", true);

        chamar(tokenDoAdmin(), HttpMethod.PATCH, "/api/v1/usuarios/" + alvo + "/desativar", null);

        assertThat(historico(alvo)).isEmpty();
    }

    // --- o rodizio nao mudou ----------------------------------------------------------------------------------

    @Test
    void rodizioContinuaExigindoOnlineEMarcadoParaIa() {
        ligarParaIa(ana);
        definirPresenca(ana, "OFFLINE");
        assertThat(rodizio()).doesNotContain(ana);

        patchPresenca(tokenDaAna(), "ONLINE");
        assertThat(rodizio()).contains(ana);

        patchPresenca(tokenDaAna(), "AUSENTE");
        assertThat(rodizio()).doesNotContain(ana);

        patchPresenca(tokenDaAna(), "ONLINE");
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE WHERE atendente_id = ?", ana);
        assertThat(rodizio()).doesNotContain(ana);
    }

    // --- usuario novo ganha a linha ---------------------------------------------------------------------------

    @Test
    void usuarioCriadoPelaGestaoGanhaLinhaDeDisponibilidadeDesligadaEFicaForaDoRodizio() {
        ResponseEntity<String> criado = chamar(
                tokenDoAdmin(),
                HttpMethod.POST,
                "/api/v1/usuarios",
                Map.of(
                        "nome", PREFIXO + "NOVO",
                        "email", "novo@e223.invalid",
                        "senha", "Senha-e223-Forte-12345",
                        "papel", "ATENDENTE"));

        assertThat(criado.getStatusCode()).as(criado.getBody()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(corpo(criado).path("id").asText());
        assertThat(flagDe(id)).isEqualTo(Boolean.FALSE);
        // Nem ONLINE o coloca no rodizio: ninguem entra sem a gestao ligar o toggle.
        definirPresenca(id, "ONLINE");
        assertThat(rodizio()).doesNotContain(id);
    }

    // --- migration de preenchimento ------------------------------------------------------------------------------

    @Test
    void preenchimentoCriaSoAsLinhasQueFaltamParaAtivosDosPapeisQueRecebemEnaoTocaNasExistentes() throws Exception {
        UUID semLinha = usuario("SEM-LINHA", "ATENDENTE", "OFFLINE", false);
        UUID subSemLinha = usuario("SUB-SEM-LINHA", "SUBGESTOR", "OFFLINE", false);
        UUID inativoSemLinha = usuario("INATIVO", "ATENDENTE", "OFFLINE", false);
        jdbc.update("UPDATE usuario SET ativo = FALSE WHERE id = ?", inativoSemLinha);
        UUID gestorSemLinha = usuario("GESTOR", "GESTOR", "OFFLINE", false);
        UUID comLinhaLigada = usuario("COM-LINHA", "ATENDENTE", "OFFLINE", false);
        jdbc.update(
                "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia, atualizado_em)"
                        + " VALUES (?, TRUE, TIMESTAMPTZ '2026-01-02 03:04:05+00')",
                comLinhaLigada);

        jdbc.execute(preenchimentoDaMigration());

        assertThat(flagDe(semLinha)).isEqualTo(Boolean.FALSE);
        assertThat(flagDe(subSemLinha)).isEqualTo(Boolean.FALSE);
        assertThat(flagDe(inativoSemLinha)).isNull();
        assertThat(flagDe(gestorSemLinha)).isNull();
        assertThat(flagDe(comLinhaLigada)).isEqualTo(Boolean.TRUE);
        assertThat(jdbc.queryForObject(
                        "SELECT atualizado_em = TIMESTAMPTZ '2026-01-02 03:04:05+00' FROM disponibilidade_atendente_ia"
                                + " WHERE atendente_id = ?",
                        Boolean.class,
                        comLinhaLigada))
                .isTrue();
        // Rodar de novo nao muda nada (idempotente).
        jdbc.execute(preenchimentoDaMigration());
        assertThat(flagDe(comLinhaLigada)).isEqualTo(Boolean.TRUE);
        assertThat(flagDe(semLinha)).isEqualTo(Boolean.FALSE);
    }

    // --- a tabela se defende (viola de proposito) ---------------------------------------------------------------

    @Test
    void tabelaRecusaTransicaoSemMudancaEOrigemDesconhecida() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO presenca_historico(usuario_id, estado_anterior, estado_novo, origem)"
                                + " VALUES (?, 'ONLINE', 'ONLINE', 'MANUAL')",
                        ana))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO presenca_historico(usuario_id, estado_anterior, estado_novo, origem)"
                                + " VALUES (?, 'OFFLINE', 'ONLINE', 'ALGUEM')",
                        ana))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- apoio -----------------------------------------------------------------------------------------------------

    private static String preenchimentoDaMigration() throws Exception {
        String sql = new String(
                new ClassPathResource("db/migration/V98__presenca_historico.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int inicio = sql.indexOf("INSERT INTO disponibilidade_atendente_ia");
        assertThat(inicio).as("o preenchimento esta na migration").isPositive();
        return sql.substring(inicio);
    }

    private List<UUID> rodizio() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.set("X-Synapse-Token", TOKEN_INTERNO);
        ResponseEntity<String> resposta = http.exchange(
                "/internal/v1/atendentes/disponiveis", HttpMethod.GET, new HttpEntity<>(cabecalhos), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<UUID> ids = new ArrayList<>();
        corpo(resposta).forEach(n -> ids.add(UUID.fromString(n.path("usuarioId").asText())));
        return ids;
    }

    private List<Map<String, Object>> historico(UUID usuario) {
        return jdbc.queryForList(
                "SELECT estado_anterior::text AS estado_anterior, estado_novo::text AS estado_novo, origem, motivo"
                        + " FROM presenca_historico WHERE usuario_id = ? ORDER BY criado_em, id",
                usuario);
    }

    private Boolean flagDe(UUID usuario) {
        return jdbc.query(
                "SELECT disponivel_para_ia FROM disponibilidade_atendente_ia WHERE atendente_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : null,
                usuario);
    }

    private void ligarParaIa(UUID usuario) {
        jdbc.update(
                "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia) VALUES (?, TRUE)"
                        + " ON CONFLICT (atendente_id) DO UPDATE SET disponivel_para_ia = TRUE",
                usuario);
    }

    private void definirPresenca(UUID usuario, String estado) {
        jdbc.update("UPDATE usuario SET status_presenca = CAST(? AS status_presenca) WHERE id = ?", estado, usuario);
    }

    private String presencaDe(UUID usuario) {
        return jdbc.queryForObject("SELECT status_presenca::text FROM usuario WHERE id = ?", String.class, usuario);
    }

    private UUID usuario(String marcador, String papel, String presenca, boolean comLinha) {
        UUID id = UUID.randomUUID();
        String senha =
                jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo)"
                        + " VALUES (?,?,?,?,CAST(? AS papel_usuario),CAST(? AS status_presenca),TRUE)",
                id,
                PREFIXO + marcador,
                id + "@e223.invalid",
                senha,
                papel,
                presenca);
        if (comLinha) {
            jdbc.update(
                    "INSERT INTO disponibilidade_atendente_ia(atendente_id, disponivel_para_ia) VALUES (?, FALSE)", id);
        }
        return id;
    }

    private String tokenDaAna() {
        return ApoioAutenticacao.login(http, EMAIL_ANA, SENHA_ATENDENTE).accessToken();
    }

    private String tokenDoAdmin() {
        return ApoioAutenticacao.login(http, EMAIL_ADMINISTRADOR, SENHA_ADMINISTRADOR).accessToken();
    }

    private ResponseEntity<String> patchPresenca(String token, String estado) {
        return chamar(token, HttpMethod.PATCH, "/api/v1/usuarios/me/presenca", Map.of("status", estado));
    }

    private ResponseEntity<String> chamar(String token, HttpMethod metodo, String rota, Object corpo) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(rota, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    private JsonNode corpo(ResponseEntity<String> resposta) {
        try {
            return mapeador.readTree(resposta.getBody());
        } catch (Exception erro) {
            throw new AssertionError("corpo ilegivel: " + resposta.getBody(), erro);
        }
    }
}
