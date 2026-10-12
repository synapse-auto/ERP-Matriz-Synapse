package com.synapse.crm.atendimento.infrastructure.vinculo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.synapse.crm.atendimento.application.VinculoDoLeadRepositorio;
import com.synapse.crm.atendimento.domain.evento.EventoDeAtendimento;
import com.synapse.crm.atendimento.domain.evento.ResponsavelDoContatoDefinido;

/**
 * Grava o atendente vinculado quando um humano <b>recebe</b>, <b>assume</b> ou <b>já é o responsável e fala</b> (D3b).
 *
 * <p>Só estes fatos mudam o vínculo, e só quando a chave {@code ia.distribuicao.atendente_vinculado} está ligada:
 * <ul>
 *   <li>{@link EventoDeAtendimento.AtendimentoTransferido} com destino humano — cobre o rodízio da IA, a
 *       transferência entre atendentes e o "assumir para si";
 *   <li>{@link EventoDeAtendimento.MensagemEnviada} em que o remetente passou a ser o dono (RN-CRM-06 trocou o dono)
 *       <em>ou já era o dono e não é participante</em> — o responsável que atende o cliente é o vinculado;
 *   <li>{@link ResponsavelDoContatoDefinido} — abriu o contato pela Agenda e ficou como responsável, com ou sem
 *       primeira mensagem (com mensagem, quem grava é o item anterior).
 * </ul>
 * Participante que colabora sem ser o dono <b>não</b> vira vinculado. Devolver para a IA <b>não</b> apaga o vínculo: é
 * justamente ele que traz o cliente de volta ao mesmo atendente. Abrir a ficha não publica nada.
 *
 * <p>Este método <b>não</b> é transacional: ele recebe todo {@code EventoDeAtendimento} (inclusive mensagem recebida,
 * o caminho mais quente) e, antes, uma anotação {@code REQUIRES_NEW} aqui abria uma transação para cada um deles só
 * para devolver sem fazer nada. A transação (e o SQL) existem apenas em
 * {@link VinculoDoLeadRepositorio#registrar}, depois de o evento casar e de a chave estar ligada — e a chave ligada é
 * lida da memória. AFTER_COMMIT: nunca soma latência nem falha o caminho da mensagem.
 */
@Component
class VinculoDoLeadListener {

    static final String ORIGEM_TRANSFERENCIA = "TRANSFERENCIA";
    static final String ORIGEM_ENVIO_MANUAL = "ENVIO_MANUAL";
    static final String ORIGEM_CONTATO_ABERTO = "CONTATO_ABERTO";

    private final VinculoDoLeadRepositorio vinculos;

    VinculoDoLeadListener(VinculoDoLeadRepositorio vinculos) {
        this.vinculos = vinculos;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoAcontecer(EventoDeAtendimento evento) {
        if (evento instanceof EventoDeAtendimento.AtendimentoTransferido transferido
                && transferido.paraAtendenteId() != null) {
            registrar(transferido.leadId(), transferido.paraAtendenteId(), ORIGEM_TRANSFERENCIA);
        } else if (evento instanceof EventoDeAtendimento.MensagemEnviada enviada && remetenteEhOResponsavel(enviada)) {
            registrar(enviada.leadId(), enviada.remetenteId(), ORIGEM_ENVIO_MANUAL);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void aoDefinirResponsavelDoContato(ResponsavelDoContatoDefinido evento) {
        registrar(evento.leadId(), evento.atendenteId(), ORIGEM_CONTATO_ABERTO);
    }

    /**
     * O remetente é quem responde pelo lead: ou assumiu agora (a RN-CRM-06 trocou o dono) ou já era o dono. O
     * participante que colabora preserva o responsável (decisão do E231): só vale se ele assumiu.
     */
    private static boolean remetenteEhOResponsavel(EventoDeAtendimento.MensagemEnviada enviada) {
        if (enviada.transferiu()) {
            return true;
        }
        return !enviada.participante() && enviada.donoAnterior().filter(enviada.remetenteId()::equals).isPresent();
    }

    private void registrar(java.util.UUID leadId, java.util.UUID atendenteId, String origem) {
        if (vinculos.habilitado()) {
            vinculos.registrar(leadId, atendenteId, origem);
        }
    }
}
