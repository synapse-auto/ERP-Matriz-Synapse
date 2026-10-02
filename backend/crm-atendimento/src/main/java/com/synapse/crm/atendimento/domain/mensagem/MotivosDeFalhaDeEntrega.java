package com.synapse.crm.atendimento.domain.mensagem;

/**
 * Motivos de falha que o proprio CRM grava em {@code mensagem.erro_entrega} (os do provedor chegam pelo
 * webhook, com codigo). Constantes compartilhadas para quem grava e quem le o motivo concordarem num so lugar.
 */
public final class MotivosDeFalhaDeEntrega {

    /**
     * O despacho ao provedor ficou sem resultado (E209): a mensagem pode ou nao ter saido, e nunca e reenviada
     * sozinha. E o mesmo texto de {@code atendimentos.mensagem.envioNaoConfirmado} em textos.json.
     */
    public static final String ENVIO_NAO_CONFIRMADO = "Não foi possível confirmar o envio";

    private MotivosDeFalhaDeEntrega() {}
}
