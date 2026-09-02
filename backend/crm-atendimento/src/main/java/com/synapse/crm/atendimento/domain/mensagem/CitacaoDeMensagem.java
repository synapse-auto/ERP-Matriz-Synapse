package com.synapse.crm.atendimento.domain.mensagem;

import java.util.UUID;

/**
 * Resumo autorizado que a bolha mostra. Nao carrega telefone, token, URL privada nem payload do
 * provedor — so o que quem alcanca a conversa <em>destino</em> ja poderia ver.
 */
public record CitacaoDeMensagem(
        UUID origemId, String tipoReferencia, String autor, String tipoConteudo, String previa) {

    public CitacaoDeMensagem {
        tipoReferencia = tipoReferencia == null ? "" : tipoReferencia;
        autor = autor == null ? "" : autor;
        tipoConteudo = tipoConteudo == null ? "" : tipoConteudo;
        previa = previa == null ? "" : previa;
    }

    static final int LIMITE_PREVIA = 120;

    public static String autorDe(RemetenteTipo tipo, String nomeLead, String nomeUsuario) {
        return switch (tipo) {
            case LEAD -> textoOu(nomeLead, "Lead");
            case ATENDENTE -> textoOu(nomeUsuario, "Atendente");
            case IA -> "IA";
            case SISTEMA -> "Sistema";
        };
    }

    public static String previaDe(TipoMensagem tipo, String conteudo, String metadados) {
        if (tipo == null) {
            return sanitizar(conteudo);
        }
        return switch (tipo) {
            case TEXTO -> sanitizar(conteudo);
            case IMAGEM, AUDIO, DOCUMENTO, VIDEO -> sanitizar(legendaDe(metadados));
            case BOTOES, LISTA -> sanitizar(conteudo);
        };
    }

    public static String sanitizar(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return "";
        }
        String semControle = bruto.replaceAll("[\\p{Cntrl}&&[^\n\t]]", " ").replace('\n', ' ').replace('\t', ' ');
        String compacto = semControle.trim().replaceAll(" +", " ");
        return compacto.length() <= LIMITE_PREVIA ? compacto : compacto.substring(0, LIMITE_PREVIA);
    }

    private static String textoOu(String valor, String alternativa) {
        return valor == null || valor.isBlank() ? alternativa : valor.trim();
    }

    private static String legendaDe(String metadados) {
        if (metadados == null || metadados.isBlank()) {
            return "";
        }
        int inicio = metadados.indexOf("\"legenda\"");
        if (inicio < 0) {
            inicio = metadados.indexOf("\"nome\"");
        }
        if (inicio < 0) {
            return "";
        }
        int doisPontos = metadados.indexOf(':', inicio);
        int abertura = metadados.indexOf('"', doisPontos + 1);
        int fechamento = metadados.indexOf('"', abertura + 1);
        if (abertura < 0 || fechamento < 0) {
            return "";
        }
        return metadados.substring(abertura + 1, fechamento);
    }
}
