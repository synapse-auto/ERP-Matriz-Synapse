package com.synapse.crm.equipe.interfaces;

import java.util.Map;

/** Nome de apresentação: sem caminhos/controles e com extensão compatível com o MIME persistido. */
final class NomeDeArquivoChat {
    private static final Map<String, String> EXTENSOES = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"), Map.entry("image/png", "png"), Map.entry("image/webp", "webp"),
            Map.entry("audio/ogg", "ogg"), Map.entry("audio/mpeg", "mp3"), Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/amr", "amr"), Map.entry("audio/aac", "aac"),
            Map.entry("video/mp4", "mp4"), Map.entry("video/3gpp", "3gp"),
            Map.entry("application/pdf", "pdf"), Map.entry("text/plain", "txt"),
            Map.entry("application/msword", "doc"), Map.entry("application/vnd.ms-excel", "xls"),
            Map.entry("application/vnd.ms-powerpoint", "ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"));

    private NomeDeArquivoChat() {}

    static String de(String original, String mimetype, String fallback) {
        String nome = original == null ? fallback : original.replace('\\', '/');
        nome = nome.substring(nome.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\\"]", "_").trim();
        if (nome.isBlank()) nome = fallback;
        String extensao = EXTENSOES.getOrDefault(mimetype == null ? "" : mimetype, "bin");
        int ponto = nome.lastIndexOf('.');
        String atual = ponto > 0 ? nome.substring(ponto + 1).toLowerCase(java.util.Locale.ROOT) : "";
        if (atual.equals(extensao) || (extensao.equals("jpg") && atual.equals("jpeg"))) return nome;
        return (ponto > 0 ? nome.substring(0, ponto) : nome) + "." + extensao;
    }
}
