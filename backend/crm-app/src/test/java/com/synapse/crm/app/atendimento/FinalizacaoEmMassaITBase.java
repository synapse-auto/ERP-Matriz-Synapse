package com.synapse.crm.app.atendimento;

import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_ANA;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.EMAIL_GESTOR;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_ATENDENTE;
import static com.synapse.crm.app.seguranca.ApoioAutenticacao.SENHA_GESTOR;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.synapse.crm.app.PostgresIT;
import com.synapse.crm.app.seguranca.ApoioAutenticacao;

/**
 * Base dos ITs da finalizacao em massa. O agendamento fica desligado pelo {@link PostgresIT}; os ciclos do worker e
 * dos avisos sao chamados a mao, pelos mesmos metodos publicos que o {@code @Scheduled} chama em producao. Assim cada
 * teste decide o instante exato em que o worker roda (antes ou depois de alterar um atendimento) e nao ha espera por
 * tempo, so por condicao.
 *
 * <p>Todo dado de teste tem o prefixo {@code FMASSA-}: o banco de teste ja traz atendimentos do seed, e um filtro de
 * teste so alcanca atendentes criados aqui.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
abstract class FinalizacaoEmMassaITBase extends PostgresIT {

    protected static final String PREFIXO = "FMASSA-";
    protected static final String BASE = "/api/v1/atendimentos/finalizacoes-em-massa";

    @Autowired protected TestRestTemplate http;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    @Autowired protected ZoneId fuso;
    @Autowired protected AgendadorDeFinalizacaoEmMassa agendador;

    private final List<UUID> usuariosCriados = new ArrayList<>();

    @AfterEach
    void limparFinalizacaoEmMassa() {
        jdbc.update("DELETE FROM finalizacao_em_massa");
        // O ator das linhas de auditoria (login, finalizacao) e um usuario de teste: sem apagar a trilha antes, a FK
        // impede apagar o usuario e o teste seguinte quebra por e-mail duplicado.
        jdbc.update(
                "DELETE FROM audit_log WHERE entidade_tipo = 'FINALIZACAO_EM_MASSA'"
                        + " OR ator_id IN (SELECT id FROM usuario WHERE nome LIKE ?)"
                        + " OR lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%",
                PREFIXO + "%");
        jdbc.update("DELETE FROM permissao_perfil_item");
        jdbc.update("DELETE FROM permissao_usuario_excecao");
        // Finalizar enfileira eventos na outbox; sem o consumidor ligado eles ficam pendentes e envelhecem, o que
        // degrada a saude critica (acumulo-outbox) de qualquer teste que rode depois.
        jdbc.update(
                "DELETE FROM outbox_evento WHERE payload->>'leadId' IN (SELECT id::text FROM lead WHERE nome LIKE ?)"
                        + " OR payload->>'lead_id' IN (SELECT id::text FROM lead WHERE nome LIKE ?)",
                PREFIXO + "%",
                PREFIXO + "%");
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update(
                "DELETE FROM mensagem WHERE atendimento_id IN (SELECT a.id FROM atendimento a"
                        + " JOIN lead l ON l.id = a.lead_id WHERE l.nome LIKE ?)",
                PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
        usuariosCriados.clear();
        restaurarParametros();
    }

    // --- cenario --------------------------------------------------------------------------------------

    protected UUID atendente(String nome) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, EMAIL_ANA);
        jdbc.update(
                // senha_alterada_em preenchida: sem ela a senha e provisoria e toda rota responde 403.
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo,senha_alterada_em)"
                        + " VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE,now())",
                id,
                PREFIXO + nome,
                "fmassa-" + nome.toLowerCase(java.util.Locale.ROOT) + "@dev.invalid",
                senha);
        usuariosCriados.add(id);
        return id;
    }

    protected String emailDe(UUID usuario) {
        return jdbc.queryForObject("SELECT email FROM usuario WHERE id = ?", String.class, usuario);
    }

    protected UUID usuario(String email) {
        return jdbc.queryForObject("SELECT id FROM usuario WHERE email = ?", UUID.class, email);
    }

    /** Atendimento EM_ATENDIMENTO do atendente, aberto no instante informado e sem mensagem (ultima atividade = abertura). */
    protected UUID atendimentoAberto(UUID atendente, Instant abertoEm) {
        return atendimento(atendente, "EM_ATENDIMENTO", abertoEm);
    }

    protected UUID atendimento(UUID atendente, String status, Instant abertoEm) {
        UUID lead = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO lead(id,nome,status_basico,atendente_responsavel_id) VALUES (?, ?, ?::status_basico_lead, ?)",
                lead,
                PREFIXO + "lead-" + lead.toString().substring(0, 8),
                statusDoLeadPara(status),
                "EM_ATENDIMENTO".equals(status) ? atendente : null);
        UUID id = UUID.randomUUID();
        UUID canal = jdbc.queryForObject("SELECT id FROM canal ORDER BY id LIMIT 1", UUID.class);
        jdbc.update(
                "INSERT INTO atendimento(id,lead_id,canal_id,atendente_id,status,iniciado_em,finalizado_em)"
                        + " VALUES (?, ?, ?, ?, ?::status_atendimento, ?, ?)",
                id,
                lead,
                canal,
                atendente,
                status,
                Timestamp.from(abertoEm),
                "FINALIZADO".equals(status) ? Timestamp.from(abertoEm) : null);
        return id;
    }

    private static String statusDoLeadPara(String statusDoAtendimento) {
        return switch (statusDoAtendimento) {
            case "EM_ATENDIMENTO" -> "EM_ATENDIMENTO";
            case "FINALIZADO" -> "FINALIZADO";
            default -> "IA";
        };
    }

    protected void mensagem(UUID atendimento, UUID remetente, Instant quando) {
        jdbc.update(
                "INSERT INTO mensagem(id,atendimento_id,remetente_tipo,remetente_id,tipo,conteudo,enviado_em)"
                        + " VALUES (?, ?, 'ATENDENTE'::remetente_tipo, ?, 'TEXTO', 'oi', ?)",
                UUID.randomUUID(),
                atendimento,
                remetente,
                Timestamp.from(quando));
    }

    protected String statusDoAtendimento(UUID atendimento) {
        return jdbc.queryForObject("SELECT status FROM atendimento WHERE id = ?", String.class, atendimento);
    }

    protected Instant noDia(LocalDate dia, LocalTime hora) {
        return dia.atTime(hora).atZone(fuso).toInstant();
    }

    protected LocalDate hoje() {
        return LocalDate.now(fuso);
    }

    // --- parametros -------------------------------------------------------------------------------------

    protected void definirParametro(String chave, int valor) {
        jdbc.update("UPDATE configuracao_automacao SET valor = ? WHERE chave = ?", String.valueOf(valor), chave);
    }

    private void restaurarParametros() {
        definirParametro("atendimento.finalizacao_em_massa.limite_por_operacao", 5000);
        definirParametro("atendimento.finalizacao_em_massa.periodo_maximo_dias", 31);
        definirParametro("atendimento.finalizacao_em_massa.lote", 50);
    }

    // --- HTTP -------------------------------------------------------------------------------------------

    protected String token(String email, String senha) {
        return ApoioAutenticacao.login(http, email, senha).accessToken();
    }

    protected String tokenGestor() {
        return token(EMAIL_GESTOR, SENHA_GESTOR);
    }

    protected String tokenDe(UUID atendente) {
        return token(emailDe(atendente), SENHA_ATENDENTE);
    }

    protected static Map<String, Object> pedido(
            List<UUID> atendentes, LocalDate de, LocalDate ate, LocalTime horaInicio, LocalTime horaFim) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("atendenteIds", atendentes);
        corpo.put("de", de == null ? null : de.toString());
        corpo.put("ate", ate == null ? null : ate.toString());
        corpo.put("horaInicio", horaInicio == null ? null : horaInicio.toString());
        corpo.put("horaFim", horaFim == null ? null : horaFim.toString());
        return corpo;
    }

    protected static Map<String, Object> pedido(List<UUID> atendentes, LocalDate de, LocalDate ate) {
        return pedido(atendentes, de, ate, null, null);
    }

    protected ResponseEntity<String> chamar(String token, HttpMethod metodo, String rota, Object corpo, String chave) {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setBearerAuth(token);
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        if (chave != null) {
            cabecalhos.set("Idempotency-Key", chave);
        }
        return http.exchange(rota, metodo, new HttpEntity<>(corpo, cabecalhos), String.class);
    }

    protected ResponseEntity<String> previa(String token, Object corpo) {
        return chamar(token, HttpMethod.POST, BASE + "/previa", corpo, null);
    }

    protected ResponseEntity<String> criar(String token, Object corpo, String chave) {
        return chamar(token, HttpMethod.POST, BASE, corpo, chave);
    }

    protected ResponseEntity<String> consultar(String token, UUID operacao) {
        return chamar(token, HttpMethod.GET, BASE + "/" + operacao, null, null);
    }

    protected JsonNode ler(ResponseEntity<String> resposta) {
        try {
            return json.readTree(resposta.getBody());
        } catch (Exception erro) {
            throw new AssertionError("corpo ilegivel: " + resposta.getBody(), erro);
        }
    }

    protected String codigo(ResponseEntity<String> resposta) {
        return ler(resposta).path("codigo").asText();
    }

    protected static String chaveNova() {
        return "chave-" + UUID.randomUUID();
    }

    // --- worker -----------------------------------------------------------------------------------------

    /** Roda o ciclo do worker (o mesmo metodo do {@code @Scheduled}) ate a operacao concluir. */
    protected void processarAteConcluir(UUID operacao) {
        for (int rodada = 0; rodada < 200; rodada++) {
            agendador.processarOperacao();
            if ("CONCLUIDA".equals(statusDaOperacao(operacao))) {
                return;
            }
        }
        throw new AssertionError("a operacao nao concluiu em 200 rodadas");
    }

    protected String statusDaOperacao(UUID operacao) {
        return jdbc.queryForObject("SELECT status FROM finalizacao_em_massa WHERE id = ?", String.class, operacao);
    }

    protected int itensComStatus(UUID operacao, String status) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM finalizacao_em_massa_item WHERE operacao_id = ? AND status = ?",
                Integer.class,
                operacao,
                status);
    }

    protected UUID idDaOperacao(ResponseEntity<String> resposta) {
        return UUID.fromString(ler(resposta).path("id").asText());
    }
}
