package com.synapse.crm.atendimento.domain.informacoeschatbot;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Snapshot imutavel do que o chatbot coletou antes de transferir o atendimento a um humano.
 *
 * <p>Nao e uma mensagem: nao tem remetente, status de entrega nem chave de particao, e nunca vai para
 * a outbox de envio. A origem e sempre a Automacao — o card jamais e atribuido a uma pessoa ou ao
 * cliente.
 */
public record InformacoesDoChatbot(UUID id, UUID atendimentoId, String conteudo, Instant registradoEm) {

    public InformacoesDoChatbot {
        Objects.requireNonNull(id, "id e obrigatorio");
        Objects.requireNonNull(atendimentoId, "toda informacao pertence a um atendimento");
        Objects.requireNonNull(conteudo, "conteudo e obrigatorio");
        Objects.requireNonNull(registradoEm, "registradoEm e obrigatorio");
    }
}
