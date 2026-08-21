package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.atendimento.StatusAtendimento;
import com.synapse.crm.atendimento.domain.canal.CanalGateway;
import com.synapse.crm.atendimento.domain.canal.ConteudoDeEnvio;
import com.synapse.crm.atendimento.domain.canal.ForaDaJanelaException;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.MensagemParaTempoReal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.atendimento.domain.mensagem.StatusEntrega;
import com.synapse.crm.atendimento.domain.mensagem.TipoMensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/** Registra a resposta da IA e a intenção de envio, sem assumir um usuário humano. */
@Service
public class ResponderAtendimentoDaAutomacaoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;
    private final LeadNoCaminhoDeMensagem leads;
    private final Outbox outbox;
    private final CanalGateway canal;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public ResponderAtendimentoDaAutomacaoUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            LeadNoCaminhoDeMensagem leads,
            Outbox outbox,
            CanalGateway canal,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.leads = leads;
        this.outbox = outbox;
        this.canal = canal;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("hasRole('SERVICO')")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID atendimentoId, String conteudo) {
        if (conteudo == null || conteudo.isBlank()) {
            throw new MensagemAutomacaoInvalidaException("conteudo e obrigatorio");
        }

        Atendimento atendimento = atendimentos.porId(atendimentoId)
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        if (atendimento.status() != StatusAtendimento.EM_IA) {
            throw new RespostaAutomacaoInvalidaException("atendimento nao esta sob responsabilidade da IA");
        }

        LeadNoCaminhoDeMensagem.ContatoParaEnvio contato = leads.contatoParaEnvio(atendimento.leadId())
                .orElseThrow(() -> new RecursoDeAtendimentoIndisponivelException("atendimento", atendimentoId));
        Instant agora = Instant.now(relogio);
        ConteudoDeEnvio.MensagemLivre envio = new ConteudoDeEnvio.MensagemLivre(conteudo.trim());
        if (!canal.aceitaTextoLivre(contato.ultimaInteracao(), agora)) {
            throw new ForaDaJanelaException(atendimento.leadId());
        }

        Mensagem gravada = mensagens.registrar(new Mensagem(
                UUID.randomUUID(),
                atendimento.id(),
                Remetente.ia(),
                TipoMensagem.TEXTO,
                envio.texto(),
                null,
                null,
                StatusEntrega.PENDENTE,
                agora));
        outbox.enfileirarEnvio(
                gravada.id(),
                agora,
                atendimento.id(),
                atendimento.leadId(),
                contato.telefone(),
                atendimento.canalCredencialId(),
                envio);
        leads.registrarInteracao(atendimento.leadId(), agora, 0, 1);

        eventos.publishEvent(new EventoDeAtendimento.MensagemEnviadaPelaAutomacao(
                atendimento.leadId(), atendimento.id(), gravada.id(), agora));
        eventos.publishEvent(new MensagemParaTempoReal(
                atendimento.id(),
                atendimento.leadId(),
                gravada.id(),
                gravada.remetente().tipo().name(),
                null,
                gravada.tipo().name(),
                gravada.conteudo(),
                null,
                null,
                null,
                gravada.statusEntrega().name(),
                gravada.enviadoEm()));

        return new Resultado(atendimento.id(), gravada.id(), gravada.statusEntrega(), gravada.enviadoEm());
    }

    public record Resultado(
            UUID atendimentoId, UUID mensagemId, StatusEntrega statusEntrega, Instant enviadoEm) {}
}
