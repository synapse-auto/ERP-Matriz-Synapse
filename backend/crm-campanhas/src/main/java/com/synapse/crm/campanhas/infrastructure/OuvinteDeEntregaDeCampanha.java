package com.synapse.crm.campanhas.infrastructure;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.MudancaDeStatusDeEntrega;
import com.synapse.crm.campanhas.application.AplicarEntregaDeCampanhaUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Leva ao funil da campanha cada mudanca de status de entrega, DEPOIS do commit da mudanca da mensagem.
 *
 * <p>Depois do commit e em transacao propria, de proposito: o caminho de entrega de mensagens e o que a regra de
 * precedencia protege, e nada de campanha pode fazer a atualizacao de status de um atendimento falhar. O custo
 * e um evento perdido por queda do processo entre o commit e este ouvinte; a reconciliacao do ciclo cobre isso
 * ({@code ExecutarCicloDeCampanhasUseCase}).
 */
@Component
class OuvinteDeEntregaDeCampanha {

    private static final Logger log = LoggerFactory.getLogger(OuvinteDeEntregaDeCampanha.class);

    private final AplicarEntregaDeCampanhaUseCase aplicar;
    private final Executor executor;

    OuvinteDeEntregaDeCampanha(
            AplicarEntregaDeCampanhaUseCase aplicar,
            @Qualifier(ExecutorDeCampanhasConfig.NOME) Executor executor) {
        this.aplicar = aplicar;
        this.executor = executor;
    }

    /** So enfileira: a transacao nova abre depois que a conexao original foi liberada (ver o executor). */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoMudarStatusDeEntrega(MudancaDeStatusDeEntrega evento) {
        executor.execute(() -> {
            try {
                ContextoDeServico.executarComo(
                        "campanhas-entrega",
                        () -> aplicar.executar(evento.mensagemId(), evento.statusEntrega(), evento.ocorridoEm()));
            } catch (RuntimeException erro) {
                log.error(
                        "[ALERTA_CAMPANHA_ENTREGA] nao foi possivel aplicar a entrega da mensagem {} a campanha; a reconciliacao do ciclo corrige.",
                        evento.mensagemId(),
                        erro);
            }
        });
    }
}
