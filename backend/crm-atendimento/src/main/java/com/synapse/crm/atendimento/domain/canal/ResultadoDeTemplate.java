package com.synapse.crm.atendimento.domain.canal;

/**
 * Resposta do provedor ao criar um template, sem o JSON da Meta.
 *
 * <p>A aprovacao e assincrona no provedor oficial: {@link Aceito} com status {@code PENDENTE} e o
 * caso feliz do POST, nao uma falha. {@link Recusado} e o pedido que a Meta nem enfileirou.
 */
public sealed interface ResultadoDeTemplate {

    record Aceito(TemplateDoCanal template) implements ResultadoDeTemplate {}

    record Recusado(String motivo) implements ResultadoDeTemplate {}

    default boolean aceito() {
        return this instanceof Aceito;
    }
}
