package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.time.Clock;
import java.time.Instant;
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
 *
 * <p><b>TTL (E07 §0):</b> a revogacao por evento (transferencia) e o caminho rapido, mas Redis
 * pub/sub e <i>at-most-once</i> — uma mensagem de revogacao perdida deixaria o dono anterior
 * recebendo para sempre, sem que nada aqui detectasse isso. Por isso toda entrada carrega um
 * instante de expiracao: {@link RedisSubscriberDeAtendimento} revalida contra o banco na primeira
 * entrega apos o TTL vencer (nunca por uma varredura periodica — o custo e uma consulta por
 * assinatura ativa a cada {@code ttlAssinaturaSegundos}, nao uma por mensagem) e renova se a
 * autorizacao ainda vale. Isso limita a janela do vazamento de "indefinida" para, no maximo,
 * {@code ttlAssinaturaSegundos}.
 */
@Component
class RegistroDeAssinaturas {

    private final ConcurrentHashMap<AssinaturaAutorizada, Instant> assinaturas = new ConcurrentHashMap<>();
    private final Clock relogio;
    private final TempoRealProperties propriedades;

    RegistroDeAssinaturas(Clock relogio, TempoRealProperties propriedades) {
        this.relogio = relogio;
        this.propriedades = propriedades;
    }

    void registrar(AssinaturaAutorizada assinatura) {
        assinaturas.put(assinatura, proximaExpiracao());
    }

    /** Snapshot imutavel — quem chama pode iterar e remover sem se preocupar com concorrencia. */
    Set<AssinaturaAutorizada> doAtendimento(UUID atendimentoId) {
        return assinaturas.keySet().stream()
                .filter(assinatura -> assinatura.atendimentoId().equals(atendimentoId))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** TTL vencido: a proxima entrega para esta assinatura precisa revalidar antes de confiar nela. */
    boolean expirou(AssinaturaAutorizada assinatura) {
        Instant expiraEm = assinaturas.get(assinatura);
        return expiraEm == null || !Instant.now(relogio).isBefore(expiraEm);
    }

    /** Revalidacao confirmou que a assinatura continua valida: empurra o TTL para frente. */
    void renovar(AssinaturaAutorizada assinatura) {
        assinaturas.computeIfPresent(assinatura, (chave, expiraEmAntigo) -> proximaExpiracao());
    }

    void remover(AssinaturaAutorizada assinatura) {
        assinaturas.remove(assinatura);
    }

    void removerUsuarioDoAtendimento(UUID atendimentoId, UUID usuarioId) {
        assinaturas.keySet().removeIf(a -> a.atendimentoId().equals(atendimentoId) && a.usuarioId().equals(usuarioId));
    }

    /**
     * UNSUBSCRIBE de uma assinatura especifica — a sessao pode ter outras abertas, para outros
     * atendimentos.
     */
    void removerPorSubscriptionId(String sessionId, String subscriptionId) {
        assinaturas.keySet().removeIf(assinatura -> assinatura.sessionId().equals(sessionId)
                && assinatura.subscriptionId().equals(subscriptionId));
    }

    /** DISCONNECT: toda assinatura daquela sessao sai de uma vez. */
    void removerDaSessao(String sessionId) {
        assinaturas.keySet().removeIf(assinatura -> assinatura.sessionId().equals(sessionId));
    }

    /** So para teste/diagnostico: quantas assinaturas ativas existem nesta instancia agora. */
    int total() {
        return assinaturas.size();
    }

    private Instant proximaExpiracao() {
        return Instant.now(relogio).plusSeconds(propriedades.ttlAssinaturaSegundos());
    }
}
