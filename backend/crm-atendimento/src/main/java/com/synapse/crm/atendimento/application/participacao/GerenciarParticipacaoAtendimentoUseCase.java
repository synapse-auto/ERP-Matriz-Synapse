package com.synapse.crm.atendimento.application.participacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.AtendimentoRepositorio;
import com.synapse.crm.atendimento.application.RecursoDeAtendimentoIndisponivelException;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.equipe.application.autenticacao.UsuarioRepositorio;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

@Service
public class GerenciarParticipacaoAtendimentoUseCase {
    private final ParticipacaoAtendimentoRepositorio participacoes;
    private final AtendimentoRepositorio atendimentos;
    private final UsuarioContext usuarios;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;
    private final UsuarioRepositorio usuariosRepositorio;
    public GerenciarParticipacaoAtendimentoUseCase(ParticipacaoAtendimentoRepositorio p, AtendimentoRepositorio a,
            UsuarioContext u, ApplicationEventPublisher e, Clock c, UsuarioRepositorio ur) { participacoes=p; atendimentos=a; usuarios=u; eventos=e; relogio=c; usuariosRepositorio=ur; }

    @PreAuthorize("isAuthenticated()") @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public UUID solicitar(UUID atendimentoId) {
        UUID usuario=usuarios.atual().id(); UUID pedido=participacoes.solicitar(atendimentoId,usuario)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        UUID lead=participacoes.leadId(atendimentoId).orElseThrow();
        eventos.publishEvent(new EventoDeAtendimento.PedidoEntradaSolicitado(lead,atendimentoId,usuario,nome(usuario),dono(atendimentoId),agora()));
        return pedido;
    }
    @PreAuthorize("isAuthenticated()") @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public UUID solicitarPorLead(UUID leadId) {
        return solicitar(participacoes.atendimentoAbertoDoLead(leadId).orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("lead", leadId)));
    }

    @PreAuthorize("isAuthenticated()") @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public void aprovar(UUID pedidoId) { responder(pedidoId,true); }
    @PreAuthorize("isAuthenticated()") @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public void recusar(UUID pedidoId) { responder(pedidoId,false); }
    private void responder(UUID pedidoId, boolean aprovado) {
        UUID dono=usuarios.atual().id(); PedidoEntradaAtendimento pedido=participacoes.pedido(pedidoId).orElseThrow();
        Instant agora=agora(); if (pedido.solicitadoEm().plus(participacoes.validadeConfigurada()).isBefore(agora)) throw new IllegalStateException("pedido expirado");
        if (aprovado) participacoes.aprovar(pedidoId,dono,agora); else participacoes.recusar(pedidoId,dono,agora);
        UUID lead=participacoes.leadId(pedido.atendimentoId()).orElseThrow();
        eventos.publishEvent(new EventoDeAtendimento.PedidoEntradaRespondido(lead,pedido.atendimentoId(),pedido.solicitanteId(),dono,aprovado,agora));
        if (aprovado) eventos.publishEvent(new EventoDeAtendimento.ParticipanteEntrou(lead,pedido.atendimentoId(),pedido.solicitanteId(),agora));
    }

    @PreAuthorize("isAuthenticated()") @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public void entrar(UUID atendimentoId) { if(!usuarios.atual().enxergaTodosOsLeads()) throw new SecurityException("sem alçada para entrar diretamente"); Instant agora=agora(); participacoes.entrar(atendimentoId,usuarios.atual().id(),agora); UUID lead=participacoes.leadId(atendimentoId).orElseThrow(); eventos.publishEvent(new EventoDeAtendimento.ParticipanteEntrou(lead,atendimentoId,usuarios.atual().id(),agora)); }

    @PreAuthorize("isAuthenticated()") @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER)
    public void sair(UUID atendimentoId) { UUID u=usuarios.atual().id(); if(!participacoes.eParticipanteAtivo(atendimentoId,u)) throw new RecursoDeAtendimentoIndisponivelException("participação",atendimentoId); Instant agora=agora(); participacoes.sair(atendimentoId,u,agora); UUID lead=participacoes.leadId(atendimentoId).orElseThrow(); eventos.publishEvent(new EventoDeAtendimento.ParticipanteSaiu(lead,atendimentoId,u,agora)); }

    @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER, readOnly=true)
    public List<ParticipanteAtendimento> participantes(UUID atendimentoId) {
        atendimentos.porId(atendimentoId).orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        return participacoes.ativos(atendimentoId);
    }
    @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER, readOnly=true)
    public List<PedidoEntradaAtendimento> pendentes(UUID atendimentoId) {
        if (!participacoes.eDono(atendimentoId, usuarios.atual().id())) throw new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId);
        Instant limite=agora().minus(participacoes.validadeConfigurada()); return participacoes.pendentes(atendimentoId,limite);
    }
    @Transactional(transactionManager=Pools.CHAT_TRANSACTION_MANAGER, readOnly=true)
    public java.util.Optional<PedidoEntradaAtendimento> meuPedido(UUID atendimentoId) {
        return participacoes.pedidoDoSolicitante(atendimentoId, usuarios.atual().id(), agora().minus(participacoes.validadeConfigurada()));
    }
    private UUID dono(UUID id) { return participacoes.donoId(id).orElseThrow(); }
    private String nome(UUID id) { return usuariosRepositorio.porId(id).map(u -> u.nome()).orElse(id.toString()); }
    private Instant agora() { return Instant.now(relogio); }
}
