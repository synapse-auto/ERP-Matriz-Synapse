package com.synapse.crm.campanhas.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.campanhas.application.RegistrarRespostaDeCampanhaUseCase;
import com.synapse.crm.sharedkernel.identidade.ContextoDeServico;

/**
 * Marca "respondeu" quando o cliente escreve. Depois do commit e isolado: a mensagem do cliente entra pelo fluxo
 * normal (IA ou atendente) e nenhuma falha daqui a atrapalha.
 */
@Component
class OuvinteDeRespostaDeCampanha {

    private static final Logger log = LoggerFactory.getLogger(OuvinteDeRespostaDeCampanha.class);

    private final RegistrarRespostaDeCampanhaUseCase registrar;

    OuvinteDeRespostaDeCampanha(RegistrarRespostaDeCampanhaUseCase registrar) {
        this.registrar = registrar;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoReceberMensagem(EventoDeAtendimento.MensagemRecebida evento) {
        try {
            ContextoDeServico.executarComo(
                    "campanhas-resposta", () -> registrar.executar(evento.leadId(), evento.ocorridoEm()));
        } catch (RuntimeException erro) {
            log.error(
                    "[ALERTA_CAMPANHA_RESPOSTA] nao foi possivel registrar a resposta do lead {} na campanha.",
                    evento.leadId(),
                    erro);
        }
    }
}
