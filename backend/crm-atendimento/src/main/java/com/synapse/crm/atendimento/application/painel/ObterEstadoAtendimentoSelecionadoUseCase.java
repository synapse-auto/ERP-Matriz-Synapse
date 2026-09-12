package com.synapse.crm.atendimento.application.painel;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.application.participacao.ParticipacaoAtendimentoRepositorio;
import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Le o estado selecionado em uma unica transacao e sob o mesmo recorte RLS do comando. */
@Service
public class ObterEstadoAtendimentoSelecionadoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final PainelDeAtendimentosRepositorio painel;
    private final ParticipacaoAtendimentoRepositorio participacoes;
    private final UsuarioContext usuarios;

    public ObterEstadoAtendimentoSelecionadoUseCase(
            AtendimentoRepositorio atendimentos,
            PainelDeAtendimentosRepositorio painel,
            ParticipacaoAtendimentoRepositorio participacoes,
            UsuarioContext usuarios) {
        this.atendimentos = atendimentos;
        this.painel = painel;
        this.participacoes = participacoes;
        this.usuarios = usuarios;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER, readOnly = true)
    public EstadoAtendimentoSelecionado executar(UUID atendimentoId) {
        Atendimento atendimento = atendimentos.porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "atendimento", atendimentoId));
        UUID usuarioId = usuarios.atual().id();
        CartaoAtendimento cartao = painel.porAtendimentoId(atendimentoId, usuarioId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException(
                        "atendimento", atendimentoId));
        var participantes = participacoes.ativos(atendimentoId);
        boolean responsavel = atendimento.pertenceA(usuarioId);
        boolean participa = participantes.stream().anyMatch(item -> item.usuarioId().equals(usuarioId));
        return new EstadoAtendimentoSelecionado(
                cartao,
                atendimentos.versaoAtualDoEvento(atendimentoId),
                participantes,
                responsavel,
                participa,
                atendimento.estaAberto());
    }
}
