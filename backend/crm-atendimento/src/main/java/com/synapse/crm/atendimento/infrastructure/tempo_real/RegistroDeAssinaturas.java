package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * A fonte da verdade sobre quem esta autorizado a receber o que, nesta instancia.
 *
 * <p>Em memoria, por instancia — de proposito. Cada instancia so entrega para as sessoes conectadas
 * <b>a ela</b>; o backplane Redis e o que faz um evento nascido noutra instancia chegar ate aqui. Sem
 * sticky session: uma sessao pode reconectar em qualquer instancia e vai reautorizar do zero.
 *
 * <p>Nunca entregar para uma sessao que nao esta aqui e a regra que faz tanto a autorizacao de
 * assinatura quanto a revogacao em transferencia valerem alguma coisa — um destino de broadcast
 * ingenuo (por exemplo um {@code /topic/atendimento/{id}} entregue pelo broker nativo do STOMP) nao
 * teria como consultar este registro antes de entregar.
 */
@Component
class RegistroDeAssinaturas {

    private final Set<AssinaturaAutorizada> assinaturas = ConcurrentHashMap.newKeySet();

    void registrar(AssinaturaAutorizada assinatura) {
        assinaturas.add(assinatura);
    }

    /** Snapshot imutavel — quem chama pode iterar e remover sem se preocupar com concorrencia. */
    Set<AssinaturaAutorizada> doAtendimento(UUID atendimentoId) {
        return assinaturas.stream()
                .filter(assinatura -> assinatura.atendimentoId().equals(atendimentoId))
                .collect(Collectors.toUnmodifiableSet());
    }

    void remover(AssinaturaAutorizada assinatura) {
        assinaturas.remove(assinatura);
    }

    /**
     * UNSUBSCRIBE de uma assinatura especifica — a sessao pode ter outras abertas, para outros
     * atendimentos.
     */
    void removerPorSubscriptionId(String sessionId, String subscriptionId) {
        assinaturas.removeIf(assinatura -> assinatura.sessionId().equals(sessionId)
                && assinatura.subscriptionId().equals(subscriptionId));
    }

    /** DISCONNECT: toda assinatura daquela sessao sai de uma vez. */
    void removerDaSessao(String sessionId) {
        assinaturas.removeIf(assinatura -> assinatura.sessionId().equals(sessionId));
    }

    /** So para teste/diagnostico: quantas assinaturas ativas existem nesta instancia agora. */
    int total() {
        return assinaturas.size();
    }
}
