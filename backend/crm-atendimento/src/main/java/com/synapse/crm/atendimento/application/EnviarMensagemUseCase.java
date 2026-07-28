package com.synapse.crm.atendimento.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.domain.atendimento.Atendimento;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.atendimento.domain.mensagem.Remetente;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.identidade.UsuarioContext;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Alguem da equipe mandou mensagem — e por isso o lead passa a ser dele (RN-CRM-06).
 *
 * <p>A transferencia e a contrapartida do isolamento de agenda: a RN-CRM-01 impede pegar o lead do
 * colega, e esta regra garante que quem trabalhou fica com ele. Como os atendentes trabalham por
 * comissao, as duas juntas sao politica comercial da casa do cliente, nao preferencia tecnica.
 *
 * <p><b>Quem o remetente alcanca continua sendo decidido pela RN-CRM-01.</b> A transferencia acontece
 * dentro do recorte de visibilidade, nao por cima dele: um atendente manda mensagem no proprio lead
 * (transferencia sem efeito) ou num lead sem dono do grupo "Potenciais" (e o lead passa a ser dele).
 * Um lead que ja e de um colega nao e alcancavel — o {@code UPDATE} nao encontra a linha e o caso de
 * uso responde como se nao existisse. Quem enxerga a base inteira (gestor) alcanca qualquer lead, e
 * para esse a regra transfere de fato.
 */
@Service
public class EnviarMensagemUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final MensagemRepositorio mensagens;
    private final LeadNoCaminhoDeMensagem leads;
    private final UsuarioContext usuarioContext;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public EnviarMensagemUseCase(
            AtendimentoRepositorio atendimentos,
            MensagemRepositorio mensagens,
            LeadNoCaminhoDeMensagem leads,
            UsuarioContext usuarioContext,
            ApplicationEventPublisher eventos,
            Clock relogio) {
        this.atendimentos = atendimentos;
        this.mensagens = mensagens;
        this.leads = leads;
        this.usuarioContext = usuarioContext;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(UUID leadId, String conteudo) {
        UUID remetenteId = usuarioContext.atual().id();
        Instant agora = Instant.now(relogio);

        // RN-CRM-06 primeiro: se o lead nao e alcancavel por quem esta enviando, nada
        // mais acontece. Gravar a mensagem antes deixaria mensagem orfa num lead que o
        // remetente nao pode tocar.
        LeadNoCaminhoDeMensagem.Transferencia transferencia = leads.transferirPara(leadId, remetenteId);
        if (!transferencia.aconteceu()) {
            throw new RecursoDeAtendimentoIndisponivelException("lead", leadId);
        }
        boolean trocouDeDono = transferencia
                .donoAnterior()
                .map(anterior -> !anterior.equals(remetenteId))
                .orElse(true);

        Atendimento aberto = atendimentos
                .abertoDoLead(leadId)
                .orElseGet(() -> atendimentos.salvar(
                        Atendimento.abrirComIa(UUID.randomUUID(), leadId, null, null, agora)));

        // O atendimento acompanha o lead: deixar a conversa com a IA depois de um humano
        // responder faria a automacao continuar falando por cima do atendente.
        if (!aberto.pertenceA(remetenteId)) {
            aberto = atendimentos.salvar(aberto.transferirPara(remetenteId));
        }

        Mensagem gravada = mensagens.registrar(Mensagem.texto(
                UUID.randomUUID(), aberto.id(), Remetente.atendente(remetenteId), conteudo, agora));

        leads.registrarInteracao(leadId, agora, 0, 1);

        eventos.publishEvent(new EventoDeAtendimento.MensagemEnviada(
                leadId,
                aberto.id(),
                gravada.id(),
                remetenteId,
                transferencia.donoAnterior(),
                trocouDeDono,
                agora));

        return new Resultado(aberto, gravada, trocouDeDono);
    }

    /** @param transferiuOLead se a RN-CRM-06 mudou o dono de fato */
    public record Resultado(Atendimento atendimento, Mensagem mensagem, boolean transferiuOLead) {}
}
