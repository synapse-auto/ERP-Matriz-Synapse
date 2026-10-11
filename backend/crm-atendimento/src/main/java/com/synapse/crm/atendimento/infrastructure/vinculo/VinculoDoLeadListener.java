package com.synapse.crm.atendimento.infrastructure.vinculo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;

/**
 * Grava o atendente vinculado quando um humano <b>recebe</b> ou <b>assume</b> a conversa (D3b).
 *
 * <p>Só dois eventos mudam o vínculo, e só quando a chave {@code ia.distribuicao.atendente_vinculado} está ligada:
 * <ul>
 *   <li>{@link EventoDeAtendimento.AtendimentoTransferido} com destino humano — cobre o rodízio da IA, a
 *       transferência entre atendentes e o "assumir para si";
 *   <li>{@link EventoDeAtendimento.MensagemEnviada} com {@code transferiu} — a RN-CRM-06 trocou o dono porque um
 *       humano respondeu.
 * </ul>
 * Devolver para a IA <b>não</b> apaga o vínculo: é justamente ele que traz o cliente de volta ao mesmo atendente.
 * Qualquer outro evento retorna antes de tocar no banco, então mensagem recebida (o caminho mais quente) não paga
 * nada. AFTER_COMMIT: nunca soma latência nem falha o caminho da mensagem.
 */
@Component
class VinculoDoLeadListener {

    static final String ORIGEM_TRANSFERENCIA = "TRANSFERENCIA";
    static final String ORIGEM_ENVIO_MANUAL = "ENVIO_MANUAL";

    private final VinculoDoLeadRepositorio vinculos;

    VinculoDoLeadListener(VinculoDoLeadRepositorio vinculos) {
        this.vinculos = vinculos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aoAcontecer(EventoDeAtendimento evento) {
        if (evento instanceof EventoDeAtendimento.AtendimentoTransferido transferido
                && transferido.paraAtendenteId() != null) {
            registrar(transferido.leadId(), transferido.paraAtendenteId(), ORIGEM_TRANSFERENCIA);
        } else if (evento instanceof EventoDeAtendimento.MensagemEnviada enviada && enviada.transferiu()) {
            registrar(enviada.leadId(), enviada.remetenteId(), ORIGEM_ENVIO_MANUAL);
        }
    }

    private void registrar(java.util.UUID leadId, java.util.UUID atendenteId, String origem) {
        if (vinculos.habilitado()) {
            vinculos.registrar(leadId, atendenteId, origem);
        }
    }
}
