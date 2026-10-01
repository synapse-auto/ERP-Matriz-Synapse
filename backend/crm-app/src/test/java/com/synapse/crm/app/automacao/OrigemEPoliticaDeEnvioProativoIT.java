package com.synapse.crm.app.automacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import com.synapse.crm.automacaoconfig.infrastructure.ChavesDeCacheConfiguracaoAutomacao;

/**
 * E219 pelos pontos de entrada HTTP. Bloco 1: toda mensagem automatica grava a origem, e o fluxo
 * antigo (sem os campos) continua funcionando com aviso. Bloco 2: a reserva proativa aplica chave,
 * ocorrencia, liga/desliga, cooldown e teto, fecha na transacao do registro e nunca bloqueia a
 * resposta ao lead. O "provedor" e simulado: cada envio autorizado vira um POST /mensagens-enviadas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=e219-token")
@ExtendWith(OutputCaptureExtension.class)
class OrigemEPoliticaDeEnvioProativoIT extends PostgresIT {

    private static final String TOKEN = "e219-token";
    private static final String PREFIXO = "E219-";
    private static final String TELEFONE = "55619219";
    private static final List<String> PARAMETROS = List.of(
            "automacao_proativa.habilitada",
            "automacao_proativa.follow_up.habilitada",
            "automacao_proativa.fidelizacao.habilitada",
            "automacao_proativa.festiva.habilitada",
            "automacao_proativa.aniversario.habilitada",
            "automacao_proativa.avaliacao.habilitada",
            "automacao_proativa.lembrete.habilitada",
            "automacao_proativa.outro.habilitada",
            "automacao_proativa.cooldown_horas",
            "automacao_proativa.teto_diario_por_lead");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private StringRedisTemplate redis;
    @Autowired private ZoneId fuso;

    private int sequencia;

    @AfterEach
    void limpar() {
        String leads = "SELECT id FROM lead WHERE nome LIKE '" + PREFIXO + "%'";
        String atendimentos = "SELECT id FROM atendimento WHERE lead_id IN (" + leads + ")";
        jdbc.update("DELETE FROM envio_proativo_reserva WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM mensagem_origem_automacao WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM comando_automacao_idempotencia WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM mensagem_automacao_idempotencia WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM mensagem_id_externo WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM outbox_evento");
        jdbc.update("DELETE FROM audit_log WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (" + leads + ")");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM atendimento WHERE id IN (" + atendimentos + ")");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM canal_credencial WHERE numero LIKE ?", TELEFONE + "%");
        jdbc.update("DELETE FROM canal WHERE nome LIKE ?", PREFIXO + "%");
        for (String chave : PARAMETROS) {
            configurar(chave, chave.endsWith("habilitada") ? "true" : "0");
        }
    }

    @Nested
    @DisplayName("Bloco 1 — origem")
    class Origem {

        @Test
        @DisplayName("n8n sem os campos de origem continua registrando, grava NAO_INFORMADA e avisa no log")
        void semCampos_registraComAviso(CapturedOutput saida) throws Exception {
            Lead lead = lead("SEM-ORIGEM");

            ResponseEntity<String> resposta = registrar(lead.atendimento(), "wamid.e219-sem", null, null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            String mensagem = json.readTree(resposta.getBody()).path("mensagemId").asText();
            assertThat(origemDa(mensagem)).containsEntry("tipo", "NAO_INFORMADA");
            assertThat(saida.getOut() + saida.getErr())
                    .contains("[ORIGEM_NAO_INFORMADA]")
                    .contains(mensagem);
        }

        @Test
        @DisplayName("n8n com os campos grava tipo, regra e execucao; tipo desconhecido nao derruba o registro")
        void comCampos_gravaOrigem() throws Exception {
            Lead lead = lead("COM-ORIGEM");

            String mensagem = mensagemId(registrar(
                    lead.atendimento(), "wamid.e219-com", null, origem("follow_up", "regra-3d", "exec-99")));
            String desconhecido = mensagemId(registrar(
                    lead.atendimento(), "wamid.e219-desconhecido", null, origem("CAMPANHA_X", null, "exec-100")));

            assertThat(origemDa(mensagem))
                    .containsEntry("tipo", "FOLLOW_UP")
                    .containsEntry("regra_id", "regra-3d")
                    .containsEntry("execucao_id", "exec-99")
                    .containsEntry("lead_id", lead.id());
            assertThat(origemDa(desconhecido))
                    .containsEntry("tipo", "NAO_INFORMADA")
                    .containsEntry("execucao_id", "exec-100");
        }

        @Test
        @DisplayName("POST /responder grava a origem declarada e, sem ela, NAO_INFORMADA sem falhar")
        void responder_gravaOrigem() throws Exception {
            Lead lead = lead("RESPONDER");

            ResponseEntity<String> comOrigem = responder(lead.atendimento(), "chave-e219-1", origem("RESPOSTA_IA", null, "exec-1"));
            ResponseEntity<String> semOrigem = responder(lead.atendimento(), "chave-e219-2", Map.of());

            assertThat(comOrigem.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(semOrigem.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(origemDa(mensagemId(comOrigem)))
                    .containsEntry("tipo", "RESPOSTA_IA")
                    .containsEntry("execucao_id", "exec-1");
            assertThat(origemDa(mensagemId(semOrigem))).containsEntry("tipo", "NAO_INFORMADA");
        }

        @Test
        @DisplayName("visao por origem/dia conta mensagens e leads no fuso da instancia, inclusive IA sem origem")
        void resumoPorOrigem_agrega() throws Exception {
            Lead a = lead("RESUMO-A");
            Lead b = lead("RESUMO-B");
            Lead c = lead("RESUMO-C");
            LocalDate dia = LocalDate.of(2021, 3, 10);
            plantarOrigem(a, "FOLLOW_UP", dia.atTime(9, 0));
            plantarOrigem(a, "FOLLOW_UP", dia.atTime(10, 0));
            plantarOrigem(b, "FOLLOW_UP", dia.atTime(11, 0));
            // 23h30 locais: em fusos a oeste de UTC ja e o dia seguinte em UTC, e ainda conta no dia 10.
            plantarOrigem(b, "FOLLOW_UP", dia.atTime(23, 30));
            plantarOrigem(a, "FESTIVA", dia.atTime(12, 0));
            plantarOrigem(a, "FOLLOW_UP", dia.plusDays(1).atTime(0, 30));
            plantarMensagemDaIaSemOrigem(c, dia.atTime(15, 0));

            ResponseEntity<String> resposta = chamar(
                    HttpMethod.GET, "/internal/v1/envios-automacao/resumo-por-origem?de=" + dia + "&ate=" + dia, null);

            assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode corpo = json.readTree(resposta.getBody());
            assertThat(corpo.path("fuso").asText()).isEqualTo(fuso.getId());
            assertThat(totais(corpo)).containsExactlyInAnyOrder(
                    "2021-03-10 FOLLOW_UP 4 2", "2021-03-10 FESTIVA 1 1", "2021-03-10 SEM_ORIGEM_REGISTRADA 1 1");

            assertThat(chamar(HttpMethod.GET, "/internal/v1/envios-automacao/resumo-por-origem?de=2021-03-10&ate=2021-03-09", null)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(chamar(HttpMethod.GET, "/internal/v1/envios-automacao/resumo-por-origem?de=2021-01-01&ate=2021-04-04", null)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Bloco 2 — reserva com politica de frequencia")
    class Reserva {

        @Test
        @DisplayName("12 reservas simultaneas com a mesma chave: exatamente uma pode enviar")
        void mesmaChaveSimultanea_umaVence() throws Exception {
            Lead lead = lead("CONCORRENCIA");
            String chave = "fu:" + UUID.randomUUID();

            List<HttpStatus> status = emParalelo(12, i -> reservar(lead.id(), "FOLLOW_UP", "regra-1", "2026-10-01", chave));

            assertThat(status).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
            assertThat(status).filteredOn(HttpStatus.OK::equals).hasSize(11);
            assertThat(reservasDo(lead)).isOne();
        }

        @Test
        @DisplayName("teto 1 com 8 ocorrencias diferentes simultaneas: so uma passa (trava por lead)")
        void tetoSobConcorrencia_naoFura() throws Exception {
            configurar("automacao_proativa.teto_diario_por_lead", "1");
            Lead lead = lead("TETO-CONCORRENTE");

            List<HttpStatus> status = emParalelo(
                    8, i -> reservar(lead.id(), "FOLLOW_UP", "regra-" + i, "2026-10-01", "fu:" + UUID.randomUUID()));

            assertThat(status).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
            assertThat(reservasDo(lead)).isOne();
        }

        @Test
        @DisplayName("cooldown bloqueia o mesmo tipo com liberadoApos; outro tipo segue liberado")
        void cooldown() throws Exception {
            configurar("automacao_proativa.cooldown_horas", "24");
            Lead lead = lead("COOLDOWN");
            ResponseEntity<String> primeira = reservar(lead.id(), "FOLLOW_UP", "regra-1", "o1", chave());
            assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Instant reservadoEm = Instant.parse(json.readTree(primeira.getBody()).at("/reserva/reservadoEm").asText());

            JsonNode bloqueada = corpo(reservar(lead.id(), "FOLLOW_UP", "regra-2", "o2", chave()), HttpStatus.OK);
            ResponseEntity<String> outroTipo = reservar(lead.id(), "FESTIVA", "natal", "2026-12-25", chave());

            assertThat(bloqueada.path("podeEnviar").asBoolean()).isFalse();
            assertThat(bloqueada.path("motivo").asText()).isEqualTo("COOLDOWN");
            assertThat(Instant.parse(bloqueada.path("liberadoApos").asText()))
                    .isCloseTo(reservadoEm.plus(Duration.ofHours(24)), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.SECONDS));
            assertThat(outroTipo.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(reservasDo(lead)).isEqualTo(2);
        }

        @Test
        @DisplayName("teto diario soma todos os tipos e tambem as proativas registradas sem reserva")
        void tetoDiario() throws Exception {
            configurar("automacao_proativa.teto_diario_por_lead", "2");
            Lead lead = lead("TETO");
            assertThat(reservar(lead.id(), "FOLLOW_UP", "r", "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(reservar(lead.id(), "FESTIVA", "r", "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.CREATED);

            JsonNode terceira = corpo(reservar(lead.id(), "ANIVERSARIO", "r", "2026", chave()), HttpStatus.OK);

            assertThat(terceira.path("motivo").asText()).isEqualTo("TETO_DIARIO");
            assertThat(terceira.path("liberadoApos").asText()).isNotBlank();

            Lead semReserva = lead("TETO-SEM-RESERVA");
            registrar(semReserva.atendimento(), "wamid.e219-fid-1", null, origem("FIDELIZACAO", "r", null));
            registrar(semReserva.atendimento(), "wamid.e219-fid-2", null, origem("FIDELIZACAO", "r", null));
            assertThat(corpo(reservar(semReserva.id(), "FOLLOW_UP", "r", "o1", chave()), HttpStatus.OK).path("motivo").asText())
                    .isEqualTo("TETO_DIARIO");
        }

        @Test
        @DisplayName("resposta a mensagem do lead nunca e bloqueada nem conta no teto; RESPOSTA_IA nao reserva")
        void respostaAoLead_naoBloqueada() throws Exception {
            configurar("automacao_proativa.teto_diario_por_lead", "1");
            configurar("automacao_proativa.cooldown_horas", "24");
            configurar("automacao_proativa.habilitada", "false");
            Lead lead = lead("RESPOSTA");

            assertThat(registrar(lead.atendimento(), "wamid.e219-resp-1", null, origem("RESPOSTA_IA", null, null)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(registrar(lead.atendimento(), "wamid.e219-resp-2", null, origem("RESPOSTA_IA", null, null)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(responder(lead.atendimento(), "chave-e219-resp", origem("RESPOSTA_IA", null, null)).getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(mensagensDe(lead)).isEqualTo(3);
            assertThat(reservar(lead.id(), "RESPOSTA_IA", null, "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            configurar("automacao_proativa.habilitada", "true");
            assertThat(reservar(lead.id(), "FOLLOW_UP", "r", "o1", chave()).getStatusCode())
                    .as("tres respostas ao lead nao consomem o teto de 1 proativa")
                    .isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("tipo desligado e chave geral desligada respondem nao envie, sem gravar reserva")
        void desligados() throws Exception {
            Lead lead = lead("DESLIGADO");
            configurar("automacao_proativa.festiva.habilitada", "false");

            assertThat(corpo(reservar(lead.id(), "FESTIVA", "natal", "2026-12-25", chave()), HttpStatus.OK).path("motivo").asText())
                    .isEqualTo("TIPO_DESLIGADO");
            assertThat(reservar(lead.id(), "FOLLOW_UP", "r", "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.CREATED);

            configurar("automacao_proativa.habilitada", "false");
            assertThat(corpo(reservar(lead.id(), "AVALIACAO", null, "atd-1", chave()), HttpStatus.OK).path("motivo").asText())
                    .isEqualTo("AUTOMACAO_PROATIVA_DESLIGADA");
            assertThat(reservasDo(lead)).isOne();
        }

        @Test
        @DisplayName("o registro do envio fecha a reserva na mesma transacao e herda a origem dela")
        void registroFechaReserva() throws Exception {
            Lead lead = lead("FECHA");
            String chave = chave();
            assertThat(reservarComExecucao(lead.id(), "FOLLOW_UP", "regra-x", "o1", chave, "exec-1").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);
            assertThat(pendentes()).contains(chave);

            ResponseEntity<String> registro = registrar(lead.atendimento(), "wamid.e219-fecha", chave, Map.of());

            assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.OK);
            String mensagem = mensagemId(registro);
            Map<String, Object> reserva = jdbc.queryForMap(
                    "SELECT estado, mensagem_id::text AS mensagem_id, wamid_saida FROM envio_proativo_reserva WHERE chave = ?", chave);
            assertThat(reserva)
                    .containsEntry("estado", "ENVIADO")
                    .containsEntry("mensagem_id", mensagem)
                    .containsEntry("wamid_saida", "wamid.e219-fecha");
            assertThat(origemDa(mensagem))
                    .containsEntry("tipo", "FOLLOW_UP")
                    .containsEntry("regra_id", "regra-x")
                    .containsEntry("execucao_id", "exec-1");
            assertThat(pendentes()).doesNotContain(chave);

            assertThat(registrar(lead.atendimento(), "wamid.e219-fecha", chave, Map.of()).getBody()).contains("\"idempotente\":true");
            assertThat(registrar(lead.atendimento(), "wamid.e219-segundo", chave, Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            JsonNode repetida = corpo(reservar(lead.id(), "FOLLOW_UP", "regra-x", "o1", chave), HttpStatus.OK);
            assertThat(repetida.path("motivo").asText()).isEqualTo("CHAVE_JA_USADA");
            assertThat(repetida.at("/reserva/estado").asText()).isEqualTo("ENVIADO");
            assertThat(mensagensDe(lead)).isOne();
        }

        @Test
        @DisplayName("reserva sem resultado aparece na lista de conferencia e nao e reenviada")
        void reservaAberta_listada() throws Exception {
            Lead lead = lead("ABERTA");
            String chave = chave();
            assertThat(reservar(lead.id(), "FIDELIZACAO", "r", "2026-10", chave).getStatusCode()).isEqualTo(HttpStatus.CREATED);

            assertThat(pendentes()).contains(chave);
            assertThat(corpo(reservar(lead.id(), "FIDELIZACAO", "r", "2026-10", chave), HttpStatus.OK).path("motivo").asText())
                    .isEqualTo("CHAVE_JA_USADA");
            assertThat(mensagensDe(lead)).isZero();
        }

        @Test
        @DisplayName("mesma chave com payload diferente 409; mesma ocorrencia com outra chave nao envia; execucaoId nao conta")
        void chaveEOcorrencia() throws Exception {
            Lead lead = lead("PAYLOAD");
            String chave = chave();
            assertThat(reservarComExecucao(lead.id(), "FOLLOW_UP", "r1", "o1", chave, "exec-1").getStatusCode())
                    .isEqualTo(HttpStatus.CREATED);

            assertThat(reservar(lead.id(), "FOLLOW_UP", "r1", "o2", chave).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(reservar(lead("PAYLOAD-OUTRO").id(), "FOLLOW_UP", "r1", "o1", chave).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(corpo(reservar(lead.id(), "FOLLOW_UP", "r1", "o1", chave()), HttpStatus.OK).path("motivo").asText())
                    .isEqualTo("OCORRENCIA_JA_REGISTRADA");
            assertThat(corpo(reservarComExecucao(lead.id(), "FOLLOW_UP", "r1", "o1", chave, "exec-2"), HttpStatus.OK)
                            .path("motivo").asText())
                    .isEqualTo("CHAVE_JA_USADA");
            assertThat(reservasDo(lead)).isOne();
        }

        @Test
        @DisplayName("negativos: lead inexistente 404, tipo ou ocorrencia invalidos 400, sem token 401, chave de outro lead 409")
        void negativos() throws Exception {
            Lead lead = lead("NEGATIVOS");
            assertThat(reservar(UUID.randomUUID(), "FOLLOW_UP", "r", "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(reservar(lead.id(), "PROGRAMADA", "r", "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reservar(lead.id(), "XYZ", "r", "o1", chave()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reservar(lead.id(), "FOLLOW_UP", "r", null, chave()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(reservar(lead.id(), "FOLLOW_UP", "r", "o1", "x".repeat(201)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            HttpHeaders semToken = new HttpHeaders();
            semToken.setContentType(MediaType.APPLICATION_JSON);
            assertThat(http.exchange(
                                    "/internal/v1/leads/" + lead.id() + "/envios-proativos/reservas",
                                    HttpMethod.POST,
                                    new HttpEntity<>("{}", semToken),
                                    String.class)
                            .getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            String chave = chave();
            assertThat(reservar(lead.id(), "FOLLOW_UP", "r", "o1", chave).getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Lead outro = lead("NEGATIVOS-OUTRO");
            assertThat(registrar(outro.atendimento(), "wamid.e219-outro", chave, Map.of()).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(mensagensDe(outro)).isZero();
        }
    }

    // ---------------------------------------------------------------- apoio

    private record Lead(UUID id, UUID atendimento) {}

    private Lead lead(String marcador) {
        UUID lead = UUID.randomUUID();
        String telefone = TELEFONE + String.format("%05d", ++sequencia);
        jdbc.update(
                "INSERT INTO lead (id, nome, telefone, status_basico, ultima_interacao_em, ultima_mensagem_do_lead_em)"
                        + " VALUES (?, ?, ?, 'IA', now(), now())",
                lead,
                PREFIXO + marcador,
                telefone);
        UUID canal = UUID.randomUUID();
        UUID credencial = UUID.randomUUID();
        jdbc.update("INSERT INTO canal (id, nome, tipo) VALUES (?, ?, 'WHATSAPP')", canal, PREFIXO + marcador);
        jdbc.update(
                "INSERT INTO canal_credencial (id, canal_id, numero, identificador_externo, token_ref, ativo)"
                        + " VALUES (?, ?, ?, ?, 'secret://e219', TRUE)",
                credencial,
                canal,
                telefone,
                telefone);
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id, lead_id, canal_credencial_id, status, iniciado_em)"
                        + " VALUES (?, ?, ?, 'EM_IA', now())",
                atendimento,
                lead,
                credencial);
        return new Lead(lead, atendimento);
    }

    private void configurar(String chave, String valor) {
        jdbc.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", valor, chave);
        redis.delete(ChavesDeCacheConfiguracaoAutomacao.porChave(chave));
    }

    private static Map<String, Object> origem(String tipo, String regra, String execucao) {
        Map<String, Object> campos = new java.util.HashMap<>();
        campos.put("origemTipo", tipo);
        campos.put("origemRegraId", regra);
        campos.put("origemExecucaoId", execucao);
        return campos;
    }

    private ResponseEntity<String> registrar(UUID atendimento, String wamid, String chaveDeEnvio, Map<String, Object> origem)
            throws Exception {
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("wamid", wamid);
        corpo.put("tipo", "TEXTO");
        corpo.put("conteudo", "Mensagem automatica de teste");
        if (chaveDeEnvio != null) {
            corpo.put("chaveDeEnvio", chaveDeEnvio);
        }
        if (origem != null) {
            corpo.putAll(origem);
        }
        return chamar(
                HttpMethod.POST,
                "/internal/v1/atendimentos/" + atendimento + "/mensagens-enviadas",
                json.writeValueAsString(corpo));
    }

    private ResponseEntity<String> responder(UUID atendimento, String chaveIdempotencia, Map<String, Object> origem)
            throws Exception {
        Map<String, Object> corpo = new java.util.HashMap<>(origem);
        corpo.put("conteudo", "Resposta da IA " + chaveIdempotencia);
        HttpHeaders headers = cabecalhos();
        headers.set("Idempotency-Key", chaveIdempotencia);
        return http.exchange(
                "/internal/v1/atendimentos/" + atendimento + "/responder",
                HttpMethod.POST,
                new HttpEntity<>(json.writeValueAsString(corpo), headers),
                String.class);
    }

    private ResponseEntity<String> reservar(UUID lead, String tipo, String regra, String ocorrencia, String chave) {
        return reservarComExecucao(lead, tipo, regra, ocorrencia, chave, null);
    }

    private ResponseEntity<String> reservarComExecucao(
            UUID lead, String tipo, String regra, String ocorrencia, String chave, String execucao) {
        Map<String, Object> corpo = new java.util.HashMap<>();
        corpo.put("tipo", tipo);
        corpo.put("regraId", regra);
        corpo.put("ocorrencia", ocorrencia);
        corpo.put("chave", chave);
        corpo.put("execucaoId", execucao);
        try {
            return chamar(
                    HttpMethod.POST, "/internal/v1/leads/" + lead + "/envios-proativos/reservas", json.writeValueAsString(corpo));
        } catch (com.fasterxml.jackson.core.JsonProcessingException erro) {
            throw new IllegalStateException(erro);
        }
    }

    private String pendentes() {
        ResponseEntity<String> resposta = chamar(
                HttpMethod.GET, "/internal/v1/envios-proativos/pendentes?reservadosAntesDe=" + Instant.now().plusSeconds(60), null);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resposta.getBody();
    }

    private ResponseEntity<String> chamar(HttpMethod metodo, String url, String corpo) {
        return http.exchange(url, metodo, new HttpEntity<>(corpo, cabecalhos()), String.class);
    }

    private static HttpHeaders cabecalhos() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private JsonNode corpo(ResponseEntity<String> resposta, HttpStatus esperado) throws Exception {
        assertThat(resposta.getStatusCode()).as(resposta.getBody()).isEqualTo(esperado);
        return json.readTree(resposta.getBody());
    }

    private String mensagemId(ResponseEntity<String> resposta) throws Exception {
        return corpo(resposta, HttpStatus.OK).path("mensagemId").asText();
    }

    private static String chave() {
        return "proativa:" + UUID.randomUUID();
    }

    private Map<String, Object> origemDa(String mensagemId) {
        return jdbc.queryForMap(
                "SELECT tipo, regra_id, execucao_id, lead_id FROM mensagem_origem_automacao WHERE mensagem_id = ?::uuid",
                mensagemId);
    }

    private long reservasDo(Lead lead) {
        return jdbc.queryForObject("SELECT count(*) FROM envio_proativo_reserva WHERE lead_id = ?", Long.class, lead.id());
    }

    private long mensagensDe(Lead lead) {
        return jdbc.queryForObject("SELECT count(*) FROM mensagem WHERE atendimento_id = ?", Long.class, lead.atendimento());
    }

    private void plantarOrigem(Lead lead, String tipo, java.time.LocalDateTime local) {
        jdbc.update(
                "INSERT INTO mensagem_origem_automacao (mensagem_id, atendimento_id, lead_id, enviado_em, tipo)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                lead.atendimento(),
                lead.id(),
                Timestamp.from(local.atZone(fuso).toInstant()),
                tipo);
    }

    private void plantarMensagemDaIaSemOrigem(Lead lead, java.time.LocalDateTime local) {
        jdbc.update(
                "INSERT INTO mensagem (id, atendimento_id, remetente_tipo, tipo, conteudo, enviado_em)"
                        + " VALUES (?, ?, 'IA', 'TEXTO', 'historico', ?)",
                UUID.randomUUID(),
                lead.atendimento(),
                Timestamp.from(local.atZone(fuso).toInstant()));
    }

    private static List<String> totais(JsonNode corpo) {
        List<String> linhas = new ArrayList<>();
        corpo.path("linhas").forEach(linha -> linhas.add(linha.path("dia").asText() + " " + linha.path("origem").asText()
                + " " + linha.path("mensagens").asLong() + " " + linha.path("leads").asLong()));
        return linhas;
    }

    private interface Chamada {
        ResponseEntity<String> executar(int indice) throws Exception;
    }

    private static List<HttpStatus> emParalelo(int concorrentes, Chamada chamada) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService threads = Executors.newFixedThreadPool(concorrentes);
        try {
            List<Future<HttpStatus>> resultados = new ArrayList<>();
            for (int i = 0; i < concorrentes; i++) {
                int indice = i;
                Callable<HttpStatus> tarefa = () -> {
                    largada.await();
                    return HttpStatus.valueOf(chamada.executar(indice).getStatusCode().value());
                };
                resultados.add(threads.submit(tarefa));
            }
            largada.countDown();
            List<HttpStatus> status = new ArrayList<>();
            for (Future<HttpStatus> resultado : resultados) {
                status.add(resultado.get(30, TimeUnit.SECONDS));
            }
            return status;
        } finally {
            threads.shutdownNow();
        }
    }
}
