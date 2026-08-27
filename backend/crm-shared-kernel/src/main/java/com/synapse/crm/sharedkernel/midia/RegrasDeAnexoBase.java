package com.synapse.crm.sharedkernel.midia;

import java.util.Map;
import java.util.Optional;

/**
 * Classificacao agnostica de mimetypes validos para anexos.
 */
public final class RegrasDeAnexoBase {

    private static final Map<String, CategoriaDeMidia> POR_MIMETYPE = Map.ofEntries(
            Map.entry("image/jpeg", CategoriaDeMidia.IMAGEM),
            Map.entry("image/png", CategoriaDeMidia.IMAGEM),
            Map.entry("image/webp", CategoriaDeMidia.IMAGEM),
            Map.entry("audio/ogg", CategoriaDeMidia.AUDIO),
            Map.entry("audio/mpeg", CategoriaDeMidia.AUDIO),
            Map.entry("audio/mp4", CategoriaDeMidia.AUDIO),
            Map.entry("audio/amr", CategoriaDeMidia.AUDIO),
            Map.entry("audio/aac", CategoriaDeMidia.AUDIO),
            Map.entry("application/pdf", CategoriaDeMidia.DOCUMENTO),
            Map.entry("application/msword", CategoriaDeMidia.DOCUMENTO),
            Map.entry(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    CategoriaDeMidia.DOCUMENTO),
            Map.entry("application/vnd.ms-excel", CategoriaDeMidia.DOCUMENTO),
            Map.entry(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    CategoriaDeMidia.DOCUMENTO),
            Map.entry("text/plain", CategoriaDeMidia.DOCUMENTO));

    private RegrasDeAnexoBase() {}

    public static Optional<CategoriaDeMidia> categoriaDe(String mimetypeReal) {
        return Optional.ofNullable(POR_MIMETYPE.get(mimetypeReal));
    }
}
