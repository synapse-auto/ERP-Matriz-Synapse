package com.synapse.crm.atendimento.infrastructure.tempo_real;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tempos da presenca automatica (E223). Nenhum numero fica no codigo; os valores iniciais abaixo sao so o ponto de
 * partida sugerido e podem ser trocados por variavel de ambiente (docs/18).
 *
 * @param tolerancia quanto tempo um usuario pode ficar sem NENHUMA sessao STOMP antes de virar OFFLINE. Tem de ser
 *     maior que o teto do backoff de reconexao do navegador (15 a 30 s, E203) mais o tempo de uma aba voltar, senao
 *     toda renovacao de token (o frontend troca o socket) ou queda curta de rede derrubaria o usuario do rodizio.
 *     Reconectar dentro dela e continuacao da mesma sessao: nao muda a presenca nem grava historico
 * @param carencia nos primeiros segundos depois que o backend sobe ninguem e marcado OFFLINE: o registro de sessoes
 *     em memoria esta vazio ate os clientes reconectarem (todo deploy derruba todas as conexoes), e sem isto cada
 *     deploy esvaziaria o rodizio. Zero desliga a carencia (so para teste)
 * @param intervaloVarredura de quanto em quanto tempo a varredura compara o banco com o registro de sessoes; a
 *     desconexao real so vira OFFLINE na primeira varredura depois da tolerancia, entao o atraso maximo e
 *     {@code tolerancia + intervaloVarredura}
 */
@ConfigurationProperties("synapse.tempo-real.presenca")
public record PresencaAutomaticaProperties(Duration tolerancia, Duration carencia, Duration intervaloVarredura) {

    public PresencaAutomaticaProperties {
        tolerancia = positivo(tolerancia) ? tolerancia : Duration.ofSeconds(90);
        carencia = carencia == null || carencia.isNegative() ? Duration.ofSeconds(120) : carencia;
        intervaloVarredura = positivo(intervaloVarredura) ? intervaloVarredura : Duration.ofSeconds(15);
    }

    private static boolean positivo(Duration valor) {
        return valor != null && !valor.isNegative() && !valor.isZero();
    }
}
