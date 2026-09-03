package com.synapse.crm.atendimento.domain.canal;

/**
 * O que o provedor respondeu, traduzido para vocabulario do CRM.
 *
 * <p>A distincao entre recusa <b>permanente</b> e <b>temporaria</b> e a informacao mais importante
 * que o adaptador produz, porque ela decide o destino da linha na outbox:
 *
 * <ul>
 *   <li>temporaria (timeout, 429, 5xx, breaker aberto) — vale a pena tentar de novo, com backoff;
 *   <li>permanente (numero invalido, template nao aprovado, fora da janela) — retentar mil vezes da
 *       o mesmo resultado e so gasta quota. A linha esgota na hora e vai para inspecao.
 * </ul>
 *
 * <p>Sem essa separacao, ou se retenta o que nunca vai funcionar, ou se desiste do que so precisava
 * de mais trinta segundos. As duas escolhas custam mensagem nao entregue.
 */
public sealed interface ResultadoDeEnvio {

    /**
     * @param idExterno id da mensagem no provedor, para casar o callback de entrega depois
     * @param enderecoDoProvedor endereco que o provedor usa para este destinatario; {@code null}
     *     quando o provedor nao informa ou a leitura falhou sem invalidar o aceite
     */
    record Aceito(String idExterno, String enderecoDoProvedor) implements ResultadoDeEnvio {

        /** Aceite sem endereco — provedor fake, resposta sem contacts, ou leitura falhou. */
        public Aceito(String idExterno) {
            this(idExterno, null);
        }
    }

    record Recusado(String motivo, boolean permanente) implements ResultadoDeEnvio {

        public static Recusado temporario(String motivo) {
            return new Recusado(motivo, false);
        }

        public static Recusado permanente(String motivo) {
            return new Recusado(motivo, true);
        }
    }

    default boolean aceito() {
        return this instanceof Aceito;
    }
}
