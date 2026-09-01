package com.synapse.crm.app.equipe;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** Critério HTTP único usado pela listagem de disponíveis e pela distribuição automática. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestPropertySource(properties = "synapse.seguranca.token-interno=e37-rodizio-token")
class CriterioDeRodizioAutomacaoIT extends PostgresIT {

    private static final String TOKEN = "e37-rodizio-token";
    private static final String PREFIXO = "E37-RODIZIO-";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private final AtomicInteger sequencia = new AtomicInteger();

    @BeforeEach
    void preparar() {
        // Os atendentes do seed não fazem parte do cenário e permanecem fora do rodízio.
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM evento_timeline WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM mensagem WHERE atendimento_id IN (SELECT id FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?))", PREFIXO + "%");
        jdbc.update("DELETE FROM atendimento WHERE lead_id IN (SELECT id FROM lead WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM lead WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM disponibilidade_atendente_ia WHERE atendente_id IN (SELECT id FROM usuario WHERE nome LIKE ?)", PREFIXO + "%");
        jdbc.update("DELETE FROM usuario WHERE nome LIKE ?", PREFIXO + "%");
        jdbc.update("UPDATE usuario SET status_presenca = 'OFFLINE' WHERE email IN ('ana@dev.local', 'bruno@dev.local')");
    }

    @Test
    void menorCargaVemPrimeiro() {
        UUID tres = criarAtendente("TRES");
        UUID um = criarAtendente("UM");
        UUID cinco = criarAtendente("CINCO");
        criarAbertos("tres", tres, 3);
        criarAbertos("um", um, 1);
        criarAbertos("cinco", cinco, 5);

        assertThat(disponiveis()).startsWith(um);
    }

    @Test
    void empateDeCargaUsaQuemRecebeuHaMaisTempo() {
        UUID antigo = criarAtendente("ANTIGO");
        UUID novo = criarAtendente("NOVO");
        Instant agora = Instant.now();
        criarAtendimento("antigo", "FINALIZADO", antigo, agora.minus(2, ChronoUnit.DAYS));
        criarAtendimento("novo", "FINALIZADO", novo, agora.minus(1, ChronoUnit.HOURS));

        assertThat(disponiveis()).startsWith(antigo);
    }

    @Test
    void quemNuncaRecebeuVemAntesDeQuemJaRecebeu() {
        UUID jaRecebeu = criarAtendente("JA-RECEBEU");
        UUID nuncaRecebeu = criarAtendente("NUNCA-RECEBEU");
        criarAtendimento("historico", "FINALIZADO", jaRecebeu, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(disponiveis()).startsWith(nuncaRecebeu);
    }

    @Test
    void empateTotalUsaIdDeFormaDeterministica() {
        UUID primeiro = criarAtendente("EMPATE-A");
        UUID segundo = criarAtendente("EMPATE-B");

        List<UUID> primeiraLeitura = disponiveis();
        List<UUID> segundaLeitura = disponiveis();

        List<UUID> esperada = jdbc.query(
                "SELECT id FROM usuario WHERE id IN (?, ?) ORDER BY id",
                (rs, rowNum) -> UUID.fromString(rs.getString("id")),
                primeiro,
                segundo);
        assertThat(primeiraLeitura).containsExactlyElementsOf(esperada);
        assertThat(segundaLeitura).containsExactlyElementsOf(esperada);
    }

    @Test
    void conjuntoDeElegiveisNaoMudaApenasAOrdem() {
        UUID primeiro = criarAtendente("CONJUNTO-A");
        UUID segundo = criarAtendente("CONJUNTO-B");
        UUID terceiro = criarAtendente("CONJUNTO-C");
        criarAbertos("conjunto-a", primeiro, 2);
        criarAbertos("conjunto-b", segundo, 1);
        criarAbertos("conjunto-c", terceiro, 3);

        List<UUID> ids = disponiveis();

        assertThat(ids).containsExactlyInAnyOrder(primeiro, segundo, terceiro);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void transferenciaEscolheOMesmoAtendenteDoTopoDaLista() {
        UUID cheio = criarAtendente("TRANSFER-CHEIO");
        UUID livre = criarAtendente("TRANSFER-LIVRE");
        criarAbertos("transfer-cheio", cheio, 2);
        UUID atendimento = criarAtendimento("transferencia", "EM_IA", null, Instant.now());

        UUID topo = disponiveis().get(0);
        ResponseEntity<String> resposta = transferirProximo(atendimento, "transferencia-1");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimento)).isEqualTo(topo);
        assertThat(topo).isEqualTo(livre);
    }

    @Test
    void duasTransferenciasSeguidasDistribuemEntreAtendentes() {
        UUID primeiro = criarAtendente("SEQUENCIAL-A");
        UUID segundo = criarAtendente("SEQUENCIAL-B");
        UUID atendimentoUm = criarAtendimento("sequencial-um", "EM_IA", null, Instant.now());
        UUID atendimentoDois = criarAtendimento("sequencial-dois", "EM_IA", null, Instant.now());

        ResponseEntity<String> primeira = transferirProximo(atendimentoUm, "sequencial-1");
        ResponseEntity<String> segunda = transferirProximo(atendimentoDois, "sequencial-2");

        assertThat(primeira.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dono(atendimentoUm)).isNotEqualTo(dono(atendimentoDois));
        assertThat(List.of(dono(atendimentoUm), dono(atendimentoDois)))
                .containsExactlyInAnyOrder(primeiro, segundo);
    }

    @Test
    void semElegivelMantem409SemAlterarAtendimento() {
        UUID atendente = criarAtendente("SEM-ELEGIVEL");
        UUID atendimento = criarAtendimento("sem-elegivel", "EM_IA", null, Instant.now());
        jdbc.update("UPDATE disponibilidade_atendente_ia SET disponivel_para_ia = FALSE WHERE atendente_id = ?", atendente);

        ResponseEntity<String> resposta = transferirProximo(atendimento, "sem-elegivel-1");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dono(atendimento)).isNull();
        assertThat(resposta.getBody()).contains("Operacao nao pode ser aplicada");
    }

    private List<UUID> disponiveis() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        ResponseEntity<String> resposta = http.exchange(
                "/internal/v1/atendentes/disponiveis", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            List<UUID> ids = new ArrayList<>();
            for (JsonNode item : json.readTree(resposta.getBody())) {
                ids.add(UUID.fromString(item.get("usuarioId").asText()));
            }
            return ids;
        } catch (Exception erro) {
            throw new AssertionError("resposta de disponíveis inválida", erro);
        }
    }

    private ResponseEntity<String> transferirProximo(UUID atendimento, String chave) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Synapse-Token", TOKEN);
        headers.set("Idempotency-Key", chave);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
                "/internal/v1/atendimentos/" + atendimento + "/transferir-proximo-humano",
                HttpMethod.POST,
                new HttpEntity<>(null, headers),
                String.class);
    }

    private UUID criarAtendente(String marcador) {
        UUID id = UUID.randomUUID();
        String senha = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE papel = 'GESTOR' LIMIT 1", String.class);
        jdbc.update(
                "INSERT INTO usuario (id,nome,email,senha_hash,papel,status_presenca,ativo) VALUES (?,?,?,?,'ATENDENTE','ONLINE',TRUE)",
                id, PREFIXO + marcador, id + "@e37.invalid", senha);
        jdbc.update("INSERT INTO disponibilidade_atendente_ia (atendente_id,disponivel_para_ia) VALUES (?,TRUE)", id);
        return id;
    }

    private void criarAbertos(String marcador, UUID dono, int quantidade) {
        for (int indice = 0; indice < quantidade; indice++) {
            criarAtendimento(marcador + "-aberto-" + indice, "EM_ATENDIMENTO", dono, Instant.now());
        }
    }

    private UUID criarAtendimento(String marcador, String status, UUID dono, Instant iniciadoEm) {
        UUID lead = UUID.randomUUID();
        String telefone = "55619037" + String.format("%06d", sequencia.incrementAndGet());
        jdbc.update(
                "INSERT INTO lead (id,nome,telefone,atendente_responsavel_id,status_basico,ultima_interacao_em,ultima_mensagem_do_lead_em) VALUES (?,?,?,?::uuid,?::status_basico_lead,?,?)",
                lead, PREFIXO + marcador, telefone, dono, dono == null ? "IA" : "EM_ATENDIMENTO",
                Timestamp.from(iniciadoEm), Timestamp.from(iniciadoEm));
        UUID atendimento = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO atendimento (id,lead_id,atendente_id,status,iniciado_em) VALUES (?,?,?,?::status_atendimento,?)",
                atendimento, lead, dono, status, Timestamp.from(iniciadoEm));
        return atendimento;
    }

    private UUID dono(UUID atendimento) {
        return jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, atendimento);
    }
}
