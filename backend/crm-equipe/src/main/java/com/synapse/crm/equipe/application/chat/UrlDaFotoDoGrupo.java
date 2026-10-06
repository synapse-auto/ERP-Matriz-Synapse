package com.synapse.crm.equipe.application.chat;

import java.time.Instant;
import java.util.UUID;

/**
 * Unico lugar que monta a URL da foto: caminho autenticado da API mais a versao. A versao muda a
 * cada troca, entao o cache do navegador e do cliente nunca serve a imagem anterior.
 */
public final class UrlDaFotoDoGrupo {

    private UrlDaFotoDoGrupo() {}

    public static String de(UUID conversaId, Instant versao) {
        return "/api/v1/chat-interno/conversas/" + conversaId + "/foto?v=" + versao.toEpochMilli();
    }
}
