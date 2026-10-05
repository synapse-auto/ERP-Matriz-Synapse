package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.ItemPendente;
import com.synapse.crm.atendimento.application.finalizacaomassa.FinalizacaoEmMassaRepositorio.OperacaoDeFinalizacao;

/**
 * Uma rodada do worker: reivindica a operacao ativa (lease), processa um lote curto de itens, e conclui se nao
 * restou nenhum. Rodadas curtas devolvem o controle ao agendador e deixam o pool do chat livre; se o processo
 * morrer no meio, o lease expira e outra rodada retoma do proximo item pendente.
 */
@Service
public class ProcessarFinalizacaoEmMassaUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessarFinalizacaoEmMassaUseCase.class);
    /** Folga sobre uma rodada normal; vencido, outro no pode retomar a operacao. */
    static final Duration LEASE = Duration.ofMinutes(2);

    private final TransacoesDaFinalizacaoEmMassa transacoes;
    private final RegistradorDeConclusaoDaFinalizacaoEmMassa conclusao;
    private final ParametrosDeFinalizacaoEmMassa parametros;
    private final Clock relogio;

    public ProcessarFinalizacaoEmMassaUseCase(
            TransacoesDaFinalizacaoEmMassa transacoes,
            RegistradorDeConclusaoDaFinalizacaoEmMassa conclusao,
            ParametrosDeFinalizacaoEmMassa parametros,
            Clock relogio) {
        this.transacoes = transacoes;
        this.conclusao = conclusao;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    /** @return quantos itens foram tratados nesta rodada (0 = nada a fazer). */
    @PreAuthorize("hasRole('SERVICO')")
    public int executar() {
        Instant agora = Instant.now(relogio);
        OperacaoDeFinalizacao operacao = transacoes.reivindicar(agora, LEASE).orElse(null);
        if (operacao == null) {
            return 0;
        }
        List<ItemPendente> lote = transacoes.proximosPendentes(operacao.id(), parametros.tamanhoDoLote());
        for (ItemPendente item : lote) {
            tratar(operacao, item);
        }
        if (transacoes.concluirSeNaoHaPendentes(operacao.id(), Instant.now(relogio))) {
            conclusao.registrar(operacao.id());
            log.info("Finalizacao em massa concluida: operacaoId={}, itensNaRodadaFinal={}", operacao.id(), lote.size());
        } else {
            transacoes.liberarLease(operacao.id());
        }
        return lote.size();
    }

    private void tratar(OperacaoDeFinalizacao operacao, ItemPendente item) {
        try {
            transacoes.processarItem(operacao, item, Instant.now(relogio));
        } catch (RuntimeException erro) {
            log.warn(
                    "Falha ao finalizar atendimento em massa: operacaoId={}, atendimentoId={}, tipoErro={}",
                    operacao.id(), item.atendimentoId(), erro.getClass().getSimpleName());
            transacoes.registrarFalha(item, Instant.now(relogio));
        }
    }
}
