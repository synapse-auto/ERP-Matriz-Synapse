package com.synapse.crm.atendimento.infrastructure.tempo_real;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametros do WebSocket. Nenhum numero fica no codigo (CLAUDE.md).
 *
 * @param threadsEntrada tamanho do pool que processa frames recebidos do cliente (SUBSCRIBE,
 *     CONNECT). Proprio, e nao o pool HTTP nem o do publisher da outbox — o bulkhead da E06
 * @param threadsSaida tamanho do pool que entrega mensagens ao cliente
 * @param threadsRedis tamanho do pool que processa o que chega do backplane Redis
 * @param filaMaxima quantas tarefas esperam antes de o executor comecar a rejeitar
 * @param origensPermitidas origens aceitas no handshake; {@code *} em desenvolvimento, lista fechada
 *     em producao
 * @param ttlAssinaturaSegundos janela maxima do vazamento descrito na E07 §0: Redis pub/sub e
 *     at-most-once, e uma revogacao perdida (reconexao do cliente Redis, instancia ocupada no
 *     publish, particao breve) deixaria o dono anterior recebendo mensagens indefinidamente sem
 *     isto. 60s e o ponto de partida — nao e lei da natureza, e uma decisao de risco explicita que
 *     alguem pode e deve revisar depois, trocando so este numero.
 */
@ConfigurationProperties("synapse.tempo-real")
public record TempoRealProperties(
        int threadsEntrada,
        int threadsSaida,
        int threadsRedis,
        int filaMaxima,
        String origensPermitidas,
        int ttlAssinaturaSegundos) {

    public TempoRealProperties {
        threadsEntrada = threadsEntrada <= 0 ? 4 : threadsEntrada;
        threadsSaida = threadsSaida <= 0 ? 4 : threadsSaida;
        threadsRedis = threadsRedis <= 0 ? 2 : threadsRedis;
        filaMaxima = filaMaxima <= 0 ? 500 : filaMaxima;
        origensPermitidas = (origensPermitidas == null || origensPermitidas.isBlank())
                ? "*"
                : origensPermitidas;
        ttlAssinaturaSegundos = ttlAssinaturaSegundos <= 0 ? 60 : ttlAssinaturaSegundos;
    }
}
