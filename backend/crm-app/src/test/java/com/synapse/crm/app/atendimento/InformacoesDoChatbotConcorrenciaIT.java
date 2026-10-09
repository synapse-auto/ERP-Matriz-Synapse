package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Validar o atendimento e registrar o card sao uma decisao so, tomada sob o lock do lead e do
 * atendimento (a mesma ordem de finalizar e transferir). Uma finalizacao ou devolucao para a IA
 * concorrente termina antes — e o card e recusado — ou depois — e o card ja esta gravado. Nunca no
 * meio, gravando num destino obsoleto.
 *
 * <p>Os tres primeiros testes sao deterministas: seguram o lock do lead como uma finalizacao em curso
 * e esperam, por condicao e nao por tempo, a requisicao bloquear nele.
 */
class InformacoesDoChatbotConcorrenciaIT extends InformacoesDoChatbotITBase {

    private static final String SQL_ESPERA_PELO_LOCK_DO_LEAD =
            "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                    + " AND wait_event_type = 'Lock' AND query ILIKE '%FROM lead WHERE id%FOR UPDATE%'";

    @Test
    @DisplayName("finalizacao em curso: o card espera o lock, ve o atendimento finalizado e e recusado sem gravar")
    void finalizacaoConcorrenteNaoGravaEmDestinoObsoleto() throws Exception {
        Atendimento atendimento = atendimentoComHumano("CONC-FINALIZA");

        ResponseEntity<String> resposta = comLockDoLeadSegurado(
                atendimento,
                "chave-conc-finaliza",
                conexao -> executar(
                        conexao,
                        "UPDATE atendimento SET status = 'FINALIZADO', finalizado_em = now() WHERE id = ?",
                        atendimento.id()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(resposta).path("motivo").asText()).isEqualTo("ATENDIMENTO_FINALIZADO");
        assertThat(cards(atendimento.id())).as("nada gravado em destino obsoleto").isZero();
        assertThat(captura.avisos).isEmpty();
    }

    @Test
    @DisplayName("devolucao para a IA em curso: o card espera o lock, ve o atendimento na IA e e recusado sem gravar")
    void devolucaoParaIaConcorrenteNaoGravaEmDestinoObsoleto() throws Exception {
        Atendimento atendimento = atendimentoComHumano("CONC-DEVOLVE");

        ResponseEntity<String> resposta = comLockDoLeadSegurado(
                atendimento,
                "chave-conc-devolve",
                conexao -> executar(
                        conexao,
                        "UPDATE atendimento SET status = 'EM_IA', atendente_id = NULL WHERE id = ?",
                        atendimento.id()));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ler(resposta).path("motivo").asText()).isEqualTo("ATENDIMENTO_NAO_TRANSFERIDO");
        assertThat(cards(atendimento.id())).isZero();
        assertThat(captura.avisos).isEmpty();
    }

    @Test
    @DisplayName("lock solto sem mudar o estado: a requisicao que esperava segue e grava (o bloqueio era o lock, nao um erro)")
    void lockSoltoSemMudancaGrava() throws Exception {
        Atendimento atendimento = atendimentoComHumano("CONC-NADA");

        ResponseEntity<String> resposta = comLockDoLeadSegurado(atendimento, "chave-conc-nada", conexao -> {});

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cards(atendimento.id())).isEqualTo(1);
        assertThat(captura.avisos).hasSize(1);
    }

    @Test
    @DisplayName("corrida real com finalizar e modo-ia: todo desfecho e um dos dois validos, sem 500 e sem card em destino obsoleto")
    void corridaRealTemSempreDesfechoValido() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int rodada = 0; rodada < 12; rodada++) {
                boolean finalizar = rodada % 2 == 0;
                Atendimento atendimento = atendimentoComHumano("CORRIDA-" + rodada);
                CountDownLatch largada = new CountDownLatch(1);

                Future<ResponseEntity<String>> card = pool.submit(() -> {
                    largada.await();
                    return postar(TOKEN, atendimento.id(), "chave-corrida-" + atendimento.id(), "informacoes");
                });
                Future<ResponseEntity<String>> mudanca = pool.submit(() -> {
                    largada.await();
                    return finalizar
                            ? chamarInterno(HttpMethod.POST, atendimento.id(), "/finalizar", "fim-" + atendimento.id())
                            : chamarInterno(HttpMethod.PATCH, atendimento.id(), "/modo-ia", "ia-" + atendimento.id());
                });
                largada.countDown();

                ResponseEntity<String> doCard = card.get(60, TimeUnit.SECONDS);
                ResponseEntity<String> daMudanca = mudanca.get(60, TimeUnit.SECONDS);

                assertThat(daMudanca.getStatusCode()).as("rodada %d: mudanca de estado", rodada).isEqualTo(HttpStatus.OK);
                if (doCard.getStatusCode() == HttpStatus.OK) {
                    assertThat(cards(atendimento.id())).as("rodada %d: card aceito esta gravado", rodada).isEqualTo(1);
                } else {
                    assertThat(doCard.getStatusCode()).as("rodada %d: unica recusa valida", rodada).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ler(doCard).path("motivo").asText())
                            .isEqualTo(finalizar ? "ATENDIMENTO_FINALIZADO" : "ATENDIMENTO_NAO_TRANSFERIDO");
                    assertThat(cards(atendimento.id())).as("rodada %d: card recusado nao deixa rastro", rodada).isZero();
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // --- apoio --------------------------------------------------------------------------------------

    /**
     * Segura o lock do lead como uma finalizacao/transferencia em curso, dispara o POST do card, espera
     * (por condicao) ele bloquear nesse lock, aplica {@code mudanca} na mesma transacao e a confirma.
     */
    private ResponseEntity<String> comLockDoLeadSegurado(Atendimento atendimento, String chave, Mudanca mudanca)
            throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection conexao = jdbc.getDataSource().getConnection()) {
            conexao.setAutoCommit(false);
            try (PreparedStatement trava = conexao.prepareStatement("SELECT id FROM lead WHERE id = ? FOR UPDATE")) {
                trava.setObject(1, atendimento.leadId());
                trava.executeQuery();
            }

            Future<ResponseEntity<String>> chamada = pool.submit(() -> postar(TOKEN, atendimento.id(), chave, "informacoes"));
            await().atMost(30, TimeUnit.SECONDS)
                    .until(() -> jdbc.queryForObject(SQL_ESPERA_PELO_LOCK_DO_LEAD, Long.class) > 0);
            assertThat(chamada.isDone()).as("a requisicao deveria estar esperando o lock do lead").isFalse();

            mudanca.aplicar(conexao);
            conexao.commit();
            return chamada.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void executar(Connection conexao, String sql, UUID id) throws Exception {
        try (PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setObject(1, id);
            comando.executeUpdate();
        }
    }

    private ResponseEntity<String> chamarInterno(HttpMethod metodo, UUID atendimentoId, String sufixo, String chave) {
        HttpHeaders cabecalhos = cabecalhos(TOKEN);
        cabecalhos.set("Idempotency-Key", chave);
        return http.exchange(
                "/internal/v1/atendimentos/" + atendimentoId + sufixo, metodo, new HttpEntity<>("{}", cabecalhos), String.class);
    }

    @FunctionalInterface
    private interface Mudanca {
        void aplicar(Connection conexao) throws Exception;
    }
}
