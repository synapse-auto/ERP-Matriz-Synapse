package com.synapse.crm.atendimento.application.tempo_real;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * A pergunta que toda assinatura de WebSocket precisa responder antes de aceitar: este usuario
 * enxerga este atendimento?
 *
 * <p>Reusa {@link AtendimentoRepositorio#porId(UUID)} — o mesmo caminho que o REST usa, com a mesma
 * politica RLS por baixo — em vez de reimplementar a regra de visibilidade para o WebSocket. Vazio
 * significa "nao existe ou nao e seu"; para o assinante, os dois casos tem o mesmo efeito: a
 * assinatura nao entra e nada chega.
 *
 * <p>Sem broadcast mal desenhado nenhuma Specification ou trava de transacao alcanca este caminho —
 * o WebSocket e uma segunda porta de leitura, e esta classe e o que garante que ela nao ignora a
 * RN-CRM-01.
 */
@Service
public class AutorizarAssinaturaAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ParticipacaoAtendimentoRepositorio participacoes;
    private final UsuarioContext usuarioContext;

    public AutorizarAssinaturaAtendimentoUseCase(AtendimentoRepositorio atendimentos, ParticipacaoAtendimentoRepositorio participacoes, UsuarioContext usuarioContext) {
        this.atendimentos = atendimentos; this.participacoes = participacoes; this.usuarioContext = usuarioContext;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public boolean autorizar(UUID atendimentoId) {
        return atendimentos.porId(atendimentoId).isPresent()
                || participacoes.eParticipanteAtivo(atendimentoId, usuarioContext.atual().id());
    }
}
