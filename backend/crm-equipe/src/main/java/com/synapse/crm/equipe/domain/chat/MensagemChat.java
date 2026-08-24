package com.synapse.crm.equipe.domain.chat;

/** Regra de dominio da fase inicial: mensagem textual nao pode ser vazia. */
public record MensagemChat(String conteudo) {
    public MensagemChat {
        if (conteudo == null || conteudo.isBlank()) {
            throw new IllegalArgumentException("A mensagem nao pode ser vazia.");
        }
        conteudo = conteudo.trim();
    }
}
