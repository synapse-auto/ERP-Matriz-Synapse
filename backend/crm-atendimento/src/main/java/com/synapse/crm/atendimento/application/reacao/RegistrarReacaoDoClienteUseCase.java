package com.synapse.crm.atendimento.application.reacao;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagem;
import com.synapse.crm.atendimento.application.referencia.OrigemDeMensagemRepositorio;
import com.synapse.crm.atendimento.domain.canal.TradutorDeCanal.ReacaoRecebidaDoCanal;
import com.synapse.crm.atendimento.domain.evento.ReacaoDoClienteParaTempoReal;
import com.synapse.crm.atendimento.domain.mensagem.Mensagem;
import com.synapse.crm.core.application.lead.LeadNoCaminhoDeMensagem;
import com.synapse.crm.sharedkernel.persistencia.Pools;

/**
 * Aplica a reacao do cliente a uma mensagem da propria conversa (E214).
 *
 * <p>Sem {@code @PreAuthorize}, como {@code RegistrarMensagemRecebidaUseCase}: quem chama e o
 * processador do webhook, sob contexto de servico, e a autorizacao e a assinatura do provedor.
 *
 * <p>Tres guardas, nessa ordem, e nenhuma cria nada:
 *
 * <ul>
 *   <li>o remetente precisa ja ser lead — reacao nao cria lead nem abre atendimento;
 *   <li>o alvo e achado pelo id externo, nunca por "ultima mensagem";
 *   <li>o alvo tem de ser da conversa desse lead. Conhecer o id de uma mensagem de outra conversa
 *       nao da acesso a ela.
 * </ul>
 */
@Service
public class RegistrarReacaoDoClienteUseCase {

    public enum Resultado {
        APLICADA,
        /** Repetida, atrasada ou igual a atual: nada muda e nada e publicado. */
        SEM_MUDANCA,
        ALVO_DESCONHECIDO
    }

    private final LeadNoCaminhoDeMensagem leads;
    private final OrigemDeMensagemRepositorio origens;
    private final ReacaoDoClienteRepositorio reacoes;
    private final ApplicationEventPublisher eventos;

    public RegistrarReacaoDoClienteUseCase(
            LeadNoCaminhoDeMensagem leads,
            OrigemDeMensagemRepositorio origens,
            ReacaoDoClienteRepositorio reacoes,
            ApplicationEventPublisher eventos) {
        this.leads = leads;
        this.origens = origens;
        this.reacoes = reacoes;
        this.eventos = eventos;
    }

    @Transactional(transactionManager = Pools.CHAT_TRANSACTION_MANAGER)
    public Resultado executar(ReacaoRecebidaDoCanal reacao) {
        Optional<UUID> leadId = leadDoRemetente(reacao.telefoneRemetente());
        if (leadId.isEmpty()) {
            return Resultado.ALVO_DESCONHECIDO;
        }
        Optional<OrigemDeMensagem> alvo = origens.buscarPorWamid(reacao.idExternoAlvo())
                .filter(origem -> leadId.get().equals(origem.leadId()));
        if (alvo.isEmpty()) {
            return Resultado.ALVO_DESCONHECIDO;
        }
        Mensagem mensagem = alvo.get().mensagem();
        var chave = new ReacaoDeMensagemRepositorio.Chave(mensagem.id(), mensagem.enviadoEm());
        if (!reacoes.aplicar(chave, reacao.emoji(), reacao.reagidoEm(), reacao.idExterno())) {
            return Resultado.SEM_MUDANCA;
        }
        eventos.publishEvent(new ReacaoDoClienteParaTempoReal(
                mensagem.atendimentoId(), mensagem.id(), mensagem.enviadoEm(), reacao.emoji()));
        return Resultado.APLICADA;
    }

    private Optional<UUID> leadDoRemetente(String telefone) {
        try {
            return leads.visivelPorTelefone(telefone);
        } catch (IllegalArgumentException telefoneIlegivel) {
            return Optional.empty();
        }
    }
}
