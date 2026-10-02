package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.evento.EventoCanonicoDeAtendimento;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Registra a abertura da conversa para o usuario autenticado. */
@Service
public class MarcarAtendimentoComoLidoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final UsuarioContext usuarioContext;
    private final Clock relogio;
    private final ApplicationEventPublisher eventos;

    public MarcarAtendimentoComoLidoUseCase(
            AtendimentoRepositorio atendimentos,
            UsuarioContext usuarioContext,
            Clock relogio,
            ApplicationEventPublisher eventos) {
        this.atendimentos = atendimentos;
        this.usuarioContext = usuarioContext;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public void executar(UUID atendimentoId) {
        UUID usuarioId = usuarioContext.atual().id();
        Instant lidoEm = Instant.now(relogio);
        atendimentos.marcarComoLido(atendimentoId, usuarioId, lidoEm);
        // Leitura pessoal de gestor/participante nao altera o indicador do responsavel.
        // O aviso so e necessario quando o proprio dono confirmou a leitura.
        atendimentos.porId(atendimentoId)
                .filter(atendimento -> usuarioId.equals(atendimento.atendenteId()))
                .ifPresent(atendimento -> EventosCanonicosDeAtendimento.publicar(
                        atendimentos,
                        eventos,
                        EventoCanonicoDeAtendimento.Tipo.LEITURA_DO_RESPONSAVEL,
                        atendimentoId,
                        atendimento.leadId(),
                        lidoEm));
    }
}
