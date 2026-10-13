package com.synapse.crm.app.atendimento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio;

/** HTTP + scheduler real + PostgreSQL: nenhum teste valida só o toast ou uma entidade em memória. */
class FinalizacaoEmMassaRegressaoIT extends FinalizacaoEmMassaITBase {

    @MockitoSpyBean FinalizacaoEmMassaRepositorio repositorio;

    @AfterEach
    void restaurarPorta() {
        reset(repositorio);
    }

    @Test
    void cinquentaETresElegiveisSaoFinalizadosMesmoSemEstaremNaPaginaDaInbox() {
        UUID dono = atendente("RegressaoPagina");
        UUID outro = atendente("RegressaoFora");
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 53; i++) {
            ids.add(atendimentoAberto(dono, noDia(hoje(), LocalTime.of(8, i))));
        }
        UUID foraDoDono = atendimentoAberto(outro, noDia(hoje(), LocalTime.of(8, 0)));
        UUID foraDoPeriodo = atendimentoAberto(dono, noDia(hoje().minusDays(1), LocalTime.of(8, 0)));
        definirParametro("atendimento.finalizacao_em_massa.lote", 7);
        String token = tokenDe(dono);
        long ativosAntes = ler(chamar(token, HttpMethod.GET, "/api/v1/atendimentos/contagem", null, null))
                .path("ATIVOS").asLong();
        var filtro = pedido(List.of(dono), hoje(), hoje());
        assertThat(ler(previa(token, filtro)).path("total").asInt()).isEqualTo(53);
        UUID operacao = idDaOperacao(criar(token, filtro, chaveNova()));
        agendador.processarOperacao();
        assertThat(itensComStatus(operacao, "PENDENTE")).isEqualTo(46);
        processarAteConcluir(operacao);

