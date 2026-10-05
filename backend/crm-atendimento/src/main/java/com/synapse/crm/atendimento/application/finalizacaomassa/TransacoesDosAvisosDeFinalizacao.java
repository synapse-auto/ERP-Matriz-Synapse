package com.synapse.crm.atendimento.application.finalizacaomassa;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.finalizacaomassa.AvisosDeFinalizacaoEmMassaRepositorio.AvisoPendente;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Reserva, entrega e marca UM aviso numa unica transacao: o lock de linha segura o aviso ate o resultado, entao
 * dois nos nunca entregam o mesmo. A entrega e at-least-once (se o processo morrer depois de entregar e antes
 * de marcar, ha reentrega); o frontend deduplica pelo identificador da operacao.
 */
@Service
public class TransacoesDosAvisosDeFinalizacao {

    private static final Logger log = LoggerFactory.getLogger(TransacoesDosAvisosDeFinalizacao.class);
    static final int MAXIMO_DE_TENTATIVAS = 5;

    private final AvisosDeFinalizacaoEmMassaRepositorio repositorio;
    private final EntregadorDeAvisoDeFinalizacao entregador;
    private final Clock relogio;

    public TransacoesDosAvisosDeFinalizacao(
            AvisosDeFinalizacaoEmMassaRepositorio repositorio, EntregadorDeAvisoDeFinalizacao entregador, Clock relogio) {
        this.repositorio = repositorio;
        this.entregador = entregador;
        this.relogio = relogio;
    }

    /** @return se havia um aviso devido (entregue ou reagendado); falso = fila vazia. */
    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public boolean publicarProximo() {
        Instant agora = Instant.now(relogio);
        AvisoPendente aviso = repositorio.reservarProximo(agora).orElse(null);
        if (aviso == null) {
            return false;
        }
        try {
            entregador.entregar(aviso);
            repositorio.marcarEnviado(aviso.operacaoId(), aviso.usuarioId(), agora);
        } catch (RuntimeException erro) {
            boolean esgotou = repositorio.registrarFalha(
                    aviso.operacaoId(), aviso.usuarioId(), erro.getClass().getSimpleName(), agora, MAXIMO_DE_TENTATIVAS);
            log.warn(
                    "Aviso de finalizacao em massa nao entregue: operacaoId={}, tipoErro={}, esgotou={}",
                    aviso.operacaoId(), erro.getClass().getSimpleName(), esgotou);
        }
        return true;
    }
}
