package com.synapse.crm.atendimento.application.origem;

import java.util.UUID;

/**
 * Origem declarada de uma mensagem automatica.
 *
 * @param regraId id da regra que gerou o envio (regra de follow-up, mensagem festiva...), opcional
 * @param execucaoId id da execucao do n8n, opcional; liga a mensagem ao log da execucao
 * @param aviso por que a origem foi rebaixada para NAO_INFORMADA, ou nulo
 */
public record OrigemDaMensagem(TipoDeOrigem tipo, String regraId, String execucaoId, String aviso) {

    public static final int TAMANHO_MAXIMO_DA_REGRA = 100;
    public static final int TAMANHO_MAXIMO_DA_EXECUCAO = 200;

    /**
     * Normaliza os campos opcionais que a Automacao envia. Nunca rejeita: a mensagem pode ja ter
     * saido no provedor, e perder o registro seria pior do que registra-la sem origem. Campo ausente
     * ou invalido vira NAO_INFORMADA com aviso, para o log apontar o fluxo a corrigir.
     */
    public static OrigemDaMensagem declaradaPelaAutomacao(String tipo, String regraId, String execucaoId) {
        String regra = aparar(regraId, TAMANHO_MAXIMO_DA_REGRA);
        String execucao = aparar(execucaoId, TAMANHO_MAXIMO_DA_EXECUCAO);
        if (tipo == null || tipo.isBlank()) {
            return new OrigemDaMensagem(TipoDeOrigem.NAO_INFORMADA, regra, execucao, "origemTipo ausente");
        }
        return TipoDeOrigem.declaradoPelaAutomacao(tipo)
                .map(declarado -> new OrigemDaMensagem(declarado, regra, execucao, null))
                .orElseGet(() -> new OrigemDaMensagem(
                        TipoDeOrigem.NAO_INFORMADA, regra, execucao, "origemTipo desconhecido: " + aparar(tipo, 40)));
    }

    public static OrigemDaMensagem programada(UUID mensagemProgramadaId) {
        return new OrigemDaMensagem(TipoDeOrigem.PROGRAMADA, mensagemProgramadaId.toString(), null, null);
    }

    public static OrigemDaMensagem deReservaProativa(TipoDeOrigem tipo, String regraId, String execucaoId) {
        return new OrigemDaMensagem(tipo, regraId, execucaoId, null);
    }

    public boolean informada() {
        return tipo != TipoDeOrigem.NAO_INFORMADA;
    }

    private static String aparar(String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
    }
}
