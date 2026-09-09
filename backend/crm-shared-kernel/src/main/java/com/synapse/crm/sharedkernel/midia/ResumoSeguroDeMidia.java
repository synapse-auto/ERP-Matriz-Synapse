package com.synapse.crm.sharedkernel.midia;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Identidade técnica de uma mídia para diagnóstico, sem incluir bytes ou conteúdo nos logs.
 *
 * <p>O resumo é deliberadamente limitado ao tamanho e ao SHA-256: esses campos permitem provar
 * que o objeto recuperado é o mesmo que foi persistido e enviado, sem registrar áudio, legenda,
 * token ou qualquer outro dado de negócio.
 */
public record ResumoSeguroDeMidia(long tamanho, String sha256) {

    public static ResumoSeguroDeMidia de(byte[] conteudo) {
        if (conteudo == null) {
            throw new IllegalArgumentException("conteudo da midia ausente");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(conteudo);
            return new ResumoSeguroDeMidia(conteudo.length, HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM sem SHA-256", e);
        }
    }
}