        assertThat(ids).allSatisfy(id -> assertThat(statusDoAtendimento(id)).isEqualTo("FINALIZADO"));
        assertThat(statusDoAtendimento(foraDoDono)).isEqualTo("EM_ATENDIMENTO");
        assertThat(statusDoAtendimento(foraDoPeriodo)).isEqualTo("EM_ATENDIMENTO");
        assertThat(ler(consultar(token, operacao)).path("finalizados").asInt()).isEqualTo(53);
        assertThat(ler(chamar(token, HttpMethod.GET, BASE + "/" + operacao + "/itens?pagina=0&tamanho=50", null, null))
                .path("itens")).hasSize(50);
        assertThat(ler(chamar(token, HttpMethod.GET, BASE + "/" + operacao + "/itens?pagina=1&tamanho=50", null, null))
                .path("itens")).hasSize(3);
        assertThat(ler(chamar(token, HttpMethod.GET, "/api/v1/atendimentos/contagem", null, null))
                .path("ATIVOS").asLong()).isEqualTo(ativosAntes - 53);
        for (UUID id : ids) {
            assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id = ?", UUID.class, id)).isEqualTo(dono);
            assertThat(jdbc.queryForObject("SELECT l.atendente_responsavel_id FROM lead l JOIN atendimento a ON a.lead_id=l.id WHERE a.id=?",
                    UUID.class, id)).isNull();
        }
    }

    @Test
    void negociacaoSemResultadoPermaneceAbertaComMotivoExplicitoMasResultadoAnteriorPermiteFinalizar() {
        UUID dono = atendente("RegressaoNegociacao");
        UUID pendente = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 0)));
        UUID resolvido = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 1)));
        UUID normal = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 2)));
        jdbc.update("UPDATE atendimento SET em_negociacao=TRUE WHERE id IN (?, ?)", pendente, resolvido);
        String token = tokenDe(dono);
        assertThat(chamar(token, HttpMethod.PUT, "/api/v1/atendimentos/" + resolvido + "/resultado-venda",
                Map.of("resultado", "VENDEU"), null).getStatusCode()).isEqualTo(HttpStatus.OK);
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(dono), hoje(), hoje()), chaveNova()));
        processarAteConcluir(operacao);

        assertThat(statusDoAtendimento(pendente)).isEqualTo("EM_ATENDIMENTO");
        assertThat(statusDoAtendimento(resolvido)).isEqualTo("FINALIZADO");
        assertThat(statusDoAtendimento(normal)).isEqualTo("FINALIZADO");
        assertThat(jdbc.queryForObject("SELECT resultado_venda FROM atendimento WHERE id=?", String.class, pendente)).isNull();
        assertThat(jdbc.queryForObject("SELECT resultado_venda FROM atendimento WHERE id=?", String.class, resolvido)).isEqualTo("VENDEU");
        var itens = ler(chamar(token, HttpMethod.GET, BASE + "/" + operacao + "/itens?status=IGNORADO", null, null));
        assertThat(itens.path("itens").get(0).path("motivo").asText()).isEqualTo("RESULTADO_VENDA_PENDENTE");
        assertThat(ler(consultar(token, operacao)).path("ignorados").asInt()).isEqualTo(1);
        assertThat(ler(consultar(token, operacao)).path("falhas").asInt()).isZero();
        assertThat(chamar(token, HttpMethod.POST, "/api/v1/atendimentos/" + pendente + "/finalizar", null, null)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void recorteQueCresceAposContagemNaoPodeSerTruncadoSilenciosamente() {
        UUID dono = atendente("RegressaoLimite");
        UUID primeiro = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 0)));
        definirParametro("atendimento.finalizacao_em_massa.limite_por_operacao", 1);
        doAnswer(inv -> {
            Object contagem = inv.callRealMethod();
            atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 1)));
            return contagem;
        }).when(repositorio).contarElegiveis(any());
        var resposta = criar(tokenDe(dono), pedido(List.of(dono), hoje(), hoje()), chaveNova());
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(resposta)).isEqualTo("LIMITE_EXCEDIDO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finalizacao_em_massa", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finalizacao_em_massa_item", Integer.class)).isZero();
        assertThat(statusDoAtendimento(primeiro)).isEqualTo("EM_ATENDIMENTO");
    }

    @Test
    void recorteQueEsvaziaAposContagemNaoCriaSucessoComZeroItens() {
        UUID dono = atendente("RegressaoEsvazia");
        UUID id = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 0)));
        doAnswer(inv -> {
            Object contagem = inv.callRealMethod();
            jdbc.update("UPDATE atendimento SET status='FINALIZADO', finalizado_em=now() WHERE id=?", id);
            return contagem;
        }).when(repositorio).contarElegiveis(any());
        var resposta = criar(tokenDe(dono), pedido(List.of(dono), hoje(), hoje()), chaveNova());
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(codigo(resposta)).isEqualTo("SEM_ATENDIMENTOS");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM finalizacao_em_massa", Integer.class)).isZero();
    }

    @Test
    void finalizacaoIndividualConcorrenteNaoDuplicaEventosDoMesmoAtendimento() throws Exception {
        UUID dono = atendente("RegressaoIndividual");
        UUID id = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenDe(dono);
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(dono), hoje(), hoje()), chaveNova()));
        var largada = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var worker = pool.submit(() -> { largada.await(); agendador.processarOperacao(); return true; });
            var individual = pool.submit(() -> { largada.await(); return chamar(token, HttpMethod.POST,
                    "/api/v1/atendimentos/" + id + "/finalizar", Map.of(), null); });
            largada.countDown();
            worker.get(20, TimeUnit.SECONDS);
            assertThat(individual.get(20, TimeUnit.SECONDS).getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
        }
        processarAteConcluir(operacao);
        assertThat(statusDoAtendimento(id)).isEqualTo("FINALIZADO");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE atendimento_id=? AND tipo='ATENDIMENTO_FINALIZADO'",
                        Integer.class, id)).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_timeline WHERE atendimento_id=? AND tipo='ATENDIMENTO_FINALIZADO'",
                Integer.class, id)).isEqualTo(1);
        agendador.processarOperacao();
        assertThat(itensComStatus(operacao, "PENDENTE")).isZero();
    }

    @Test
    void transferenciaConcorrenteNaoFinalizaDepoisDeMudarODestinatario() throws Exception {
        UUID dono = atendente("RegressaoTransferir");
        UUID destino = atendente("RegressaoDestino");
        UUID id = atendimentoAberto(dono, noDia(hoje(), LocalTime.of(10, 0)));
        String token = tokenGestor();
        UUID operacao = idDaOperacao(criar(token, pedido(List.of(dono), hoje(), hoje()), chaveNova()));
        var largada = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var worker = pool.submit(() -> { largada.await(); agendador.processarOperacao(); return true; });
            var transferir = pool.submit(() -> { largada.await(); return chamar(token, HttpMethod.POST,
                    "/api/v1/atendimentos/" + id + "/transferir", Map.of("paraAtendenteId", destino), null); });
            largada.countDown();
            worker.get(20, TimeUnit.SECONDS);
            var resposta = transferir.get(20, TimeUnit.SECONDS);
            assertThat(resposta.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CONFLICT);
            processarAteConcluir(operacao);
            if (resposta.getStatusCode() == HttpStatus.OK) {
                assertThat(statusDoAtendimento(id)).isEqualTo("EM_ATENDIMENTO");
                assertThat(itensComStatus(operacao, "IGNORADO")).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT atendente_id FROM atendimento WHERE id=?", UUID.class, id)).isEqualTo(destino);
            } else {
                assertThat(statusDoAtendimento(id)).isEqualTo("FINALIZADO");
                assertThat(itensComStatus(operacao, "FINALIZADO")).isEqualTo(1);
            }
        }
    }
}
