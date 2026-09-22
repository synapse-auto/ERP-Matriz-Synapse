package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.time.Duration;

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
 * @param heartbeatSaidaMs de quanto em quanto tempo o servidor escreve um pulso para o navegador.
 *     Sem pulso nenhum (o padrao do {@code SimpleBroker} e {@code 0,0}), uma conexao que morre em
 *     silencio — proxy derrubando conexao ociosa, troca de rede, laptop suspenso — so e percebida
 *     quando a pilha TCP do navegador desiste sozinha, o que nao tem prazo previsivel. Como a tela
 *     de Atendimentos nao tem polling por decisao de projeto (docs/40), esse prazo indefinido e
 *     exatamente o tempo que o atendente fica sem ver a mensagem que ja chegou. 10s e o ponto de
 *     partida: o cliente espera o dobro do intervalo antes de declarar a conexao perdida, entao a
 *     morte e detectada em ~20s e o reconector com backoff entra em seguida
 * @param heartbeatEntradaMs de quanto em quanto tempo o servidor espera um pulso do navegador.
 *     Mesma escolha de 10s do lado de saida; e o que permite ao servidor derrubar sessao morta em
 *     vez de manter recurso preso ate o TCP expirar
 * @param intervaloMetricas de quanto em quanto tempo o resumo local de sessoes e usuarios STOMP
 *     autenticados vai ao log operacional
 * @param reconexaoAtrasoInicialMs atraso inicial que o navegador aplica depois de uma desconexao
 * @param reconexaoFator multiplicador exponencial do atraso de reconexao
 * @param reconexaoAtrasoMaximoMs teto do atraso de reconexao anunciado ao navegador
 */
@ConfigurationProperties("synapse.tempo-real")
public record TempoRealProperties(
        int threadsEntrada,
        int threadsSaida,
        int threadsRedis,
        int filaMaxima,
        String origensPermitidas,
        int ttlAssinaturaSegundos,
        long heartbeatSaidaMs,
        long heartbeatEntradaMs,
        Duration intervaloMetricas,
        long reconexaoAtrasoInicialMs,
        double reconexaoFator,
        long reconexaoAtrasoMaximoMs) {

    public TempoRealProperties {
        threadsEntrada = threadsEntrada <= 0 ? 4 : threadsEntrada;
        threadsSaida = threadsSaida <= 0 ? 4 : threadsSaida;
        threadsRedis = threadsRedis <= 0 ? 2 : threadsRedis;
        filaMaxima = filaMaxima <= 0 ? 500 : filaMaxima;
        origensPermitidas = (origensPermitidas == null || origensPermitidas.isBlank())
                ? "*"
                : origensPermitidas;
        ttlAssinaturaSegundos = ttlAssinaturaSegundos <= 0 ? 60 : ttlAssinaturaSegundos;
        heartbeatSaidaMs = heartbeatSaidaMs <= 0 ? 10_000L : heartbeatSaidaMs;
        heartbeatEntradaMs = heartbeatEntradaMs <= 0 ? 10_000L : heartbeatEntradaMs;
        intervaloMetricas = intervaloMetricas == null || intervaloMetricas.isNegative() || intervaloMetricas.isZero()
                ? Duration.ofMinutes(1)
                : intervaloMetricas;
        reconexaoAtrasoInicialMs = reconexaoAtrasoInicialMs <= 0 ? 1_000L : reconexaoAtrasoInicialMs;
        reconexaoFator = reconexaoFator <= 1 ? 2 : reconexaoFator;
        reconexaoAtrasoMaximoMs = reconexaoAtrasoMaximoMs < reconexaoAtrasoInicialMs
                ? 30_000L
                : reconexaoAtrasoMaximoMs;
    }
}
