package com.synapse.crm.atendimento.application;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.AtendimentoJaFinalizadoException;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Finaliza, em uma operacao, os atendimentos {@code EM_ATENDIMENTO} visiveis ao usuario atual.
 *
 * <p>Potenciais ({@code EM_IA}) ficam de fora de proposito: o atendente os enxerga, mas encerra-los
 * em massa esvaziaria a fila da IA e o {@code UPDATE} para {@code FINALIZADO} e recusado pela RLS.
 */
@Service
public class FinalizarAtendimentosVisiveisUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final FinalizarAtendimentoUseCase finalizar;
    private final UsuarioContext usuarioContext;

    public FinalizarAtendimentosVisiveisUseCase(
            AtendimentoRepositorio atendimentos,
            FinalizarAtendimentoUseCase finalizar,
            UsuarioContext usuarioContext) {
        this.atendimentos = atendimentos;
        this.finalizar = finalizar;
        this.usuarioContext = usuarioContext;
    }

    /**
     * O recorte e calculado pelo banco sob RLS; cada item tambem passa pelo caso de uso individual,
     * preservando as mesmas autorizacoes e eventos. Disputas concorrentes sao contadas como recusadas
     * sem desfazer os demais encerramentos.
     */
    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar() {
        UUID quemFinalizou = usuarioContext.atual().id();
        var abertos = atendimentos.abertosVisiveis();
        int solicitados = abertos.size();
        int finalizados = 0;
        int recusados = 0;

        for (var atendimento : abertos) {
            try {
                finalizar.executarEmLote(atendimento.id(), quemFinalizou);
                finalizados++;
            } catch (AtendimentoJaFinalizadoException | RecursoDeAtendimentoIndisponivelException e) {
                recusados++;
            }
        }
        return new Resultado(solicitados, finalizados, recusados);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public int quantidade() {
        return atendimentos.abertosVisiveis().size();
    }

    public record Resultado(int solicitados, int finalizados, int recusados) {}
}
