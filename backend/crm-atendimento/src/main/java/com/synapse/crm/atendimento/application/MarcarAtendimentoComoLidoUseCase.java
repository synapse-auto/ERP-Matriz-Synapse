package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Registra a abertura da conversa para o usuario autenticado. */
@Service
public class MarcarAtendimentoComoLidoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final UsuarioContext usuarioContext;
    private final Clock relogio;

    public MarcarAtendimentoComoLidoUseCase(
            AtendimentoRepositorio atendimentos, UsuarioContext usuarioContext, Clock relogio) {
        this.atendimentos = atendimentos;
        this.usuarioContext = usuarioContext;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void executar(UUID atendimentoId) {
        UUID usuarioId = usuarioContext.atual().id();
        atendimentos.marcarComoLido(atendimentoId, usuarioId, Instant.now(relogio));
    }
}
